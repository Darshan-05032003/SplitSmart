package com.darshan.splitsmart.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darshan.splitsmart.core.DispatcherProvider
import com.darshan.splitsmart.domain.model.GroupSummary
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupDraft(val name: String = "", val error: NameError? = null, val submitting: Boolean = false)

data class GroupsUiState(
    val groups: LoadState<List<GroupSummary>> = LoadState.Loading,
    val draft: GroupDraft? = null,
)

sealed interface GroupsEvent {
    data class GroupCreated(val groupId: String) : GroupsEvent
    data object CreateFailed : GroupsEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GroupsViewModel @Inject constructor(
    repository: GroupRepository,
    private val createGroup: CreateGroupUseCase,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {
    private val reload = MutableStateFlow(0)
    private val draft = MutableStateFlow<GroupDraft?>(null)
    private val _events = Channel<GroupsEvent>(Channel.BUFFERED)
    val events: Flow<GroupsEvent> = _events.receiveAsFlow()

    private val groups: Flow<LoadState<List<GroupSummary>>> = reload.flatMapLatest {
        repository.observeGroupSummaries()
            .map<List<GroupSummary>, LoadState<List<GroupSummary>>> { if (it.isEmpty()) LoadState.Empty else LoadState.Content(it) }
            .onStart { emit(LoadState.Loading) }
            .catch { emit(LoadState.Error(it.toErrorReason())) }
    }.flowOn(dispatchers.default)

    val uiState: StateFlow<GroupsUiState> = combine(groups, draft) { g, d -> GroupsUiState(g, d) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupsUiState())

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

    private companion object {
        /** Keeps a pasted novel from being held in memory; the validator enforces the real 60-char limit. */
        const val NAME_INPUT_LIMIT = 200
    }
}
