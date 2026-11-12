package com.darshan.splitsmart.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darshan.splitsmart.domain.repository.GroupRepository
import com.darshan.splitsmart.domain.repository.Removal
import com.darshan.splitsmart.domain.repository.TrashRepository
import com.darshan.splitsmart.ui.undo.UndoCenter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How an Undo ended, when it needs saying. */
enum class UndoOutcome { Restored, Failed }

/**
 * What the shell itself needs: the groups the bar's Scan can open the camera for (null until loaded; archived groups only when
 * nothing else is left), and the delete that can still be undone, shown above whichever screen the person landed on.
 */
@HiltViewModel
class ShellViewModel @Inject constructor(
    groups: GroupRepository,
    private val trash: TrashRepository,
    private val undoCenter: UndoCenter,
) : ViewModel() {
    val scanChoices: StateFlow<List<ScanChoice>?> = groups.observeGroupSummaries()
        .map { summaries -> scanChoicesOf(summaries.map { it.group }) }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The delete Undo is offered for, if any. */
    val undoOffer: StateFlow<Removal?> = undoCenter.offer

    private val outcomes = Channel<UndoOutcome>(Channel.BUFFERED)
    val undoOutcomes: Flow<UndoOutcome> = outcomes.receiveAsFlow()

    /** Puts it back, once however often it is tapped. A put-back that can't be exact writes nothing and says so. */
    fun undo(removal: Removal) {
        if (!undoCenter.take(removal)) return
        viewModelScope.launch {
            val restored = runCatching { trash.restore(removal) }.getOrDefault(false)
            outcomes.send(if (restored) UndoOutcome.Restored else UndoOutcome.Failed)
        }
    }

    /** The window closed (or another message replaced it): the delete stands and its copy is dropped. */
    fun expire(removal: Removal) {
        undoCenter.take(removal)
    }
}
