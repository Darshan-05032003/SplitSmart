package com.darshan.splitsmart.ui.designsystem.modifiers

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.darshan.splitsmart.ui.designsystem.motion.LocalMotion
import com.darshan.splitsmart.ui.designsystem.motion.MotionToken
import com.darshan.splitsmart.ui.designsystem.motion.SplitSmartMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** How hard something squashes under a finger. */
enum class Squash(val widen: Float, val flatten: Float) {
    /** Buttons and the FAB: visibly squishy. */
    Chunky(widen = 0.05f, flatten = 0.09f),

    /** Cards, rows and chips: a nudge, so a list doesn't wobble. */
    Subtle(widen = 0.012f, flatten = 0.025f),
}

/**
 * Pure squash-and-stretch. Progress 1 is fully pressed (wider, flatter). The Bouncy release spring carries progress below 0 for
 * a moment, and that overshoot is the stretch (taller, narrower), exaggerated by [STRETCH_GAIN] so it reads.
 */
object SquashMath {
    const val STRETCH_GAIN = 2.5f
    private const val MIN_PROGRESS = -0.4f

    private fun shaped(progress: Float): Float {
        val p = progress.coerceIn(MIN_PROGRESS, 1f)
        return if (p < 0f) p * STRETCH_GAIN else p
    }

    /** [widthPx] and [maxWidenPx] cap how far a wide element grows, so a full-width button squashes as much as a small one. */
    fun scaleX(progress: Float, squash: Squash, widthPx: Float = 0f, maxWidenPx: Float = 0f): Float {
        val widen = if (widthPx > 0f && maxWidenPx > 0f) minOf(squash.widen, maxWidenPx / widthPx) else squash.widen
        return 1f + widen * shaped(progress)
    }
    fun scaleY(progress: Float, squash: Squash): Float = 1f - squash.flatten * shaped(progress)

    /** How far into its depth the element has travelled: never above its shadow, never below the page. */
    fun depthTravel(progress: Float): Float = progress.coerceIn(0f, 1f)
}

/**
 * Press state of a tappable element: [progress] runs 0 (rest) to 1 (pressed), dipping below 0 on the release bounce. Read it
 * inside `graphicsLayer` or `drawBehind`, never in composition. Hoist it to drive something else from the same press.
 */
@Stable
class PressFeedback internal constructor(initial: Float, private val scope: CoroutineScope, private val motion: SplitSmartMotion) {
    private val animatable = Animatable(initial)
    val progress: Float get() = animatable.value

    /** Finger down. Starts on the next frame: feedback is never delayed by the tap timeout `clickable` applies in scrollers. */
    fun press() {
        scope.launch { animatable.animateTo(1f, motion.spec(MotionToken.Press)) }
    }

    fun release() {
        scope.launch { animatable.animateTo(0f, motion.spec(MotionToken.Release)) }
    }
}

/** [initial] 1f renders the pressed look without a finger, for the catalog and screenshot tests. */
@Composable
fun rememberPressFeedback(initial: Float = 0f): PressFeedback {
    val scope = rememberCoroutineScope()
    val motion = LocalMotion.current
    return remember(motion) { PressFeedback(initial, scope, motion) }
}

/**
 * Feeds [state] from raw pointer events on the Initial pass, so it fires before children and scrollers and consumes nothing:
 * `clickable`, scrolling and drags keep working.
 */
fun Modifier.trackPress(state: PressFeedback, enabled: Boolean = true): Modifier =
    if (!enabled) {
        this
    } else {
        pointerInput(state) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                state.press()
                waitForUpOrCancellation(PointerEventPass.Initial)
                state.release()
            }
        }
    }

/** Squash and stretch from [state], anchored at the bottom edge like something sitting on a table. Layer only: no recomposition. */
fun Modifier.squash(state: PressFeedback, squash: Squash = Squash.Chunky): Modifier = graphicsLayer { squashAndSink(state.progress, squash) }

/**
 * The one press transform every tactile element uses: sink [depthPx] into its base as it is pressed, squash wider and flatter,
 * stretch on the rebound. Anchored at the bottom edge. Call it inside `graphicsLayer { }`.
 */
fun GraphicsLayerScope.squashAndSink(progress: Float, squash: Squash, depthPx: Float = 0f) {
    translationY = depthPx * SquashMath.depthTravel(progress)
    scaleX = SquashMath.scaleX(progress, squash, size.width, MaxWiden.toPx())
    scaleY = SquashMath.scaleY(progress, squash)
    transformOrigin = BottomCenter
}

private val BottomCenter = TransformOrigin(0.5f, 1f)

/** A pressed element grows at most this much in total, however wide it is. */
private val MaxWiden = 8.dp
