package com.darshan.splitsmart.ui.designsystem.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.darshan.splitsmart.ui.designsystem.motion.MotionDurations
import kotlinx.coroutines.CompletableDeferred

enum class SnackbarResult {
    /** The user tapped the action: put things back. */
    Undo,

    /** The undo window ran out: the change is final. */
    TimedOut,

    /** Swiped or closed without the action: also final. */
    Dismissed,

    /** A newer message took its place: final for this one. */
    Replaced,
}

/** One message on screen. Every way out resolves it exactly once, so a double tap on Undo restores once. */
@Stable
class SsSnackbarData internal constructor(
    val message: String,
    val emoji: String?,
    val actionLabel: String?,
    val durationMs: Int,
    val id: Long,
) {
    private val result = CompletableDeferred<SnackbarResult>()
    val resolved: Boolean get() = result.isCompleted

    fun performAction(): Boolean = result.complete(SnackbarResult.Undo)
    fun dismiss(): Boolean = result.complete(SnackbarResult.Dismissed)
    fun timeOut(): Boolean = result.complete(SnackbarResult.TimedOut)
    internal fun replace(): Boolean = result.complete(SnackbarResult.Replaced)
    internal suspend fun await(): SnackbarResult = result.await()
}

/**
 * Holds the one snackbar on screen. `show` suspends until the message resolves, so a ViewModel or screen writes
 * `if (state.show("Group archived", actionLabel = "Undo") == SnackbarResult.Undo) restore()`. A new message replaces the current
 * one (which resolves as [SnackbarResult.Replaced]); a cancelled caller takes its message off screen.
 */
@Stable
class SsSnackbarState {
    var current: SsSnackbarData? by mutableStateOf(null)
        private set
    private var nextId = 0L

    suspend fun show(
        message: String,
        emoji: String? = null,
        actionLabel: String? = null,
        durationMs: Int = MotionDurations.UndoWindow,
    ): SnackbarResult {
        current?.replace()
        val data = SsSnackbarData(message, emoji, actionLabel, durationMs, nextId++)
        current = data
        try {
            return data.await()
        } finally {
            if (current === data) current = null
        }
    }

    companion object {
        /** How much of the undo window is left, for the draining timer bar. */
        fun remainingFraction(elapsedMs: Long, durationMs: Int): Float =
            if (durationMs <= 0) 0f else (1f - elapsedMs.toFloat() / durationMs).coerceIn(0f, 1f)
    }
}
