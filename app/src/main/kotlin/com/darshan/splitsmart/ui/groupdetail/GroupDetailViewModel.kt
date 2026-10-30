package com.darshan.splitsmart.ui.groupdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darshan.splitsmart.core.DispatcherProvider
import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.Debt
import com.darshan.splitsmart.domain.split.DebtSimplifier
import com.darshan.splitsmart.domain.model.Group
import com.darshan.splitsmart.domain.model.Member
import com.darshan.splitsmart.domain.model.NameError
import com.darshan.splitsmart.domain.model.VpaError
import com.darshan.splitsmart.domain.repository.BillRepository
import com.darshan.splitsmart.domain.repository.GroupRepository
import com.darshan.splitsmart.domain.usecase.AddMemberResult
import com.darshan.splitsmart.domain.usecase.AddMemberUseCase
import com.darshan.splitsmart.domain.usecase.CreateBillResult
import com.darshan.splitsmart.domain.usecase.CreateManualBillUseCase
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupDetail(val group: Group, val members: List<Member>, val bills: List<Bill>, val debts: List<Debt> = emptyList(), val unsettledBills: Int = 0)

data class MemberDraft(
    val name: String = "",
    val vpa: String = "",
    val nameError: NameError? = null,
    val vpaError: VpaError? = null,
    val duplicate: Boolean = false,
    val groupFull: Boolean = false,
    val submitting: Boolean = false,
)

data class BillDraft(
    val title: String = "",
    val amount: String = "",
    val payerId: String? = null,
    val titleError: NameError? = null,
    val amountError: Boolean = false,
    val submitting: Boolean = false,
)

data class GroupDetailUiState(
    val detail: LoadState<GroupDetail> = LoadState.Loading,
    val memberDraft: MemberDraft? = null,
    val billDraft: BillDraft? = null,
)

sealed interface GroupDetailEvent {
    data class BillCreated(val billId: String) : GroupDetailEvent
    data object GroupDeleted : GroupDetailEvent
    data object Failed : GroupDetailEvent
}

/** Dialog drafts live in [SavedStateHandle] so an open dialog survives rotation and process death. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val groupRepository: GroupRepository,
    billRepository: BillRepository,
    private val addMember: AddMemberUseCase,
    private val createManualBill: CreateManualBillUseCase,
    dispatchers: DispatcherProvider,
) : ViewModel() {
    val groupId: String = checkNotNull(savedStateHandle[ARG_GROUP_ID]) { "groupId missing" }

    private val reload = MutableStateFlow(0)
    private val _events = Channel<GroupDetailEvent>(Channel.BUFFERED)
    val events: Flow<GroupDetailEvent> = _events.receiveAsFlow()

    // Persisted draft fields
    private val memberOpen = savedStateHandle.getStateFlow(KEY_MEMBER_OPEN, false)
    private val memberName = savedStateHandle.getStateFlow(KEY_MEMBER_NAME, "")
    private val memberVpa = savedStateHandle.getStateFlow(KEY_MEMBER_VPA, "")
    private val billOpen = savedStateHandle.getStateFlow(KEY_BILL_OPEN, false)
    private val billTitle = savedStateHandle.getStateFlow(KEY_BILL_TITLE, "")
    private val billAmount = savedStateHandle.getStateFlow(KEY_BILL_AMOUNT, "")
    private val billPayer = savedStateHandle.getStateFlow<String?>(KEY_BILL_PAYER, null)
    // Transient validation state
    private val memberErrors = MutableStateFlow(MemberDraft())
    private val billErrors = MutableStateFlow(BillDraft())

    private val detail: Flow<LoadState<GroupDetail>> = reload.flatMapLatest {
        combine(groupRepository.observeGroup(groupId), groupRepository.observeMembers(groupId), billRepository.observeBills(groupId)) { g, m, b ->
            if (g == null) LoadState.Error(ErrorReason.NOT_FOUND) else LoadState.Content(GroupDetail(g, m, b, debts = debtsOf(b), unsettledBills = DebtSimplifier.unsettledCount(b)))
        }.onStart<LoadState<GroupDetail>> { emit(LoadState.Loading) }.catch { emit(LoadState.Error(it.toErrorReason())) }
    }.flowOn(dispatchers.default)

    private val memberDraft: Flow<MemberDraft?> = combine(memberOpen, memberName, memberVpa, memberErrors) { open, name, vpa, errors ->
        if (open) errors.copy(name = name, vpa = vpa) else null
    }
    private val billDraft: Flow<BillDraft?> = combine(billOpen, billTitle, billAmount, billPayer, billErrors) { open, title, amount, payer, errors ->
        if (open) errors.copy(title = title, amount = amount, payerId = payer) else null
    }

    val uiState: StateFlow<GroupDetailUiState> = combine(detail, memberDraft, billDraft) { d, m, b -> GroupDetailUiState(d, m, b) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupDetailUiState(memberDraft = initialMemberDraft(), billDraft = initialBillDraft()))

    /** Balances are a pure function of the bills; a corrupt split must never take the whole screen down. */
    private fun debtsOf(bills: List<Bill>): List<Debt> = runCatching { DebtSimplifier.simplify(DebtSimplifier.balances(bills)) }.getOrDefault(emptyList())

    private fun initialMemberDraft() = if (memberOpen.value) MemberDraft(memberName.value, memberVpa.value) else null
    private fun initialBillDraft() = if (billOpen.value) BillDraft(billTitle.value, billAmount.value, billPayer.value) else null

    fun retry() = reload.update { it + 1 }

    // ----- members -----
    fun openAddMember() { memberErrors.value = MemberDraft(); savedStateHandle[KEY_MEMBER_OPEN] = true }
    fun dismissMember() { savedStateHandle[KEY_MEMBER_OPEN] = false; savedStateHandle[KEY_MEMBER_NAME] = ""; savedStateHandle[KEY_MEMBER_VPA] = "" }
    fun onMemberNameChange(v: String) { savedStateHandle[KEY_MEMBER_NAME] = v.take(INPUT_LIMIT); memberErrors.update { it.copy(nameError = null, duplicate = false) } }
    fun onMemberVpaChange(v: String) { savedStateHandle[KEY_MEMBER_VPA] = v.take(INPUT_LIMIT); memberErrors.update { it.copy(vpaError = null) } }

    fun submitMember() {
        if (memberErrors.value.submitting || !memberOpen.value) return
        memberErrors.update { it.copy(submitting = true) }
        viewModelScope.launch {
            when (val result = addMember(groupId, memberName.value, memberVpa.value)) {
                is AddMemberResult.Added -> { memberErrors.value = MemberDraft(); dismissMember() }
                is AddMemberResult.InvalidName -> memberErrors.update { it.copy(nameError = result.error, submitting = false) }
                is AddMemberResult.InvalidVpa -> memberErrors.update { it.copy(vpaError = result.error, submitting = false) }
                AddMemberResult.DuplicateName -> memberErrors.update { it.copy(duplicate = true, submitting = false) }
                AddMemberResult.GroupFull -> memberErrors.update { it.copy(groupFull = true, submitting = false) }
                is AddMemberResult.Failed -> { memberErrors.update { it.copy(submitting = false) }; _events.send(GroupDetailEvent.Failed) }
            }
        }
    }

    fun removeMember(memberId: String) {
        viewModelScope.launch { runCatching { groupRepository.removeMember(memberId) }.onFailure { _events.send(GroupDetailEvent.Failed) } }
    }

    // ----- manual bills -----
    fun openAddBill() { billErrors.value = BillDraft(); savedStateHandle[KEY_BILL_OPEN] = true }
    fun dismissBill() { savedStateHandle[KEY_BILL_OPEN] = false; savedStateHandle[KEY_BILL_TITLE] = ""; savedStateHandle[KEY_BILL_AMOUNT] = ""; savedStateHandle[KEY_BILL_PAYER] = null }
    fun onBillTitleChange(v: String) { savedStateHandle[KEY_BILL_TITLE] = v.take(INPUT_LIMIT); billErrors.update { it.copy(titleError = null) } }
    fun onBillAmountChange(v: String) { savedStateHandle[KEY_BILL_AMOUNT] = v.take(AMOUNT_INPUT_LIMIT); billErrors.update { it.copy(amountError = false) } }
    fun onBillPayerChange(memberId: String?) { savedStateHandle[KEY_BILL_PAYER] = memberId }

    fun submitBill() {
        if (billErrors.value.submitting || !billOpen.value) return
        billErrors.update { it.copy(submitting = true) }
        viewModelScope.launch {
            when (val result = createManualBill(groupId, billTitle.value, billAmount.value, billPayer.value)) {
                is CreateBillResult.Created -> { billErrors.value = BillDraft(); dismissBill(); _events.send(GroupDetailEvent.BillCreated(result.bill.id)) }
                is CreateBillResult.InvalidTitle -> billErrors.update { it.copy(titleError = result.error, submitting = false) }
                CreateBillResult.InvalidAmount -> billErrors.update { it.copy(amountError = true, submitting = false) }
                is CreateBillResult.Failed -> { billErrors.update { it.copy(submitting = false) }; _events.send(GroupDetailEvent.Failed) }
            }
        }
    }

    fun deleteGroup() {
        viewModelScope.launch {
            runCatching { groupRepository.deleteGroup(groupId) }
                .onSuccess { _events.send(GroupDetailEvent.GroupDeleted) }
                .onFailure { _events.send(GroupDetailEvent.Failed) }
        }
    }

    companion object {
        const val ARG_GROUP_ID = "groupId"
        private const val KEY_MEMBER_OPEN = "memberDialogOpen"
        private const val KEY_MEMBER_NAME = "memberName"
        private const val KEY_MEMBER_VPA = "memberVpa"
        private const val KEY_BILL_OPEN = "billDialogOpen"
        private const val KEY_BILL_TITLE = "billTitle"
        private const val KEY_BILL_AMOUNT = "billAmount"
        private const val KEY_BILL_PAYER = "billPayer"
        private const val INPUT_LIMIT = 200
        private const val AMOUNT_INPUT_LIMIT = 24
    }
}
