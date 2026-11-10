package com.darshan.splitsmart.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Who owns the status bar. Surfaces register while they are composed (newest last) and say whether they have finished
 * arriving; [darkIcons] applies [StatusBarLogic]. The activity reads it and sets the system's icon colour.
 */
class StatusBarController {
    private val entries = mutableStateListOf<StatusBarEntry>()
    private var nextId = 0L

    internal fun register(surface: Color, settled: Boolean): Long {
        val id = nextId++
        entries += StatusBarEntry(id, surface, settled)
        return id
    }

    internal fun update(id: Long, surface: Color, settled: Boolean) {
        val index = entries.indexOfFirst { it.id == id }
        if (index >= 0 && (entries[index].surface != surface || entries[index].settled != settled)) {
            entries[index] = StatusBarEntry(id, surface, settled)
        }
    }

    internal fun unregister(id: Long) {
        entries.removeAll { it.id == id }
    }

    /** Snapshot-backed: reading it in composition recomposes when ownership changes. */
    fun darkIcons(themeDark: Boolean): Boolean = StatusBarLogic.darkIcons(entries, themeDark)

    /** For tests and the load report. */
    val size: Int get() = entries.size
}

val LocalStatusBarController = staticCompositionLocalOf<StatusBarController?> { null }

/** Whether everything above this point has finished arriving (the intro to app hand-off). True outside the root. */
val LocalSurfaceSettled = staticCompositionLocalOf<() -> Boolean> { { true } }

/**
 * Declares that [surface] is what sits under the status bar while this is composed. [settled] = this surface has finished
 * arriving and really covers the top of the screen; until then the previous owner keeps the bar. No-op outside the app root.
 */
@Composable
fun StatusBarSurface(surface: Color, settled: Boolean = true) {
    val controller = LocalStatusBarController.current ?: return
    val parentSettled = LocalSurfaceSettled.current
    val effective = settled && parentSettled()
    val id = remember(controller) { LongArray(1) { -1L } }
    DisposableEffect(controller) {
        id[0] = controller.register(surface, effective)
        onDispose { controller.unregister(id[0]) }
    }
    SideEffect { if (id[0] >= 0) controller.update(id[0], surface, effective) }
}

/** True once this screen's enter transition has finished; stays true while it leaves, so it keeps the bar until replaced. */
fun AnimatedVisibilityScope.hasArrived(): Boolean = transition.currentState == EnterExitState.Visible
