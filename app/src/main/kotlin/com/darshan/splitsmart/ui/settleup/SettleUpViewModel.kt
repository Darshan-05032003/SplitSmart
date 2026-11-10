package com.darshan.splitsmart.ui.settleup

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darshan.splitsmart.core.DispatcherProvider
import com.darshan.splitsmart.data.upi.UpiAppLauncher
import com.darshan.splitsmart.data.upi.UpiLaunchResult
import com.darshan.splitsmart.data.upi.UpiLinkBuilder
import com.darshan.splitsmart.data.upi.UpiPayLink
import com.darshan.splitsmart.domain.analytics.AnalyticsEvent
import com.darshan.splitsmart.domain.analytics.AnalyticsTracker
import com.darshan.splitsmart.domain.analytics.SettleUpMethod
import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.Debt
import com.darshan.splitsmart.domain.model.Group
import com.darshan.splitsmart.domain.model.Member
import com.darshan.splitsmart.domain.model.Settlement
import com.darshan.splitsmart.domain.model.SettlementMethod
import com.darshan.splitsmart.domain.model.VpaError
import com.darshan.splitsmart.domain.model.VpaValidation
import com.darshan.splitsmart.domain.model.VpaValidator
import com.darshan.splitsmart.domain.repository.BillRepository
import com.darshan.splitsmart.domain.repository.GroupRepository
import com.darshan.splitsmart.domain.repository.SettlementRepository
import com.darshan.splitsmart.domain.split.BillCheck
import com.darshan.splitsmart.domain.split.DebtSimplifier
import com.darshan.splitsmart.domain.split.SplitCheck
import com.darshan.splitsmart.ui.ErrorReason
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.designsystem.effects.HapticEvent
import com.darshan.splitsmart.ui.designsystem.effects.UiEffect
import com.darshan.splitsmart.ui.designsystem.effects.UiEffectChannel
import com.darshan.splitsmart.ui.toErrorReason
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One simplified payment: [payer] pays [payee] the debt, through [link] when the payee has a UPI ID. */
@Immutable
data class SettleUpItem(val debt: Debt, val payer: Member, val payee: Member, val link: UpiPayLink?) {
    val missingVpa: Boolean get() = payee.vpa == null

    /** A UPI ID is on file but the amount is more than one UPI link may carry. */
    val tooLargeForUpi: Boolean get() = !missingVpa && link == null

    val key: String get() = settleKey(debt.fromMemberId, debt.toMemberId)
}

fun settleKey(fromMemberId: String, toMemberId: String): String = "$fromMemberId>$toMemberId"

@Immutable
data class SettleUpContent(
    val group: Group,
    val items: List<SettleUpItem>,
    /** Bills left out of the payments, each with the reason (a member left, never split, no payer). */
    val toFix: List<BillCheck> = emptyList(),
    /** Bills that move money. */
    val countedBills: Int = 0,
    /** Payments already marked as made in this group. */
    val history: Int = 0,
) {
    val totalPaise: Long get() = SettleUpLogic.total(items)

    /** Nobody owes anybody, and there was something to settle in the first place. */
    val allSquare: Boolean get() = items.isEmpty() && (countedBills > 0 || history > 0)
}

/**
 * The question settle-up asks before anything is recorded: did [fromName] pay [toName] [amountPaise]? With the UPI app it is
 * asked only once the person is back ([asking] false while they are away in it); "paid another way" asks at once. Kept in the
 * SavedStateHandle: the UPI app may be heavy enough for the system to stop SplitSmart while it is open.
 */
@Serializable
data class SettleCheck(
    val fromId: String,
    val toId: String,
    val fromName: String,
    val toName: String,
    val amountPaise: Long,
    val method: SettlementMethod,
    val asking: Boolean,
) {
    val key: String get() = settleKey(fromId, toId)
}

/** The payment just marked as made: the stamp and, when it was the last one, the all-square moment. */
@Immutable
data class SettledMoment(
    val settlementId: String,
    val fromName: String,
    val toName: String,
    val amountPaise: Long,
    val method: SettlementMethod,
    val allSquare: Boolean,
)

data class VpaDraft(val memberId: String, val text: String = "", val error: VpaError? = null, val saving: Boolean = false)

data class SettleUpUiState(
    val content: LoadState<SettleUpContent> = LoadState.Loading,
    val qrItem: SettleUpItem? = null,
    val vpaDraft: VpaDraft? = null,
    val check: SettleCheck? = null,
    val settled: SettledMoment? = null,
    val recording: Boolean = false,
)

sealed interface SettleUpEvent {
    data object NoUpiApp : SettleUpEvent
    data object ShareFailed : SettleUpEvent
    data object Failed : SettleUpEvent

    /** A payment was recorded; the screen offers Undo. */
    data class Recorded(val settlementId: String, val fromName: String, val toName: String, val amountPaise: Long) : SettleUpEvent
}

/**
 * The fewest payments that settle a group, one `upi://pay` link each. SplitSmart never moves money: Pay opens the person's UPI
 * app with the amount filled in, and nothing is marked as paid until they come back and say it went through. A UPI app's reply
 * is never read (the link is started, not started for a result), so it can never mark anything by itself.
 */
@HiltViewModel
class SettleUpViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val groupRepository: GroupRepository,
    private val billRepository: BillRepository,
    private val settlementRepository: SettlementRepository,
    private val launcher: UpiAppLauncher,
    private val analytics: AnalyticsTracker,
    dispatchers: DispatcherProvider,
) : ViewModel() {
    val groupId: String = checkNotNull(savedStateHandle[ARG_GROUP_ID]) { "groupId missing" }
    private val dialogs = MutableStateFlow(SettleUpUiState(check = restoreCheck()))
    private val _events = Channel<SettleUpEvent>(Channel.BUFFERED)
    val events: Flow<SettleUpEvent> = _events.receiveAsFlow()
    private val effectChannel = UiEffectChannel()
    val effects: Flow<UiEffect> = effectChannel.effects

    @Volatile private var members: List<Member> = emptyList()

    /** Bumped by [retry]: reading again after a failed read. */
    private val reload = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val content: Flow<LoadState<SettleUpContent>> = reload.flatMapLatest {
        combine(
            groupRepository.observeGroup(groupId),
            groupRepository.observeMembers(groupId),
            billRepository.observeBills(groupId),
            settlementRepository.observeSettlements(groupId),
        ) { group, members, bills, settlements ->
            this.members = members
            when {
                group == null -> LoadState.Error(ErrorReason.NOT_FOUND)
                bills.isEmpty() && settlements.isEmpty() -> LoadState.Empty
                else -> LoadState.Content(contentOf(group, members, bills, settlements))
            }
        }.onStart<LoadState<SettleUpContent>> { emit(LoadState.Loading) }.catch { emit(LoadState.Error(it.toErrorReason())) }
    }.flowOn(dispatchers.default)

    /** Reads the group again after a failed read. */
    fun retry() = reload.update { it + 1 }

    val uiState: StateFlow<SettleUpUiState> = combine(content, dialogs) { c, d -> d.copy(content = c) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), dialogs.value)

    private fun contentOf(group: Group, members: List<Member>, bills: List<Bill>, settlements: List<Settlement>): SettleUpContent {
        val countable = SplitCheck.countable(bills, members)
        val byId = members.associateBy { it.id }
        val items = SettleUpLogic.debts(countable, settlements).mapNotNull { debt ->
            val payer = byId[debt.fromMemberId] ?: return@mapNotNull null
            val payee = byId[debt.toMemberId] ?: return@mapNotNull null
            val link = payee.vpa?.let { vpa -> runCatching { UpiLinkBuilder.build(vpa, payee.name, debt.amountPaise, "${group.name}$NOTE_SUFFIX") }.getOrNull() }
            SettleUpItem(debt, payer, payee, link)
        }
        return SettleUpContent(group, items, SplitCheck.problems(bills, members), countable.size, settlements.size)
    }

    /** Opens the payee's UPI app with the amount filled in, then waits for the person to come back. One question at a time. */
    fun pay(item: SettleUpItem) {
        val state = dialogs.value
        if (state.check != null || state.recording) return
        val link = item.link ?: return
        analytics.track(AnalyticsEvent.SettleUpTap(SettleUpMethod.UPI, payeeHasVpa = true))
        when (launcher.openUpiLink(link.uri)) {
            UpiLaunchResult.Opened -> setCheck(item.check(link.amountPaise, SettlementMethod.UPI_APP, asking = false))
            UpiLaunchResult.NoUpiApp -> _events.trySend(SettleUpEvent.NoUpiApp)
            UpiLaunchResult.Invalid -> _events.trySend(SettleUpEvent.Failed)
        }
    }

    /** Cash, a bank transfer, anything SplitSmart did not open: asks before recording. */
    fun markPaid(item: SettleUpItem) {
        val state = dialogs.value
        if (state.check != null || state.recording) return
        setCheck(item.check(item.debt.amountPaise, SettlementMethod.OTHER, asking = true))
    }

    /** The screen is back in front (the activity resumed). After Pay, that is when to ask whether it went through. */
    fun onResumed() {
        val check = dialogs.value.check ?: return
        if (!check.asking) setCheck(check.copy(asking = true))
    }

    fun notYet() {
        if (dialogs.value.recording) return
        setCheck(null)
    }

    /** "Yes, it went through": the only way a payment is ever recorded. */
    fun confirmPaid() {
        val state = dialogs.value
        val check = state.check ?: return
        if (!check.asking || state.recording) return
        dialogs.update { it.copy(recording = true) }
        viewModelScope.launch {
            val current = runCatching { groupRepository.getMembers(groupId) }.getOrDefault(emptyList())
            val from = current.firstOrNull { it.id == check.fromId }
            val to = current.firstOrNull { it.id == check.toId }
            if (from == null || to == null) {
                // Someone left the group while the question was open: there is nobody to record a payment between.
                dialogs.update { it.copy(recording = false) }
                setCheck(null)
                effectChannel.send(UiEffect.Haptic(HapticEvent.Reject))
                _events.send(SettleUpEvent.Failed)
                return@launch
            }
            runCatching { settlementRepository.record(from, to, check.amountPaise, check.method) }
                .onSuccess { paid ->
                    val square = runCatching { allSquareNow() }.getOrDefault(false)
                    savedStateHandle[KEY_CHECK] = null
                    dialogs.update {
                        it.copy(
                            check = null,
                            recording = false,
                            settled = SettledMoment(paid.id, paid.fromName, paid.toName, paid.amountPaise, paid.method, square),
                        )
                    }
                    effectChannel.send(UiEffect.Haptic(HapticEvent.Confirm))
                    analytics.track(AnalyticsEvent.SettlementMarked(paid.method, square))
                    _events.send(SettleUpEvent.Recorded(paid.id, paid.fromName, paid.toName, paid.amountPaise))
                }
                .onFailure {
                    dialogs.update { it.copy(recording = false) }
                    effectChannel.send(UiEffect.Haptic(HapticEvent.Reject))
                    _events.send(SettleUpEvent.Failed)
                }
        }
    }

    /** Undo from the snackbar: the payment is deleted and its debt comes back. */
    fun undo(settlementId: String) {
        viewModelScope.launch {
            runCatching { settlementRepository.delete(settlementId) }
                .onSuccess { dialogs.update { s -> if (s.settled?.settlementId == settlementId) s.copy(settled = null) else s } }
                .onFailure { _events.send(SettleUpEvent.Failed) }
        }
    }

    private suspend fun allSquareNow(): Boolean {
        val members = groupRepository.getMembers(groupId)
        val bills = SplitCheck.countable(billRepository.observeBills(groupId).first(), members)
        return SettleUpLogic.debts(bills, settlementRepository.observeSettlements(groupId).first()).isEmpty()
    }

    fun share(text: String, item: SettleUpItem? = null) {
        analytics.track(AnalyticsEvent.SettleUpTap(SettleUpMethod.SHARE, payeeHasVpa = item?.link != null))
        if (!launcher.shareText(text)) _events.trySend(SettleUpEvent.ShareFailed)
    }

    fun showQr(item: SettleUpItem) {
        analytics.track(AnalyticsEvent.SettleUpTap(SettleUpMethod.QR, payeeHasVpa = true))
        dialogs.update { it.copy(qrItem = item) }
    }
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

    private fun SettleUpItem.check(amountPaise: Long, method: SettlementMethod, asking: Boolean) =
        SettleCheck(payer.id, payee.id, payer.name, payee.name, amountPaise, method, asking)

    private fun setCheck(check: SettleCheck?) {
        savedStateHandle[KEY_CHECK] = check?.let { checkJson.encodeToString(SettleCheck.serializer(), it) }
        dialogs.update { it.copy(check = check) }
    }

    private fun restoreCheck(): SettleCheck? =
        savedStateHandle.get<String>(KEY_CHECK)?.let { runCatching { checkJson.decodeFromString(SettleCheck.serializer(), it) }.getOrNull() }

    companion object {
        const val ARG_GROUP_ID = "groupId"
        /** Transaction note (`tn`) suffix shown in the payer's UPI app; a memo, not UI text. */
        const val NOTE_SUFFIX = " via SplitSmart"
        private const val INPUT_LIMIT = 320
        private const val KEY_CHECK = "settleCheck"
        private val checkJson = Json { ignoreUnknownKeys = true }
    }
}

/** Pure parts of settle-up, shared with the tests and the load runs. */
object SettleUpLogic {
    /** The fewest payments for these bills and payments already made, largest first; never throws. */
    fun debts(bills: List<Bill>, settlements: List<Settlement>): List<Debt> =
        runCatching { DebtSimplifier.simplify(DebtSimplifier.balances(bills, settlements)) }.getOrDefault(emptyList())
            .sortedWith(compareByDescending<Debt> { it.amountPaise }.thenBy { it.fromMemberId }.thenBy { it.toMemberId })

    /** Saturating sum, so a corrupt row can make a total wrong but never wrap it negative. */
    fun total(items: List<SettleUpItem>): Long {
        var total = 0L
        for (item in items) {
            val v = item.debt.amountPaise.coerceAtLeast(0)
            total = if (total > Long.MAX_VALUE - v) Long.MAX_VALUE else total + v
        }
        return total
    }
}
