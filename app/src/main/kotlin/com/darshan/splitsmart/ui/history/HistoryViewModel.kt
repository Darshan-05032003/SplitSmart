package com.darshan.splitsmart.ui.history

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darshan.splitsmart.core.DispatcherProvider
import com.darshan.splitsmart.domain.model.Group
import com.darshan.splitsmart.domain.model.Settlement
import com.darshan.splitsmart.domain.repository.BillRepository
import com.darshan.splitsmart.domain.repository.GroupRepository
import com.darshan.splitsmart.domain.repository.SettlementRepository
import com.darshan.splitsmart.ui.ErrorReason
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.designsystem.effects.HapticEvent
import com.darshan.splitsmart.ui.designsystem.effects.UiEffect
import com.darshan.splitsmart.ui.designsystem.effects.UiEffectChannel
import com.darshan.splitsmart.ui.toErrorReason
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class HistoryContent(val group: Group, val months: List<HistoryMonth>, val insights: Insights) {
    val entryCount: Int get() = months.sumOf { it.entries.size }
}

data class HistoryUiState(
    val content: LoadState<HistoryContent> = LoadState.Loading,
    /** A payment opened in its sheet, where it can be removed. */
    val selected: Settlement? = null,
    val removing: Boolean = false,
)

sealed interface HistoryEvent {
    data class Removed(val fromName: String, val toName: String) : HistoryEvent
    data object Failed : HistoryEvent
}

/**
 * A group's history: every bill and every payment marked as made, month by month, under an insights card (who paid most, top
 * categories, what was settled). A payment marked by mistake can be removed from its sheet; its debt then comes back.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    groupRepository: GroupRepository,
    billRepository: BillRepository,
    private val settlementRepository: SettlementRepository,
    dispatchers: DispatcherProvider,
) : ViewModel() {
    val groupId: String = checkNotNull(savedStateHandle[ARG_GROUP_ID]) { "groupId missing" }
    private val zone: ZoneId = ZoneId.systemDefault()
    private val sheet = MutableStateFlow(HistoryUiState())
    private val _events = Channel<HistoryEvent>(Channel.BUFFERED)
    val events: Flow<HistoryEvent> = _events.receiveAsFlow()
    private val effectChannel = UiEffectChannel()
    val effects: Flow<UiEffect> = effectChannel.effects

    /** Bumped by [retry]: reading again after a failed read. */
    private val reload = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val content: Flow<LoadState<HistoryContent>> = reload.flatMapLatest {
        combine(
            groupRepository.observeGroup(groupId),
            groupRepository.observeMembers(groupId),
            billRepository.observeBills(groupId),
            settlementRepository.observeSettlements(groupId),
        ) { group, members, bills, settlements ->
            when {
                group == null -> LoadState.Error(ErrorReason.NOT_FOUND)
                bills.isEmpty() && settlements.isEmpty() -> LoadState.Empty
                else -> LoadState.Content(
                    HistoryContent(group, HistoryLogic.timeline(bills, settlements, members, zone), InsightsLogic.of(bills, members, settlements)),
                )
            }
        }.onStart<LoadState<HistoryContent>> { emit(LoadState.Loading) }.catch { emit(LoadState.Error(it.toErrorReason())) }
    }.flowOn(dispatchers.default)

    /** Reads the group again after a failed read. */
    fun retry() = reload.update { it + 1 }

    val uiState: StateFlow<HistoryUiState> = combine(content, sheet) { c, s -> s.copy(content = c) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun select(settlement: Settlement) {
        if (sheet.value.removing) return
        sheet.update { it.copy(selected = settlement) }
    }

    fun dismiss() {
        if (sheet.value.removing) return
        sheet.update { it.copy(selected = null) }
    }

    /** Removes the open payment: one removal per opening, however many taps. */
    fun remove() {
        val state = sheet.value
        val settlement = state.selected ?: return
        if (state.removing) return
        sheet.update { it.copy(removing = true) }
        viewModelScope.launch {
            runCatching { settlementRepository.delete(settlement.id) }
                .onSuccess {
                    sheet.update { it.copy(selected = null, removing = false) }
                    effectChannel.send(UiEffect.Haptic(HapticEvent.Heavy))
                    _events.send(HistoryEvent.Removed(settlement.fromName, settlement.toName))
                }
                .onFailure {
                    sheet.update { it.copy(removing = false) }
                    effectChannel.send(UiEffect.Haptic(HapticEvent.Reject))
                    _events.send(HistoryEvent.Failed)
                }
        }
    }

    companion object {
        const val ARG_GROUP_ID = "groupId"
    }
}
