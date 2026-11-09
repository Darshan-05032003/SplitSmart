package com.darshan.splitsmart.ui.designsystem.components

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darshan.splitsmart.ui.designsystem.motion.LocalMotion
import com.darshan.splitsmart.ui.designsystem.motion.MotionDurations
import com.darshan.splitsmart.ui.designsystem.motion.MotionEasing
import com.darshan.splitsmart.ui.designsystem.tokens.IllustrationColors
import com.darshan.splitsmart.ui.designsystem.tokens.Sizes
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A looping 0..1 phase for ambient motion, or null under reduced motion (callers then draw their resting frame). Restarts from 0,
 * so it reads as continuous only for cyclic drawings.
 */
@Composable
internal fun rememberAmbientPhase(durationMs: Int = MotionDurations.Ambient): State<Float>? {
    if (!LocalMotion.current.decorativeEnabled) return null
    val transition = rememberInfiniteTransition(label = "ambient")
    return transition.animateFloat(0f, 1f, ambientSpec(durationMs), label = "phase")
}

private fun ambientSpec(durationMs: Int): InfiniteRepeatableSpec<Float> =
    infiniteRepeatable(tween(durationMs, easing = MotionEasing.Standard.curve), RepeatMode.Restart)

/**
 * The branded loader: a marigold whose petals breathe in a wave around the flower while it turns. Under reduced motion it is a
 * still flower. TalkBack hears [label] as an indeterminate progress bar. Use it instead of any spinner.
 */
@Composable
fun BrandLoader(modifier: Modifier = Modifier, size: Dp = Sizes.loader, label: String = "Loading") {
    val colors = SplitSmartTheme.colors.illustration
    val phase = rememberAmbientPhase()
    Canvas(
        modifier
            .size(size)
            .clearAndSetSemantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            },
    ) {
        val p = phase?.value ?: 0f
        rotate(p * LOADER_TURN_DEGREES) { drawMarigold(center, this.size.minDimension / 2f, colors, wave = if (phase == null) -1f else p) }
    }
}

private const val LOADER_TURN_DEGREES = 90f

/** Three dots that hop in a wave, for inside buttons and small places. Still under reduced motion. */
@Composable
fun LoadingDots(color: Color, modifier: Modifier = Modifier) {
    val phase = rememberAmbientPhase(MotionDurations.Shimmer)
    Canvas(modifier.size(DOTS_WIDTH, Sizes.icon)) {
        val radius = Sizes.loadingDot.toPx() / 2f
        val gap = (this.size.width - radius * 2f * DOTS) / (DOTS - 1)
        val hopPx = Sizes.loadingDot.toPx()
        for (i in 0 until DOTS) {
            val local = phase?.let { ((it.value - i * DOT_OFFSET) % 1f + 1f) % 1f } ?: 0f
            val lift = if (local < 0.5f) sin(local * 2f * PI.toFloat()) * hopPx else 0f
            drawCircle(color, radius, Offset(radius + i * (radius * 2f + gap), center.y - lift))
        }
    }
}

private const val DOTS = 3
private const val DOT_OFFSET = 0.16f
private val DOTS_WIDTH = 30.dp

/**
 * A marigold: an outer ring of deep-orange petals, an inner ring of marigold petals, a seeded centre. [wave] in 0..1 makes the
 * petals swell one after another (the loader); a negative [wave] draws it still.
 */
internal fun DrawScope.drawMarigold(center: Offset, radius: Float, colors: IllustrationColors, wave: Float = -1f, petals: Int = 12) {
    val step = 2f * PI.toFloat() / petals
    for (i in 0 until petals) {
        val angle = i * step
        val swell = if (wave < 0f) 1f else 0.82f + 0.18f * (0.5f + 0.5f * cos(angle - wave * 2f * PI.toFloat()))
        val distance = radius * 0.66f
        val petal = Offset(center.x + cos(angle) * distance, center.y + sin(angle) * distance)
        scale(swell, pivot = petal) { drawCircle(colors.petalDeep, radius * 0.34f, petal) }
    }
    for (i in 0 until petals) {
        val angle = i * step + step / 2f
        val distance = radius * 0.4f
        drawCircle(colors.petal, radius * 0.27f, Offset(center.x + cos(angle) * distance, center.y + sin(angle) * distance))
    }
    drawCircle(colors.petalDeep, radius * 0.24f, center)
    drawCircle(colors.petal, radius * 0.13f, center)
}
