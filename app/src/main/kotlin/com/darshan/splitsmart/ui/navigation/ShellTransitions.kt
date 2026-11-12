package com.darshan.splitsmart.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.darshan.splitsmart.ui.designsystem.motion.LocalMotion
import com.darshan.splitsmart.ui.designsystem.motion.MotionToken
import com.darshan.splitsmart.ui.designsystem.motion.SplitSmartMotion
import com.darshan.splitsmart.ui.designsystem.tokens.Radii

/** Turns a [ScreenTransition] into the four animations Navigation asks for, using only the design system's motion helpers. */
class ShellTransitions(private val motion: SplitSmartMotion) {
    fun enter(from: ShellRoute?, to: ShellRoute?): EnterTransition = when (ShellLogic.transition(from, to)) {
        ScreenTransition.SharedAxis -> motion.sharedAxisX(forward = true).targetContentEnter
        ScreenTransition.Rise -> if (to == ShellRoute.Scan) motion.enter() else motion.fadeThrough().targetContentEnter
        ScreenTransition.FadeThrough, ScreenTransition.ContainerTransform -> motion.fadeThrough().targetContentEnter
    }

    fun exit(from: ShellRoute?, to: ShellRoute?): ExitTransition = when (ShellLogic.transition(from, to)) {
        ScreenTransition.SharedAxis -> motion.sharedAxisX(forward = true).initialContentExit
        ScreenTransition.FadeThrough, ScreenTransition.Rise, ScreenTransition.ContainerTransform -> motion.fadeThrough().initialContentExit
    }

    fun popEnter(from: ShellRoute?, to: ShellRoute?): EnterTransition = when (ShellLogic.transition(from, to)) {
        ScreenTransition.SharedAxis -> motion.sharedAxisX(forward = false).targetContentEnter
        ScreenTransition.FadeThrough, ScreenTransition.Rise, ScreenTransition.ContainerTransform -> motion.fadeThrough().targetContentEnter
    }

    fun popExit(from: ShellRoute?, to: ShellRoute?): ExitTransition = when (ShellLogic.transition(from, to)) {
        ScreenTransition.SharedAxis -> motion.sharedAxisX(forward = false).initialContentExit
        ScreenTransition.Rise -> if (from == ShellRoute.Scan) motion.exit() else motion.fadeThrough().initialContentExit
        ScreenTransition.FadeThrough, ScreenTransition.ContainerTransform -> motion.fadeThrough().initialContentExit
    }
}

/** Set by the shell around the NavHost, null everywhere else (screen tests), where [containerTransform] does nothing. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = compositionLocalOf<AnimatedContentScope?> { null }

/** The shared-element key for one group: its card on the groups list and its group screen carry the same one. */
fun groupContainerKey(groupId: String): String = "group:$groupId"

/** The shared-element key for one bill: its row on the group screen and its bill screen. */
fun billContainerKey(billId: String): String = "bill:$billId"

/**
 * Marks the element as one end of a container transform: the element with the same [key] on the other screen is what it grows
 * into, its corners easing from a card's to the screen's. Reduced motion, or a screen outside the shell, leaves the element as
 * it is and the screens simply fade.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.containerTransform(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current
    val scope = LocalNavAnimatedScope.current
    val motion = LocalMotion.current
    if (shared == null || scope == null || motion.reduced) return this
    val bounds = remember(motion) { BoundsTransform { _, _ -> motion.spec<Rect>(MotionToken.Screen) } }
    return with(shared) {
        this@containerTransform.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = scope,
            boundsTransform = bounds,
            clipInOverlayDuringTransition = OverlayClip(Radii.mediumShape),
        )
    }
}

/**
 * Which element of a long list is one end of its container transform right now: the one under the finger, or the last one
 * opened (kept across the trip to the other screen, so the way back finds its pair). Every shared element registers with the
 * shell as it composes, so a list where every row carried one recomposed its visible rows on every fling.
 */
@Stable
class TransformAnchor(initial: String? = null) {
    var key: String? by mutableStateOf(initial)

    companion object {
        val Saver: Saver<TransformAnchor, String> = Saver(save = { it.key }, restore = { TransformAnchor(it) })
    }
}

@Composable
fun rememberTransformAnchor(): TransformAnchor = rememberSaveable(saver = TransformAnchor.Saver) { TransformAnchor() }

/**
 * [containerTransform] for one element of a long list: it takes part only while [anchor] points at [key]. A finger landing on
 * it points it there, and gives it back as soon as it travels past the touch slop (a scroll or a swipe, not a tap), so a fling
 * carries no shared element. A tap's finger lands at least a frame before its click, which is what the pair needs to match. A
 * click with no finger (TalkBack, a keyboard) opens with the plain fade. Outside the shell, or under reduced motion, it adds
 * nothing.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.listContainerTransform(anchor: TransformAnchor, key: String): Modifier {
    if (LocalSharedTransitionScope.current == null || LocalNavAnimatedScope.current == null || LocalMotion.current.reduced) return this
    val active by remember(anchor, key) { derivedStateOf { anchor.key == key } }
    return this
        .pointerInput(anchor, key) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val before = anchor.key
                anchor.key = key
                // A finger that travels is a scroll or a swipe, not a tap: hand the anchor back before the fling carries the row.
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        if (anchor.key == key) anchor.key = before
                        break
                    }
                }
            }
        }
        .then(if (active) Modifier.containerTransform(key) else Modifier)
}
