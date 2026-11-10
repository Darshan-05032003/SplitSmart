package com.darshan.splitsmart.ui.designsystem.modifiers

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView

/** `FLAG_WINDOW_IS_PARTIALLY_OBSCURED` (API 29). Inlined: on older phones the flag is simply never set. */
@SuppressLint("InlinedApi")
private const val OBSCURED_FLAGS = MotionEvent.FLAG_WINDOW_IS_OBSCURED or MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED

/** True when another app's window covered the touch: the classic tapjacking overlay. */
fun MotionEvent.isObscured(): Boolean = flags and OBSCURED_FLAGS != 0

/**
 * Tapjacking protection for one action that moves or records money (`filterTouchesWhenObscured` for a composable): a touch
 * delivered through another app's window, fully or partly over ours, is swallowed before the action sees it. Put it before the
 * button's own modifiers so it runs first.
 */
fun Modifier.filterTouchesWhenObscured(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        // Once any event of a gesture came through an overlay, the rest of that gesture is swallowed too.
        var blocked = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.motionEvent?.isObscured() == true) blocked = true
            if (blocked) event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}

/**
 * The same at window level while a money screen is showing: the Compose view drops obscured touches before they reach any
 * composable (Android's `View.setFilterTouchesWhenObscured`). The previous value comes back when the screen leaves.
 */
@Composable
fun FilterObscuredTouches() {
    val view = LocalView.current
    DisposableEffect(view) {
        val before = view.filterTouchesWhenObscured
        view.filterTouchesWhenObscured = true
        onDispose { view.filterTouchesWhenObscured = before }
    }
}
