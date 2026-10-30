package com.darshan.splitsmart.ui.bill

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darshan.splitsmart.core.DispatcherProvider
import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.Member
import com.darshan.splitsmart.domain.repository.BillRepository
import com.darshan.splitsmart.domain.repository.GroupRepository
import com.darshan.splitsmart.ui.ErrorReason
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.toErrorReason
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BillDetail(val bill: Bill, val members: List<Member>) {
    val payerName: String? get() = members.find { it.id == bill.paidByMemberId }?.name
}

data class BillDetailUiState(val bill: LoadState<BillDetail> = LoadState.Loading, val deleting: Boolean = false)

sealed interface BillDetailEvent {
    data object Deleted : BillDetailEvent
    data object Failed : BillDetailEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BillDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val billRepository: BillRepository,
    groupRepository: GroupRepository,
    dispatchers: DispatcherProvider,
) : ViewModel() {
    val billId: String = checkNotNull(savedStateHandle[ARG_BILL_ID]) { "billId missing" }
    private val reload = MutableStateFlow(0)
    private val deleting = MutableStateFlow(false)
    private val _events = Channel<BillDetailEvent>(Channel.BUFFERED)
    val events: Flow<BillDetailEvent> = _events.receiveAsFlow()

    private val detail: Flow<LoadState<BillDetail>> = reload.flatMapLatest {
        billRepository.observeBill(billId).flatMapLatest { bill ->
            if (bill == null) flowOf(LoadState.Error(ErrorReason.NOT_FOUND))
            else groupRepository.observeMembers(bill.groupId).map<List<Member>, LoadState<BillDetail>> { LoadState.Content(BillDetail(bill, it)) }
        }.onStart { emit(LoadState.Loading) }.catch { emit(LoadState.Error(it.toErrorReason())) }
    }.flowOn(dispatchers.default)

    val uiState: StateFlow<BillDetailUiState> = kotlinx.coroutines.flow.combine(detail, deleting) { d, del -> BillDetailUiState(d, del) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BillDetailUiState())

    fun retry() = reload.update { it + 1 }

    fun deleteBill() {
        if (deleting.value) return
        deleting.value = true
        viewModelScope.launch {
            runCatching { billRepository.deleteBill(billId) }
                .onSuccess { _events.send(BillDetailEvent.Deleted) }
                .onFailure { deleting.value = false; _events.send(BillDetailEvent.Failed) }
        }
    }

    companion object {
        const val ARG_BILL_ID = "billId"
    }
}
