package com.darshan.splitsmart.ui.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.darshan.splitsmart.ui.designsystem.tokens.AvatarTone
import com.darshan.splitsmart.ui.designsystem.tokens.IllustrationColors
import com.darshan.splitsmart.ui.designsystem.tokens.Sizes
import com.darshan.splitsmart.ui.designsystem.tokens.Spacing
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The code-drawn illustrations. Each is original: marigolds, receipts, friends and confetti dots, nothing traced. */
enum class IllustrationKind {
    /** No groups yet: three friends under a marigold. */
    Groups,

    /** No bills yet: a receipt with a marigold stamp. */
    Bills,

    /** Everyone is square: a marigold in bloom with a check. */
    Settled,

    /** Nothing matches: a magnifier over a receipt. */
    Search,

    /** Something broke: a receipt torn in two. */
    Error,
}

/** How far an illustration bobs, as a fraction of its size, and how much the marigold sways, in degrees. */
private const val BOB = 0.025f
private const val SWAY_DEGREES = 8f
private const val SPRINKLE_ALPHA = 0.85f

/** Receipt placement per illustration, in a 100 × 100 design grid. */
private val ReceiptBox = Rect(24f, 14f, 68f, 84f)
private val SearchReceiptBox = Rect(22f, 16f, 64f, 82f)
private val TornReceiptBox = Rect(26f, 12f, 70f, 86f)

/**
 * Draws an illustration. It bobs gently and its marigold sways (decorative: still under reduced motion or with [animate] false).
 * Every path and stroke is built once per size in the draw cache; each frame only moves them. Hidden from TalkBack: the title
 * next to it says what matters.
 */
@Composable
fun Illustration(kind: IllustrationKind, modifier: Modifier = Modifier, size: Dp = Sizes.illustration, animate: Boolean = true) {
    val colors = SplitSmartTheme.colors
    val art = colors.illustration
    val avatars = colors.avatars
    val confetti = colors.confetti
    val phase = if (animate) rememberAmbientPhase() else null
    Spacer(
        modifier
            .size(size)
            .clearAndSetSemantics { }
            .drawWithCache {
                val u = this.size.minDimension / 100f
                val parts = IllustrationParts(
                    u = u,
                    sprinkle = sprinkleDots(kind.ordinal.toLong()),
                    receipt = receiptPath(
                        when (kind) {
                            IllustrationKind.Search -> SearchReceiptBox
                            IllustrationKind.Error -> TornReceiptBox
                            else -> ReceiptBox
                        },
                        u,
                    ),
                    check = Path().apply {
                        moveTo(43f * u, 50f * u)
                        lineTo(48.5f * u, 55.5f * u)
                        lineTo(58f * u, 45f * u)
                    },
                    thin = Stroke(width = 2.2f * u, cap = StrokeCap.Round),
                    hairline = Stroke(1.2f * u),
                    outline = Stroke(6f * u),
                    bold = Stroke(width = 4f * u, cap = StrokeCap.Round),
                    lens = Stroke(5f * u),
                    tornTop = tearRegion(u, above = true),
                    tornBottom = tearRegion(u, above = false),
                )
                onDrawBehind {
                    val p = phase?.value ?: 0f
                    val wave = sin(p * 2f * PI.toFloat())
                    translate(top = wave * BOB * this.size.height) {
                        for (dot in parts.sprinkle) {
                            drawCircle(confetti[dot.color % confetti.size], dot.radius * u, Offset(dot.x * u, dot.y * u), alpha = SPRINKLE_ALPHA)
                        }
                        when (kind) {
                            IllustrationKind.Groups -> drawGroups(parts, art, avatars, wave)
                            IllustrationKind.Bills -> drawBills(parts, art, wave)
                            IllustrationKind.Settled -> drawSettled(parts, art, wave)
                            IllustrationKind.Search -> drawSearch(parts, art)
                            IllustrationKind.Error -> drawTorn(parts, art)
                        }
                    }
                }
            },
    )
}

private class IllustrationParts(
    val u: Float,
    val sprinkle: List<Dot>,
    val receipt: Path,
    val check: Path,
    val thin: Stroke,
    val hairline: Stroke,
    val outline: Stroke,
    val bold: Stroke,
    val lens: Stroke,
    val tornTop: Path,
    val tornBottom: Path,
)

private class Dot(val x: Float, val y: Float, val radius: Float, val color: Int)

/** Seeded so every frame and every screenshot shows the same sprinkle. */
private fun sprinkleDots(seed: Long): List<Dot> {
    val random = Random(seed + 11)
    return List(9) {
        val angle = random.nextFloat() * 2f * PI.toFloat()
        val distance = 40f + random.nextFloat() * 8f
        Dot(50f + cos(angle) * distance, 50f + sin(angle) * distance, 1.4f + random.nextFloat() * 1.4f, random.nextInt(5))
    }
}

/** A receipt with rounded top corners and a zigzag tear-off edge, in px for a design-grid [box]. */
private fun receiptPath(box: Rect, u: Float): Path {
    val rect = Rect(box.left * u, box.top * u, box.right * u, box.bottom * u)
    val corner = 4f * u
    val tooth = 5f * u
    return Path().apply {
        moveTo(rect.left, rect.top + corner)
        quadraticTo(rect.left, rect.top, rect.left + corner, rect.top)
        lineTo(rect.right - corner, rect.top)
        quadraticTo(rect.right, rect.top, rect.right, rect.top + corner)
        lineTo(rect.right, rect.bottom)
        var x = rect.right
        var down = false
        while (x > rect.left) {
            x = (x - tooth).coerceAtLeast(rect.left)
            lineTo(x, if (down) rect.bottom else rect.bottom - tooth * 0.7f)
            down = !down
        }
        close()
    }
}

private fun DrawScope.drawFriend(center: Offset, radius: Float, tone: AvatarTone, parts: IllustrationParts, ink: Color) {
    drawCircle(tone.background, radius, center)
    val eye = radius * 0.12f
    drawCircle(tone.content, eye, Offset(center.x - radius * 0.32f, center.y - radius * 0.12f))
    drawCircle(tone.content, eye, Offset(center.x + radius * 0.32f, center.y - radius * 0.12f))
    drawArc(
        tone.content,
        startAngle = 20f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(center.x - radius * 0.4f, center.y - radius * 0.2f),
        size = Size(radius * 0.8f, radius * 0.6f),
        style = parts.thin,
    )
    drawCircle(ink, radius, center, alpha = 0.08f, style = parts.hairline)
}

private fun DrawScope.drawGroups(parts: IllustrationParts, art: IllustrationColors, avatars: List<AvatarTone>, wave: Float) {
    val u = parts.u
    drawFriend(Offset(30f * u, 62f * u), 17f * u, avatars[1], parts, art.ink)
    drawFriend(Offset(70f * u, 62f * u), 17f * u, avatars[3], parts, art.ink)
    drawFriend(Offset(50f * u, 56f * u), 20f * u, avatars[0], parts, art.ink)
    rotate(wave * SWAY_DEGREES, pivot = Offset(50f * u, 24f * u)) { drawMarigold(Offset(50f * u, 24f * u), 13f * u, art) }
}

private fun DrawScope.drawReceipt(parts: IllustrationParts, box: Rect, art: IllustrationColors) {
    val u = parts.u
    drawPath(parts.receipt, art.ink, alpha = 0.1f, style = parts.outline)
    drawPath(parts.receipt, art.paper)
    val line = 3f * u
    val left = box.left * u
    val right = box.right * u
    val width = right - left
    for (i in 0 until 4) {
        val y = box.top * u + 12f * u + i * 8f * u
        drawLine(art.ink, Offset(left + 8f * u, y), Offset(left + width * (0.42f + 0.08f * (i % 2)), y), line, StrokeCap.Round, alpha = LINE_ALPHA)
        drawLine(art.ink, Offset(right - 14f * u, y), Offset(right - 8f * u, y), line, StrokeCap.Round, alpha = LINE_ALPHA)
    }
    val totalY = box.top * u + 50f * u
    if (totalY < box.bottom * u - 8f * u) {
        drawLine(art.ink, Offset(left + 8f * u, totalY), Offset(right - 8f * u, totalY), 4f * u, StrokeCap.Round, alpha = 0.55f)
    }
}

private fun DrawScope.drawBills(parts: IllustrationParts, art: IllustrationColors, wave: Float) {
    val u = parts.u
    rotate(-7f, pivot = Offset(46f * u, 50f * u)) { drawReceipt(parts, ReceiptBox, art) }
    rotate(wave * SWAY_DEGREES, pivot = Offset(72f * u, 70f * u)) { drawMarigold(Offset(72f * u, 70f * u), 14f * u, art) }
}

private fun DrawScope.drawSettled(parts: IllustrationParts, art: IllustrationColors, wave: Float) {
    val u = parts.u
    val center = Offset(50f * u, 50f * u)
    rotate(wave * SWAY_DEGREES, pivot = center) { drawMarigold(center, 34f * u, art, petals = 14) }
    drawCircle(art.paper, 13f * u, center)
    drawPath(parts.check, art.leaf, style = parts.bold)
}

private fun DrawScope.drawSearch(parts: IllustrationParts, art: IllustrationColors) {
    val u = parts.u
    drawReceipt(parts, SearchReceiptBox, art)
    val lens = Offset(62f * u, 56f * u)
    drawLine(art.ink, Offset(lens.x + 12f * u, lens.y + 12f * u), Offset(lens.x + 24f * u, lens.y + 24f * u), 7f * u, StrokeCap.Round)
    drawCircle(art.mint, 15f * u, lens, alpha = 0.85f)
    drawCircle(art.ink, 15f * u, lens, style = parts.lens)
}

/** The region above or below a zigzag tear across the middle of the 100-unit grid. */
private fun tearRegion(u: Float, above: Boolean): Path = Path().apply {
    val edge = if (above) 0f else 100f * u
    moveTo(0f, edge)
    lineTo(0f, 50f * u)
    var x = 0f
    var up = true
    while (x < 100f * u) {
        x = (x + 5f * u).coerceAtMost(100f * u)
        lineTo(x, if (up) 46f * u else 52f * u)
        up = !up
    }
    lineTo(100f * u, edge)
    close()
}

private const val LINE_ALPHA = 0.28f

/** The same receipt twice, clipped above and below the tear, the halves knocked apart. */
private fun DrawScope.drawTorn(parts: IllustrationParts, art: IllustrationColors) {
    val u = parts.u
    rotate(-9f, pivot = Offset(40f * u, 40f * u)) {
        clipPath(parts.tornTop) { drawReceipt(parts, TornReceiptBox, art) }
    }
    rotate(8f, pivot = Offset(60f * u, 66f * u)) {
        translate(left = 6f * u, top = 8f * u) { clipPath(parts.tornBottom) { drawReceipt(parts, TornReceiptBox, art) } }
    }
    drawCircle(art.chilli, 3f * u, Offset(78f * u, 30f * u))
    drawCircle(art.petal, 2.2f * u, Offset(18f * u, 66f * u))
}

/**
 * A designed empty or first-run state: an illustration, a friendly title, one line of help and, when there is one, the one action
 * that fixes it (a primary button). The title is a heading for TalkBack.
 */
@Composable
fun EmptyState(
    illustration: IllustrationKind,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionEmoji: String? = null,
) {
    val type = SplitSmartTheme.type
    val colors = SplitSmartTheme.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Illustration(illustration)
        Spacer(Modifier.size(Spacing.xs))
        BasicText(title, Modifier.semantics { heading() }, style = type.display.copy(color = colors.text, textAlign = TextAlign.Center))
        BasicText(message, style = type.body.copy(color = colors.textSecondary, textAlign = TextAlign.Center))
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.size(Spacing.xs))
            ChunkyButton(actionLabel, onAction, emoji = actionEmoji)
        }
    }
}

/** A designed error: the torn receipt, what went wrong in plain words, and a retry. */
@Composable
fun ErrorState(
    title: String,
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryLabel: String = "Try again",
    retryStyle: ButtonStyle = ButtonStyle.Primary,
) {
    val type = SplitSmartTheme.type
    val colors = SplitSmartTheme.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Illustration(IllustrationKind.Error, size = Sizes.illustrationSmall)
        BasicText(title, Modifier.semantics { heading() }, style = type.title.copy(color = colors.text, textAlign = TextAlign.Center))
        BasicText(message, style = type.body.copy(color = colors.textSecondary, textAlign = TextAlign.Center))
        Spacer(Modifier.size(Spacing.xs))
        ChunkyButton(retryLabel, onRetry, style = retryStyle, size = ButtonSize.Compact)
    }
}
