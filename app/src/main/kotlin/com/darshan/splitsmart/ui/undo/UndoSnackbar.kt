package com.darshan.splitsmart.ui.undo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.darshan.splitsmart.R
import com.darshan.splitsmart.domain.repository.Removal
import com.darshan.splitsmart.ui.designsystem.components.DisplayText
import com.darshan.splitsmart.ui.designsystem.components.SnackbarResult
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarHost
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.darshan.splitsmart.ui.designsystem.effects.LocalHaptics
import com.darshan.splitsmart.ui.designsystem.effects.reject
import com.darshan.splitsmart.ui.navigation.UndoOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

object UndoTags {
    const val HOST = "undo_snackbar"
}

/**
 * "Goa trip deleted · Undo", drawn by the shell above whichever screen the person landed on after a delete. Undo hands the
 * removal back to [onUndo]; when the window closes (timed out or swiped away) [onExpire] makes the delete final. A newer offer
 * replaces this one, and the older delete stands. An Undo that could not put everything back as it was says so.
 */
@Composable
fun UndoSnackbarHost(
    offer: Removal?,
    onUndo: (Removal) -> Unit,
    onExpire: (Removal) -> Unit,
    modifier: Modifier = Modifier,
    outcomes: Flow<UndoOutcome> = emptyFlow(),
    state: SsSnackbarState = remember { SsSnackbarState() },
) {
    val haptics = LocalHaptics.current
    val deleted = stringResource(R.string.undo_deleted_message)
    val undoLabel = stringResource(R.string.action_undo)
    val failed = stringResource(R.string.undo_restore_failed)
    val currentUndo by rememberUpdatedState(onUndo)
    val currentExpire by rememberUpdatedState(onExpire)
    LaunchedEffect(offer) {
        val removal = offer ?: return@LaunchedEffect
        when (state.show(String.format(deleted, DisplayText.safe(removal.name)), emoji = "🗑️", actionLabel = undoLabel)) {
            SnackbarResult.Undo -> currentUndo(removal)
            // A newer delete took the slot: the offer has already moved on.
            SnackbarResult.Replaced -> Unit
            else -> currentExpire(removal)
        }
    }
    LaunchedEffect(outcomes) {
        outcomes.collect { outcome ->
            if (outcome == UndoOutcome.Failed) {
                haptics.reject()
                state.show(failed, emoji = "😕")
            }
        }
    }
    SsSnackbarHost(state, modifier.testTag(UndoTags.HOST))
}
