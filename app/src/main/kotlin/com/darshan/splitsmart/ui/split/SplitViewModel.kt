package com.darshan.splitsmart.ui.split

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darshan.splitsmart.core.DispatcherProvider
import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.Member
import com.darshan.splitsmart.domain.model.SplitConfig
import com.darshan.splitsmart.domain.model.SplitError
import com.darshan.splitsmart.domain.model.SplitException
import com.darshan.splitsmart.domain.model.SplitMode
import com.darshan.splitsmart.domain.model.SplitResult
import com.darshan.splitsmart.domain.repository.BillRepository
import com.darshan.splitsmart.domain.repository.GroupRepository
import com.darshan.splitsmart.domain.split.SplitCalculator
import com.darshan.splitsmart.domain.usecase.SaveSplitResult
import com.darshan.splitsmart.domain.usecase.SaveSplitUseCase
import com.darshan.splitsmart.ui.ErrorReason
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.toErrorReason
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class SplitEditor(
    val bill: Bill,
    val members: List<Member>,
    val config: SplitConfig,
    val payerId: String?,
    /** Raw share text per member (BY_SHARE); parsed into [SplitConfig.shares] on every change. */
    val shareTexts: Map<String, String>,
    val preview: SplitResult?,
    val error: SplitError?,
)

data class SplitUiState(val editor: LoadState<SplitEditor> = LoadState.Loading, val saving: Boolean = false)

sealed interface SplitEvent {
    data class Saved(val billId: String) : SplitEvent
    data object Failed : SplitEvent
}

/** Edits a [SplitConfig] with a live preview; the draft config lives in SavedStateHandle so it survives process death. */
@HiltViewModel
class SplitViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val billRepository: BillRepository,
    private val groupRepository: GroupRepository,
    private val saveSplit: SaveSplitUseCase,
    @Suppress("unused") private val dispatchers: DispatcherProvider,
) : ViewModel() {
    val billId: String = checkNotNull(savedStateHandle[ARG_BILL_ID]) { "billId missing" }
    private val json = Json { ignoreUnknownKeys = true }
    private val _uiState = MutableStateFlow(SplitUiState())
    val uiState: StateFlow<SplitUiState> = _uiState.asStateFlow()
    private val _events = Channel<SplitEvent>(Channel.BUFFERED)
    val events: Flow<SplitEvent> = _events.receiveAsFlow()

    init {
        load()
    }

    private fun load() {
        _uiState.value = SplitUiState()
        viewModelScope.launch {
            val bill = runCatching { billRepository.getBill(billId) }.getOrElse { _uiState.value = SplitUiState(LoadState.Error(it.toErrorReason())); return@launch }
                ?: run { _uiState.value = SplitUiState(LoadState.Error(ErrorReason.NOT_FOUND)); return@launch }
            val members = runCatching { groupRepository.getMembers(bill.groupId) }.getOrDefault(emptyList())
            val savedConfig = savedStateHandle.get<String>(KEY_CONFIG)?.let { runCatching { json.decodeFromString(SplitConfig.serializer(), it) }.getOrNull() }
            val config = savedConfig ?: bill.split?.config ?: SplitConfig(SplitMode.EQUAL, members.map { it.id })
            val savedShares = savedStateHandle.get<String>(KEY_SHARE_TEXTS)?.let { runCatching { json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), it) }.getOrNull() }
            val shareTexts = savedShares ?: members.associate { m -> m.id to (config.shares[m.id]?.toString() ?: "1") }
            val payer = if (savedStateHandle.contains(KEY_PAYER)) savedStateHandle.get<String>(KEY_PAYER) else bill.paidByMemberId
            apply(SplitEditor(bill, members, config, payer, shareTexts, preview = null, error = null), persist = false)
        }
    }

    fun retry() = load()

    private fun apply(editor: SplitEditor, persist: Boolean = true) {
        val withShares = editor.config.copy(shares = editor.shareTexts.mapValues { (_, text) -> text.trim().toIntOrNull() ?: 0 })
        val computed = try {
            editor.copy(config = withShares, preview = SplitCalculator.split(editor.bill, withShares), error = null)
        } catch (e: SplitException) {
            editor.copy(config = withShares, preview = null, error = e.reason)
        }
        _uiState.update { it.copy(editor = LoadState.Content(computed)) }
        if (persist) {
            savedStateHandle[KEY_CONFIG] = json.encodeToString(SplitConfig.serializer(), computed.config)
            savedStateHandle[KEY_SHARE_TEXTS] = json.encodeToString(MapSerializer(String.serializer(), String.serializer()), computed.shareTexts)
            savedStateHandle[KEY_PAYER] = computed.payerId
        }
    }

    private fun edit(transform: (SplitEditor) -> SplitEditor) {
        val current = (_uiState.value.editor as? LoadState.Content)?.data ?: return
        if (_uiState.value.saving) return
        apply(transform(current))
    }

    fun setMode(mode: SplitMode) = edit { it.copy(config = it.config.copy(mode = mode)) }

    fun toggleParticipant(memberId: String) = edit { e ->
        val selected = e.config.participantIds.toMutableSet().apply { if (!remove(memberId)) add(memberId) }
        e.copy(config = e.config.copy(participantIds = e.members.map { it.id }.filter { it in selected }))
    }

    fun toggleItemAssignment(itemId: String, memberId: String) = edit { e ->
        val current = e.config.itemAssignments[itemId].orEmpty()
        val next = if (memberId in current) current - memberId else current + memberId
        e.copy(config = e.config.copy(itemAssignments = e.config.itemAssignments + (itemId to next)))
    }

    fun setShareText(memberId: String, text: String) = edit { it.copy(shareTexts = it.shareTexts + (memberId to text.take(6))) }

    fun setPayer(memberId: String?) = edit { it.copy(payerId = memberId) }

    fun save() {
        val editor = (_uiState.value.editor as? LoadState.Content)?.data ?: return
        if (_uiState.value.saving || editor.preview == null) return
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val result = saveSplit(editor.bill, editor.config, editor.payerId)) {
                is SaveSplitResult.Saved -> {
                    savedStateHandle[KEY_CONFIG] = null; savedStateHandle[KEY_SHARE_TEXTS] = null; savedStateHandle.remove<String>(KEY_PAYER)
                    _uiState.update { it.copy(saving = false) }
                    _events.send(SplitEvent.Saved(billId))
                }
                is SaveSplitResult.Invalid -> { _uiState.update { it.copy(saving = false) }; apply(editor) }
                is SaveSplitResult.Failed -> { _uiState.update { it.copy(saving = false) }; _events.send(SplitEvent.Failed) }
            }
        }
    }

    companion object {
        const val ARG_BILL_ID = "billId"
        private const val KEY_CONFIG = "splitConfig"
        private const val KEY_SHARE_TEXTS = "shareTexts"
        private const val KEY_PAYER = "payer"
    }
}
