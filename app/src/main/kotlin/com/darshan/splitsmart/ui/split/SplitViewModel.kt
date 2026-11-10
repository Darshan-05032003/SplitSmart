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
import com.darshan.splitsmart.ui.designsystem.effects.HapticEvent
import com.darshan.splitsmart.ui.designsystem.effects.UiEffect
import com.darshan.splitsmart.ui.designsystem.effects.UiEffectChannel
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
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
    /** Items picked by long-press (by item), handed to one face together. */
    val selected: Set<String> = emptySet(),
    /** Goes up by one each time the last unassigned item gets an owner; the screen plays confetti once per value. */
    val celebrate: Int = 0,
) {
    val option: SplitModeOption get() = SplitModeOption.of(config)

    /** Percentage points still to give in percent mode, null otherwise. */
    val percentLeft: Int? get() = SplitLogic.percentLeft(config)

    /** Items given to each participant by name. */
    val assignedCounts: Map<String, Int> get() = SplitLogic.assignmentCounts(bill.items, config)

    val allAssigned: Boolean get() = SplitLogic.allAssigned(bill.items, config)
}

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
    private val effectChannel = UiEffectChannel()

    /** Haptics: light when an item lands on a face, confirm when the split is saved, reject when a save is refused. */
    val effects: Flow<UiEffect> = effectChannel.effects

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
            val selected = savedStateHandle.get<String>(KEY_SELECTED)?.let { runCatching { json.decodeFromString(SetSerializer(String.serializer()), it) }.getOrNull() }.orEmpty()
            val itemIds = bill.items.map { it.id }.toSet()
            apply(SplitEditor(bill, members, config, payer, shareTexts, preview = null, error = null, selected = selected.filterTo(LinkedHashSet()) { it in itemIds }), persist = false)
        }
    }

    fun retry() = load()

    private fun apply(editor: SplitEditor, persist: Boolean = true) {
        val withShares = editor.config.copy(shares = editor.shareTexts.mapValues { (_, text) -> text.trim().toIntOrNull() ?: 0 })
        val percentLeft = SplitLogic.percentLeft(withShares)
        val computed = try {
            // Percentages that don't total 100 would still split by their ratio: no preview until they do, so nothing misleads.
            val preview = if (percentLeft != null && percentLeft != 0) null else SplitCalculator.split(editor.bill, withShares)
            editor.copy(config = withShares, preview = preview, error = null)
        } catch (e: SplitException) {
            editor.copy(config = withShares, preview = null, error = e.reason)
        }
        _uiState.update { it.copy(editor = LoadState.Content(computed)) }
        if (persist) {
            savedStateHandle[KEY_CONFIG] = json.encodeToString(SplitConfig.serializer(), computed.config)
            savedStateHandle[KEY_SHARE_TEXTS] = json.encodeToString(MapSerializer(String.serializer(), String.serializer()), computed.shareTexts)
            savedStateHandle[KEY_PAYER] = computed.payerId
            savedStateHandle[KEY_SELECTED] = json.encodeToString(SetSerializer(String.serializer()), computed.selected)
        }
    }

    private fun edit(transform: (SplitEditor) -> SplitEditor) {
        val current = (_uiState.value.editor as? LoadState.Content)?.data ?: return
        if (_uiState.value.saving) return
        apply(transform(current))
    }

    fun setMode(mode: SplitMode) = setModeOption(SplitModeOption.entries.first { it.mode == mode })

    /**
     * Switches mode. Entering percent mode with numbers that don't total 100 starts from an even 100 (34/33/33); leaving it keeps
     * the numbers as plain shares. Picks belong to "by item" and are dropped elsewhere.
     */
    fun setModeOption(option: SplitModeOption) = edit { e ->
        val config = option.apply(e.config)
        val texts = if (option == SplitModeOption.Percent && SplitLogic.percentLeft(config.copy(shares = e.shareTexts.mapValues { it.value.trim().toIntOrNull() ?: 0 })) != 0) {
            e.shareTexts + SplitLogic.evenPercents(config.participantIds)
        } else {
            e.shareTexts
        }
        e.copy(config = config, shareTexts = texts, selected = if (option == SplitModeOption.ByItem) e.selected else emptySet())
    }

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

    /** The share steppers: one share (or one percentage point) up or down, never below 0. */
    fun stepShare(memberId: String, delta: Int) = edit { it.copy(shareTexts = it.shareTexts + (memberId to SplitLogic.stepShare(it.shareTexts[memberId].orEmpty(), delta))) }

    /** Long-press on an item picks it (or unpicks it); picked items go to one face together. */
    fun toggleSelected(itemId: String) = edit { e ->
        if (e.bill.items.none { it.id == itemId }) e
        else e.copy(selected = if (itemId in e.selected) e.selected - itemId else e.selected + itemId)
    }

    fun clearSelection() = edit { it.copy(selected = emptySet()) }

    /** Gives the picked items to [memberId]. */
    fun assignSelected(memberId: String) {
        val editor = (_uiState.value.editor as? LoadState.Content)?.data ?: return
        assignItems(editor.selected, memberId)
    }

    /**
     * An item dropped on a face (or picked items handed over): the person gets it as well as anyone who had it. A light haptic
     * confirms a change; the last unassigned item getting an owner bumps [SplitEditor.celebrate].
     */
    fun assignItems(itemIds: Collection<String>, memberId: String) = edit { e ->
        val ids = itemIds.toList()
        val config = SplitLogic.assign(e.config, ids, memberId)
        if (config == e.config) return@edit e
        effectChannel.send(UiEffect.Haptic(HapticEvent.Light))
        val celebrate = if (!e.allAssigned && SplitLogic.allAssigned(e.bill.items, config)) e.celebrate + 1 else e.celebrate
        e.copy(config = config, selected = e.selected - ids.toSet(), celebrate = celebrate)
    }

    fun setPayer(memberId: String?) = edit { it.copy(payerId = memberId) }

    fun save() {
        val editor = (_uiState.value.editor as? LoadState.Content)?.data ?: return
        if (_uiState.value.saving) return
        if (editor.preview == null) {
            effectChannel.send(UiEffect.Haptic(HapticEvent.Reject))
            return
        }
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val result = saveSplit(editor.bill, editor.config, editor.payerId)) {
                is SaveSplitResult.Saved -> {
                    savedStateHandle[KEY_CONFIG] = null; savedStateHandle[KEY_SHARE_TEXTS] = null; savedStateHandle.remove<String>(KEY_PAYER)
                    savedStateHandle.remove<String>(KEY_SELECTED)
                    _uiState.update { it.copy(saving = false) }
                    effectChannel.send(UiEffect.Haptic(HapticEvent.Confirm))
                    _events.send(SplitEvent.Saved(billId))
                }
                is SaveSplitResult.Invalid -> {
                    _uiState.update { it.copy(saving = false) }
                    effectChannel.send(UiEffect.Haptic(HapticEvent.Reject))
                    apply(editor)
                }
                is SaveSplitResult.Failed -> { _uiState.update { it.copy(saving = false) }; _events.send(SplitEvent.Failed) }
            }
        }
    }

    companion object {
        const val ARG_BILL_ID = "billId"
        private const val KEY_CONFIG = "splitConfig"
        private const val KEY_SHARE_TEXTS = "shareTexts"
        private const val KEY_PAYER = "payer"
        private const val KEY_SELECTED = "selectedItems"
    }
}
