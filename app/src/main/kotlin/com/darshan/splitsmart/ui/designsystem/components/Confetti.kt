package com.darshan.splitsmart.ui.designsystem.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.darshan.splitsmart.ui.designsystem.motion.LocalMotion
import com.darshan.splitsmart.ui.designsystem.motion.MotionDurations
import com.darshan.splitsmart.ui.theme.SplitSmartTheme

/** The festival default: marigold, sparkle, party popper. */
val CelebrationEmoji = listOf("🌼", "✨", "🎉")

/** Size of a paper piece and of an emoji piece, as fractions of the burst's unit. */
private const val PIECE = 0.022f
private const val EMOJI_PIECE = 0.06f

/** Fraction of the smaller side that one physics unit spans. */
private const val UNIT = 0.5f

/**
 * A one-shot confetti burst of paper pieces in the palette with a few emoji mixed in. It plays once each time [playKey] changes to
 * a new non-null value, from [origin] (fractions of the box), and never blocks touches: it draws over content with no pointer
 * input. **Only for real achievements** (a debt settled, a group all square), so it stays special. Decorative: nothing plays
 * under reduced motion, so the moment must also be told in words. Seeded by [playKey], so a screenshot is repeatable.
 *
 * Per frame it only moves cached pieces: physics write into one reused array and emoji are measured once.
 */
@Composable
fun ConfettiBurst(
    playKey: Any?,
    modifier: Modifier = Modifier,
    emojis: List<String> = CelebrationEmoji,
    particleCount: Int = DEFAULT_PARTICLES,
    origin: Offset = DEFAULT_ORIGIN,
) {
    val motion = LocalMotion.current
    val palette = SplitSmartTheme.colors.confetti
    val measurer = rememberTextMeasurer()
    val emojiStyle = TextStyle(fontSize = SplitSmartTheme.type.title.fontSize)
    val emojiLayouts = remember(emojis, measurer) { emojis.map { measurer.measure(it, emojiStyle) } }
    val particles = remember(playKey, emojis.size, palette.size) {
        ConfettiPhysics.burst(playKey.hashCode().toLong(), particleCount, palette.size, emojis.size)
    }
    var elapsed by remember(playKey) { mutableFloatStateOf(-1f) }
    val durationSec = MotionDurations.Confetti / MILLIS
    val playing = playKey != null && motion.decorativeEnabled
    LaunchedEffect(playKey, playing) {
        if (!playing) return@LaunchedEffect
        val start = withFrameMillis { it }
        do {
            val now = withFrameMillis { it }
            elapsed = (now - start) / MILLIS
        } while (elapsed < durationSec)
        elapsed = -1f
    }
    val scratch = remember { FloatArray(4) }
    Spacer(
        modifier
            .clearAndSetSemantics { }
            .drawWithCache {
                val unit = size.minDimension * UNIT
                val piece = unit * PIECE
                val pieceSize = Size(piece * 2f, piece)
                val corner = CornerRadius(piece / 3f)
                val start = Offset(size.width * origin.x, size.height * origin.y)
                onDrawBehind {
                    val t = elapsed
                    if (!playing || t < 0f) return@onDrawBehind
                    for (particle in particles) {
                        ConfettiPhysics.place(particle, t, durationSec, scratch)
                        val alpha = scratch[3]
                        if (alpha <= 0f) continue
                        val x = start.x + scratch[0] * unit
                        val y = start.y + scratch[1] * unit
                        if (particle.emojiIndex >= 0) {
                            val layout = emojiLayouts[particle.emojiIndex]
                            val scale = particle.size * unit * EMOJI_PIECE / layout.size.height.coerceAtLeast(1)
                            translate(x - layout.size.width / 2f, y - layout.size.height / 2f) {
                                rotate(scratch[2] / 4f, pivot = Offset(layout.size.width / 2f, layout.size.height / 2f)) {
                                    scale(scale, pivot = Offset(layout.size.width / 2f, layout.size.height / 2f)) {
                                        drawText(layout, alpha = alpha)
                                    }
                                }
                            }
                        } else {
                            val color = palette[particle.colorIndex]
                            rotate(scratch[2], pivot = Offset(x, y)) {
                                when (particle.shape) {
                                    0 -> drawRoundRect(color, Offset(x - piece, y - piece / 2f), pieceSize, corner, alpha = alpha)
                                    1 -> drawCircle(color, piece * 0.7f * particle.size, Offset(x, y), alpha = alpha)
                                    else -> drawRoundRect(color, Offset(x - piece / 2f, y - piece), Size(piece, piece * 2f), corner, alpha = alpha)
                                }
                            }
                        }
                    }
                }
            },
    )
}

private const val DEFAULT_PARTICLES = 48
private const val MILLIS = 1000f
private val DEFAULT_ORIGIN = Offset(0.5f, 0.45f)
