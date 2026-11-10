package com.darshan.splitsmart.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.darshan.splitsmart.ui.designsystem.components.drawMarigold
import com.darshan.splitsmart.ui.designsystem.components.rememberAmbientPhase
import com.darshan.splitsmart.ui.designsystem.components.sparklePath
import com.darshan.splitsmart.ui.designsystem.motion.LocalMotion
import com.darshan.splitsmart.ui.designsystem.tokens.MarigoldColors
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import kotlin.math.PI
import kotlin.math.sin

/** What each intro page shows. All of it is drawn here: receipts, a donut, a phone, coins and marigolds, nothing traced. */
enum class ArtKind {
    /** A receipt in a viewfinder with an AI sparkle: scanning. */
    Snap,

    /** A three-way donut with friends around it: splitting. */
    Split,

    /** A phone with a tick and coins flying off: settling up over UPI. */
    Settle,
}

/** How far the front layer bobs, as a fraction of the art's size. */
private const val BOB = 0.02f

/**
 * One intro illustration in three layers that move at different speeds as the page slides ([pageOffset], read in the layer
 * lambdas, so a swipe never recomposes). The front layer bobs gently while the page rests (decorative: still under reduced motion).
 * Hidden from TalkBack: the page title says what matters.
 */
@Composable
fun OnboardingArt(kind: ArtKind, pageOffset: () -> Float, modifier: Modifier = Modifier) {
    val colors = SplitSmartTheme.colors
    val motion = LocalMotion.current
    val reduced = motion.reduced
    val bob = rememberAmbientPhase()
    Box(modifier.clearAndSetSemantics { }) {
        for (layer in listOf(ArtLayer.Back, ArtLayer.Mid, ArtLayer.Front)) {
            Spacer(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        translationX = ParallaxLogic.shift(pageOffset(), size.width, layer.depth, reduced)
                        if (layer == ArtLayer.Front && bob != null) translationY = sin(bob.value * 2f * PI.toFloat()) * BOB * size.height
                    }
                    .drawWithCache {
                        val u = size.minDimension / 100f
                        val left = (size.width - 100f * u) / 2f
                        val top = (size.height - 100f * u) / 2f
                        val parts = ArtParts(u)
                        onDrawBehind {
                            translate(left, top) { drawLayer(kind, layer, parts, colors) }
                        }
                    },
            )
        }
    }
}

/** Fixed spots in the 100 × 100 grid. */
private val ConfettiDots = listOf(Offset(12f, 20f), Offset(88f, 14f), Offset(92f, 72f), Offset(8f, 64f), Offset(70f, 94f), Offset(26f, 92f))
private val ReceiptLines = listOf(32f to 60f, 40f to 54f, 48f to 58f, 56f to 50f)
private val Brackets = listOf(
    Offset(24f, 16f) to Offset(1f, 1f), Offset(76f, 16f) to Offset(-1f, 1f),
    Offset(24f, 86f) to Offset(1f, -1f), Offset(76f, 86f) to Offset(-1f, -1f),
)
private val Seats = listOf(Offset(18f, 28f), Offset(84f, 40f), Offset(62f, 88f))
private val Coins = listOf(Offset(20f, 40f) to 7f, Offset(82f, 64f) to 9f)

/** Donut shares in degrees: three friends, three slices. */
private val DonutSweeps = listOf(128f, 104f, 92f)

/** Paths and strokes built once per size, in a 100 × 100 design grid scaled by [u]. */
private class ArtParts(val u: Float) {
    val receipt = Path().apply {
        val l = 32f * u
        val r = 68f * u
        val t = 22f * u
        val b = 80f * u
        moveTo(l, t)
        lineTo(r, t)
        lineTo(r, b)
        val teeth = 6
        val step = (r - l) / teeth
        for (i in 1..teeth) {
            lineTo(r - step * (i - 0.5f), b - 3f * u)
            lineTo(r - step * i, b)
        }
        close()
    }
    val thin = Stroke(width = 2.6f * u, cap = StrokeCap.Round)
    val bracket = Stroke(width = 3.6f * u, cap = StrokeCap.Round)
    val donut = Stroke(width = 12f * u, cap = StrokeCap.Round)
    val check = Path().apply {
        moveTo(41f * u, 50f * u)
        lineTo(48f * u, 57f * u)
        lineTo(60f * u, 43f * u)
    }
    val tick = Stroke(width = 5f * u, cap = StrokeCap.Round)
    val trail = Stroke(width = 2f * u, cap = StrokeCap.Round)
    val sparkle = sparklePath(Offset(76f * u, 22f * u), 8f * u)
    val sparkleSmall = sparklePath(Offset(84f * u, 36f * u), 4f * u)
}

private fun DrawScope.drawLayer(kind: ArtKind, layer: ArtLayer, p: ArtParts, colors: MarigoldColors) {
    val u = p.u
    val art = colors.illustration
    when (layer) {
        ArtLayer.Back -> {
            drawCircle(art.paper.copy(alpha = 0.55f), radius = 40f * u, center = Offset(50f * u, 50f * u))
            ConfettiDots.forEachIndexed { i, d -> drawCircle(colors.confetti[(i + kind.ordinal) % colors.confetti.size], 2.4f * u, Offset(d.x * u, d.y * u)) }
        }
        ArtLayer.Mid -> when (kind) {
            ArtKind.Snap -> rotate(-6f, pivot = Offset(50f * u, 50f * u)) {
                drawPath(p.receipt, art.paper)
                drawPath(p.receipt, art.ink, style = Stroke(1.6f * u))
                for ((y, end) in ReceiptLines) {
                    drawLine(art.ink.copy(alpha = 0.7f), Offset(38f * u, y * u), Offset(end * u, y * u), strokeWidth = p.thin.width, cap = StrokeCap.Round)
                }
                drawLine(art.petalDeep, Offset(38f * u, 68f * u), Offset(62f * u, 68f * u), strokeWidth = p.bracket.width, cap = StrokeCap.Round)
            }
            ArtKind.Split -> {
                val box = Size(48f * u, 48f * u)
                val topLeft = Offset(26f * u, 26f * u)
                var start = -90f
                DonutSweeps.forEachIndexed { i, sweep ->
                    val color = when (i) {
                        0 -> art.petal
                        1 -> art.leaf
                        else -> art.chilli
                    }
                    drawArc(color, start + 6f, sweep - 12f, useCenter = false, topLeft = topLeft, size = box, style = p.donut)
                    start += sweep
                }
                drawCircle(art.ink, 3f * u, Offset(50f * u, 50f * u))
            }
            ArtKind.Settle -> {
                drawRoundRect(art.ink, Offset(32f * u, 16f * u), Size(36f * u, 68f * u), CornerRadius(7f * u))
                drawRoundRect(art.paper, Offset(35f * u, 21f * u), Size(30f * u, 56f * u), CornerRadius(4f * u))
                drawCircle(art.mint, 11f * u, Offset(50f * u, 50f * u))
                drawPath(p.check, art.leaf, style = p.tick)
                drawRoundRect(art.petal, Offset(39f * u, 66f * u), Size(22f * u, 6f * u), CornerRadius(3f * u))
            }
        }
        ArtLayer.Front -> when (kind) {
            ArtKind.Snap -> {
                for ((c, dir) in Brackets) {
                    val o = Offset(c.x * u, c.y * u)
                    drawLine(art.ink, o, Offset(o.x + dir.x * 9f * u, o.y), strokeWidth = p.bracket.width, cap = StrokeCap.Round)
                    drawLine(art.ink, o, Offset(o.x, o.y + dir.y * 9f * u), strokeWidth = p.bracket.width, cap = StrokeCap.Round)
                }
                drawPath(p.sparkle, colors.info.content)
                drawPath(p.sparkleSmall, colors.info.content)
                drawMarigold(Offset(18f * u, 80f * u), 9f * u, art)
            }
            ArtKind.Split -> {
                Seats.forEachIndexed { i, s ->
                    val tone = colors.avatars[(i * 2 + 1) % colors.avatars.size]
                    val c = Offset(s.x * u, s.y * u)
                    drawCircle(art.paper, 10.5f * u, c)
                    drawCircle(tone.background, 9f * u, c)
                    drawCircle(tone.content, 1.3f * u, Offset(c.x - 3f * u, c.y - 1.5f * u))
                    drawCircle(tone.content, 1.3f * u, Offset(c.x + 3f * u, c.y - 1.5f * u))
                    drawArc(tone.content, 20f, 140f, useCenter = false, topLeft = Offset(c.x - 4f * u, c.y - 2f * u), size = Size(8f * u, 7f * u), style = p.trail)
                }
            }
            ArtKind.Settle -> {
                for ((c, r) in Coins) {
                    val o = Offset(c.x * u, c.y * u)
                    drawCircle(art.petalDeep, r * u, o)
                    drawCircle(art.petal, (r - 2f) * u, o)
                    drawLine(art.ink.copy(alpha = 0.35f), Offset(o.x - (r + 3f) * u, o.y + 2f * u), Offset(o.x - (r + 9f) * u, o.y + 4f * u), strokeWidth = p.trail.width, cap = StrokeCap.Round)
                }
                drawMarigold(Offset(80f * u, 20f * u), 9f * u, art)
            }
        }
        ArtLayer.Text -> Unit
    }
}
