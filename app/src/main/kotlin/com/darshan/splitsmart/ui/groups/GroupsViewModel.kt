package com.darshan.splitsmart.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darshan.splitsmart.core.DispatcherProvider
import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.Group
import com.darshan.splitsmart.domain.model.GroupSummary
import com.darshan.splitsmart.domain.model.Member
import com.darshan.splitsmart.domain.model.NameValidation
import com.darshan.splitsmart.domain.model.NameValidator
import com.darshan.splitsmart.domain.repository.BillRepository
import com.darshan.splitsmart.domain.repository.ProfileRepository
import com.darshan.splitsmart.domain.split.DebtSimplifier
import java.time.Instant
import com.darshan.splitsmart.domain.model.NameError
import com.darshan.splitsmart.domain.repository.GroupRepository
import com.darshan.splitsmart.domain.usecase.CreateGroupResult
import com.darshan.splitsmart.domain.usecase.CreateGroupUseCase
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

data class GroupDraft(val name: String = "", val error: NameError? = null, val submitting: Boolean = false)

/** "Your name" dialog draft (see [ProfileRepository]). */
data class NameDraft(val name: String = "", val error: NameError? = null)

/** One group card on the home screen. */
data class GroupCard(
    val summary: GroupSummary,
    val memberNames: List<String> = emptyList(),
    /** Newest bill, or the group's creation when it has no bills. */
    val lastActivity: Instant = summary.group.createdAt,
    /** Money still to move inside the group (sum of what creditors are owed). */
    val outstandingPaise: Long = 0,
    /** Your net balance in this group (positive = you are owed); null when you are not a member or no name is set. */
    val yourBalancePaise: Long? = null,
) {
    val group: Group get() = summary.group
}

/** Header numbers on the home screen. */
data class HomeOverview(
    val yourName: String? = null,
    val youAreOwedPaise: Long = 0,
    val youOwePaise: Long = 0,
    val outstandingPaise: Long = 0,
    val groupsWithYou: Int = 0,
    val groupCount: Int = 0,
)

data class GroupsUiState(
    val groups: LoadState<List<GroupCard>> = LoadState.Loading,
    val draft: GroupDraft? = null,
    val overview: HomeOverview = HomeOverview(),
    val nameDraft: NameDraft? = null,
)

sealed interface GroupsEvent {
    data class GroupCreated(val groupId: String) : GroupsEvent
    data object CreateFailed : GroupsEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GroupsViewModel @Inject constructor(
    repository: GroupRepository,
    billRepository: BillRepository,
    private val profile: ProfileRepository,
    private val createGroup: CreateGroupUseCase,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val reload = MutableStateFlow(0)
    private val draft = MutableStateFlow<GroupDraft?>(null)
    private val nameDraft = MutableStateFlow<NameDraft?>(null)
    private val _events = Channel<GroupsEvent>(Channel.BUFFERED)
    val events: Flow<GroupsEvent> = _events.receiveAsFlow()

    private val home: Flow<Pair<LoadState<List<GroupCard>>, HomeOverview>> = reload.flatMapLatest {
        combine(repository.observeGroupSummaries(), repository.observeAllMembers(), billRepository.observeAllBills(), profile.yourName) { summaries, members, bills, me ->
            val cards = buildCards(summaries, members, bills, me)
            val state: LoadState<List<GroupCard>> = if (cards.isEmpty()) LoadState.Empty else LoadState.Content(cards)
            state to overviewOf(cards, me)
        }
            .onStart { emit(LoadState.Loading to HomeOverview()) }
            .catch { emit(LoadState.Error(it.toErrorReason()) to HomeOverview()) }
    }.flowOn(dispatchers.default)

    val uiState: StateFlow<GroupsUiState> = combine(home, draft, nameDraft) { (g, o), d, n -> GroupsUiState(g, d, o, n) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupsUiState())

    // ----- your name -----
    fun openNameDialog(current: String?) = nameDraft.update { it ?: NameDraft(current.orEmpty()) }
    fun dismissNameDialog() = nameDraft.update { null }
    fun onNameDraftChange(name: String) = nameDraft.update { it?.copy(name = name.take(NAME_INPUT_LIMIT), error = null) }

    fun saveName() {
        val current = nameDraft.value ?: return
        when (val result = NameValidator.validate(current.name)) {
            is NameValidation.Invalid -> nameDraft.update { it?.copy(error = result.error) }
            is NameValidation.Valid -> {
                nameDraft.value = null
                viewModelScope.launch { runCatching { profile.setYourName(result.name) }.onFailure { _events.send(GroupsEvent.CreateFailed) } }
            }
        }
    }

    fun retry() = reload.update { it + 1 }

    fun openCreateDialog() = draft.update { it ?: GroupDraft() }

    fun dismissDialog() = draft.update { null }

    fun onDraftNameChange(name: String) = draft.update { it?.copy(name = name.take(NAME_INPUT_LIMIT), error = null) }

    fun submitCreate() {
        val current = draft.value ?: return
        if (current.submitting) return
        draft.value = current.copy(submitting = true)
        viewModelScope.launch {
            when (val result = createGroup(current.name)) {
                is CreateGroupResult.Created -> {
                    draft.value = null
                    _events.send(GroupsEvent.GroupCreated(result.group.id))
                }
                is CreateGroupResult.InvalidName -> draft.update { it?.copy(error = result.error, submitting = false) }
                is CreateGroupResult.Failed -> {
                    draft.update { it?.copy(submitting = false) }
                    _events.send(GroupsEvent.CreateFailed)
                }
            }
        }
    }

    companion object {
        /** Cards with balances, newest activity first. Pure so it can be unit-tested and run off the main thread. */
        fun buildCards(summaries: List<GroupSummary>, members: List<Member>, bills: List<Bill>, yourName: String?): List<GroupCard> {
            val membersByGroup = members.groupBy { it.groupId }
            val billsByGroup = bills.groupBy { it.groupId }
            val me = yourName?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            return summaries.map { summary ->
                val id = summary.group.id
                val groupMembers = membersByGroup[id].orEmpty()
                val groupBills = billsByGroup[id].orEmpty()
                val balances = runCatching { DebtSimplifier.balances(groupBills) }.getOrDefault(emptyMap())
                val you = me?.let { name -> groupMembers.firstOrNull { it.name.trim().lowercase() == name } }
                GroupCard(
                    summary = summary,
                    memberNames = groupMembers.map { it.name },
                    lastActivity = groupBills.maxOfOrNull { it.createdAt } ?: summary.group.createdAt,
                    outstandingPaise = balances.values.filter { it > 0 }.sum(),
                    yourBalancePaise = you?.let { balances[it.id] ?: 0L },
                )
            }.sortedByDescending { it.lastActivity }
        }

        fun overviewOf(cards: List<GroupCard>, yourName: String?): HomeOverview {
            val yours = cards.mapNotNull { it.yourBalancePaise }
            return HomeOverview(
                yourName = yourName,
                youAreOwedPaise = yours.filter { it > 0 }.sum(),
                youOwePaise = -yours.filter { it < 0 }.sum(),
                outstandingPaise = cards.sumOf { it.outstandingPaise },
                groupsWithYou = yours.size,
                groupCount = cards.size,
            )
        }

        /** Keeps a pasted novel from being held in memory; the validator enforces the real 60-char limit. */
        private const val NAME_INPUT_LIMIT = 200
    }
}
