package com.darshan.splitsmart.ui.settleup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darshan.splitsmart.core.DispatcherProvider
import com.darshan.splitsmart.data.upi.UpiAppLauncher
import com.darshan.splitsmart.data.upi.UpiLaunchResult
import com.darshan.splitsmart.data.upi.UpiLinkBuilder
import com.darshan.splitsmart.data.upi.UpiPayLink
import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.Debt
import com.darshan.splitsmart.domain.model.Group
import com.darshan.splitsmart.domain.model.Member
import com.darshan.splitsmart.domain.model.VpaError
import com.darshan.splitsmart.domain.model.VpaValidation
import com.darshan.splitsmart.domain.model.VpaValidator
import com.darshan.splitsmart.domain.repository.BillRepository
import com.darshan.splitsmart.domain.repository.GroupRepository
import com.darshan.splitsmart.domain.split.DebtSimplifier
import com.darshan.splitsmart.ui.ErrorReason
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.toErrorReason
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettleUpItem(val debt: Debt, val payer: Member, val payee: Member, val link: UpiPayLink?) {
    val missingVpa: Boolean get() = payee.vpa == null
}

data class SettleUpContent(val group: Group, val items: List<SettleUpItem>)

data class VpaDraft(val memberId: String, val text: String = "", val error: VpaError? = null, val saving: Boolean = false)

data class SettleUpUiState(
    val content: LoadState<SettleUpContent> = LoadState.Loading,
    val qrItem: SettleUpItem? = null,
    val vpaDraft: VpaDraft? = null,
)

sealed interface SettleUpEvent {
    data object NoUpiApp : SettleUpEvent
    data object ShareFailed : SettleUpEvent
    data object Failed : SettleUpEvent
}

/** One `upi://pay` link per simplified debt; pay, share as text, or show as a QR code. */
@HiltViewModel
class SettleUpViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groupRepository: GroupRepository,
    billRepository: BillRepository,
    private val launcher: UpiAppLauncher,
    dispatchers: DispatcherProvider,
) : ViewModel() {
    val groupId: String = checkNotNull(savedStateHandle[ARG_GROUP_ID]) { "groupId missing" }
    private val dialogs = MutableStateFlow(SettleUpUiState())
    private val _events = Channel<SettleUpEvent>(Channel.BUFFERED)
    val events: Flow<SettleUpEvent> = _events.receiveAsFlow()
    private var members: List<Member> = emptyList()

    private val content: Flow<LoadState<SettleUpContent>> =
        combine(groupRepository.observeGroup(groupId), groupRepository.observeMembers(groupId), billRepository.observeBills(groupId)) { group, members, bills ->
            this.members = members
            if (group == null) return@combine LoadState.Error(ErrorReason.NOT_FOUND)
            val items = buildItems(group, members, bills)
            if (items.isEmpty()) LoadState.Empty else LoadState.Content(SettleUpContent(group, items))
        }.onStart<LoadState<SettleUpContent>> { emit(LoadState.Loading) }.catch { emit(LoadState.Error(it.toErrorReason())) }.flowOn(dispatchers.default)

    val uiState: StateFlow<SettleUpUiState> = combine(content, dialogs) { c, d -> d.copy(content = c) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettleUpUiState())

    private fun buildItems(group: Group, members: List<Member>, bills: List<Bill>): List<SettleUpItem> {
        val byId = members.associateBy { it.id }
        val debts = runCatching { DebtSimplifier.simplify(DebtSimplifier.balances(bills)) }.getOrDefault(emptyList())
        return debts.mapNotNull { debt ->
            val payer = byId[debt.fromMemberId] ?: return@mapNotNull null
            val payee = byId[debt.toMemberId] ?: return@mapNotNull null
            val link = payee.vpa?.let { vpa -> runCatching { UpiLinkBuilder.build(vpa, payee.name, debt.amountPaise, "${group.name}$NOTE_SUFFIX") }.getOrNull() }
            SettleUpItem(debt, payer, payee, link)
        }
    }

    fun pay(item: SettleUpItem) {
        val link = item.link ?: return
        when (launcher.openUpiLink(link.uri)) {
            UpiLaunchResult.Opened -> Unit
            UpiLaunchResult.NoUpiApp -> _events.trySend(SettleUpEvent.NoUpiApp)
            UpiLaunchResult.Invalid -> _events.trySend(SettleUpEvent.Failed)
        }
    }

    fun share(text: String) {
        if (!launcher.shareText(text)) _events.trySend(SettleUpEvent.ShareFailed)
    }

    fun showQr(item: SettleUpItem) = dialogs.update { it.copy(qrItem = item) }
    fun dismissQr() = dialogs.update { it.copy(qrItem = null) }

    fun editVpa(memberId: String) = dialogs.update { it.copy(vpaDraft = VpaDraft(memberId, text = members.find { m -> m.id == memberId }?.vpa ?: "")) }
    fun dismissVpa() = dialogs.update { it.copy(vpaDraft = null) }
    fun onVpaChange(text: String) = dialogs.update { s -> s.copy(vpaDraft = s.vpaDraft?.copy(text = text.take(INPUT_LIMIT), error = null)) }

    fun saveVpa() {
        val draft = dialogs.value.vpaDraft ?: return
        if (draft.saving) return
        val vpa = when (val v = VpaValidator.validate(draft.text)) {
            is VpaValidation.Invalid -> { dialogs.update { it.copy(vpaDraft = draft.copy(error = v.error)) }; return }
            is VpaValidation.Valid -> v.vpa
        }
        val member = members.find { it.id == draft.memberId } ?: run { dismissVpa(); return }
        dialogs.update { it.copy(vpaDraft = draft.copy(saving = true)) }
        viewModelScope.launch {
            runCatching { groupRepository.updateMember(member.copy(vpa = vpa)) }
                .onSuccess { dismissVpa() }
                .onFailure { dialogs.update { it.copy(vpaDraft = draft.copy(saving = false)) }; _events.send(SettleUpEvent.Failed) }
        }
    }

    companion object {
        const val ARG_GROUP_ID = "groupId"
        /** Transaction note (`tn`) suffix shown in the payer's UPI app; a memo, not UI text. */
        const val NOTE_SUFFIX = " via SplitSmart"
        private const val INPUT_LIMIT = 320
    }
}
