package com.darshan.splitsmart.ui.designsystem.components

import com.darshan.splitsmart.ui.designsystem.motion.MotionDurations
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * Accepts one tap per debounce window, so a double or triple tap on Save or Settle runs the action once. A clock that jumps
 * backwards (a test, a time change) re-opens the window rather than locking the button.
 */
class ClickGuard(private val windowMs: Long = MotionDurations.TapDebounce.toLong()) {
    private var lastAccepted: Long? = null

    fun tryAccept(nowMs: Long): Boolean {
        val last = lastAccepted
        if (last != null && nowMs >= last && nowMs - last < windowMs) return false
        lastAccepted = nowMs
        return true
    }
}

/**
 * Text from outside the app (OCR, the AI, other people's names) made safe to draw: bidi overrides and isolates, zero-width
 * marks and control characters are removed so a name can't reorder the text around it; whitespace collapses; very long text is
 * cut on a code-point boundary with an ellipsis. Letters of any script and emoji are kept.
 */
object DisplayText {
    const val MAX_CHARS = 120
    private const val ELLIPSIS = '…'

    private fun isUnsafe(codePoint: Int): Boolean =
        Character.getType(codePoint) == Character.CONTROL.toInt() && !Character.isWhitespace(codePoint) ||
            codePoint in 0x202A..0x202E || codePoint in 0x2066..0x2069 || codePoint == 0x200E || codePoint == 0x200F ||
            codePoint == 0x061C || codePoint == 0xFEFF || codePoint == 0x200B

    fun safe(raw: String): String {
        val cleaned = StringBuilder(raw.length)
        var lastWasSpace = true
        var index = 0
        while (index < raw.length) {
            val cp = raw.codePointAt(index)
            index += Character.charCount(cp)
            when {
                isUnsafe(cp) -> Unit
                Character.isWhitespace(cp) || Character.isISOControl(cp) -> {
                    if (!lastWasSpace) cleaned.append(' ')
                    lastWasSpace = true
                }
                else -> {
                    cleaned.appendCodePoint(cp)
                    lastWasSpace = false
                }
            }
        }
        val text = cleaned.toString().trimEnd()
        if (text.codePointCount(0, text.length) <= MAX_CHARS) return text
        val cut = text.offsetByCodePoints(0, MAX_CHARS)
        return text.substring(0, cut).trimEnd() + ELLIPSIS
    }
}

enum class AmountSign {
    /** Only money going out shows a sign. */
    NegativeOnly,

    /** `+` in, `−` out, nothing for zero. */
    Always,

    /** No sign: the caller shows direction another way (colour, a word). */
    None,
}

enum class AmountDecimals { Always, WhenNonZero }
enum class RollDirection { Up, Down }

/**
 * Money on screen. Amounts are Long paise, written with ₹ and the Indian grouping (12,34,567.89). Displays clamp to
 * ₹99,99,99,999.99 so no layout ever has to fit more. The minus is U+2212, drawn in the UI font.
 */
object AmountFormat {
    const val MAX_DISPLAY_PAISE = 99_99_99_999_99L
    const val MINUS = '−'

    private fun clamp(paise: Long): Long = paise.coerceIn(-MAX_DISPLAY_PAISE, MAX_DISPLAY_PAISE)

    fun signText(paise: Long, sign: AmountSign): String {
        val value = clamp(paise)
        return when {
            value < 0 && sign != AmountSign.None -> MINUS.toString()
            value > 0 && sign == AmountSign.Always -> "+"
            else -> ""
        }
    }

    /** The amount without its sign: `₹12,34,567.89`. */
    fun unsigned(paise: Long, decimals: AmountDecimals = AmountDecimals.Always): String {
        val abs = abs(clamp(paise))
        val rupees = abs / 100
        val cents = (abs % 100).toInt()
        val grouped = groupIndian(rupees.toString())
        val showDecimals = decimals == AmountDecimals.Always || cents != 0
        return if (showDecimals) "₹$grouped.${cents.toString().padStart(2, '0')}" else "₹$grouped"
    }

    fun format(paise: Long, sign: AmountSign = AmountSign.NegativeOnly, decimals: AmountDecimals = AmountDecimals.Always): String =
        signText(paise, sign) + unsigned(paise, decimals)

    /** For TalkBack: screen readers often skip a symbol minus, so it is spelled out. */
    fun spoken(paise: Long, decimals: AmountDecimals = AmountDecimals.Always): String =
        (if (clamp(paise) < 0) "minus " else "") + unsigned(paise, decimals)

    internal fun groupIndian(digits: String): String {
        if (digits.length <= 3) return digits
        val head = digits.dropLast(3)
        val firstGroup = head.length % 2
        val parts = buildList {
            if (firstGroup == 1) add(head.substring(0, 1))
            var i = firstGroup
            while (i < head.length) {
                add(head.substring(i, i + 2))
                i += 2
            }
        }
        return parts.joinToString(",") + "," + digits.takeLast(3)
    }
}

/** One glyph of a rolling amount. [key] follows place value, so a column keeps its identity as the amount grows. */
sealed interface AmountGlyph {
    val key: String
    val char: Char

    data class Digit(val value: Int, val place: Int) : AmountGlyph {
        override val key get() = "d$place"
        override val char get() = '0' + value
    }

    data class Separator(override val char: Char, val place: Int) : AmountGlyph {
        override val key get() = "s$place"
    }

    data object Currency : AmountGlyph {
        override val key = "currency"
        override val char = '₹'
    }

    /** Drawn in the UI font. */
    data class Sign(override val char: Char) : AmountGlyph {
        override val key = "sign"
    }
}

/** Pure rules of the rolling amount: glyphs, roll direction, and how much to shrink a huge amount to fit. */
object RollingAmountLogic {
    /** Font scale moves in these steps, so text doesn't jitter as amounts of different widths come and go. */
    const val SCALE_STEP = 0.05f

    fun glyphs(paise: Long, sign: AmountSign, decimals: AmountDecimals): List<AmountGlyph> {
        val clamped = paise.coerceIn(-AmountFormat.MAX_DISPLAY_PAISE, AmountFormat.MAX_DISPLAY_PAISE)
        val abs = abs(clamped)
        val rupees = (abs / 100).toString()
        val cents = (abs % 100).toInt()
        return buildList {
            val signText = AmountFormat.signText(clamped, sign)
            if (signText.isNotEmpty()) add(AmountGlyph.Sign(signText.single()))
            add(AmountGlyph.Currency)
            rupees.forEachIndexed { index, digit ->
                val place = rupees.length - 1 - index
                add(AmountGlyph.Digit(digit - '0', place))
                if (place >= 3 && (place - 3) % 2 == 0) add(AmountGlyph.Separator(',', place))
            }
            if (decimals == AmountDecimals.Always || cents != 0) {
                add(AmountGlyph.Separator('.', -1))
                add(AmountGlyph.Digit(cents / 10, -1))
                add(AmountGlyph.Digit(cents % 10, -2))
            }
        }
    }

    fun direction(from: Long, to: Long): RollDirection = if (to >= from) RollDirection.Up else RollDirection.Down

    /** Font scale that fits [naturalWidth] into [available], in stable steps and never below [floorScale]. */
    fun fitScale(naturalWidth: Float, available: Float, floorScale: Float): Float {
        if (naturalWidth <= available) return 1f
        if (available <= 0f) return floorScale
        val steps = floor(available / naturalWidth / SCALE_STEP + 1e-4f).toInt()
        return (steps * SCALE_STEP).coerceIn(floorScale, 1f)
    }
}

/** Cart-style counters: small, capped, and they know which way to slide. */
object CounterLogic {
    const val MAX_SHOWN = 99

    fun label(count: Int): String = when {
        count <= 0 -> "0"
        count > MAX_SHOWN -> "$MAX_SHOWN+"
        else -> count.toString()
    }

    fun direction(from: Int, to: Int): RollDirection = if (to >= from) RollDirection.Up else RollDirection.Down
}

data class StackLayout(val visible: Int, val overflow: Int)

/** Pure rules of avatars and stacks: initials, a stable colour per person, what a stack shows, and where each face sits. */
object AvatarLogic {
    const val MAX_OVERFLOW_SHOWN = 999
    private const val UNNAMED = "Someone"

    fun initials(name: String): String {
        val safe = DisplayText.safe(name)
        val letters = safe.split(' ')
            .mapNotNull { word ->
                var i = 0
                var found: String? = null
                while (i < word.length && found == null) {
                    val cp = word.codePointAt(i)
                    if (Character.isLetterOrDigit(cp)) found = String(Character.toChars(cp)).uppercase(Locale.ROOT)
                    i += Character.charCount(cp)
                }
                found
            }
            .take(2)
        if (letters.isNotEmpty()) return letters.joinToString("")
        if (safe.isEmpty()) return "?"
        val graphemes = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(safe) }
        val end = graphemes.next()
        return if (end == BreakIterator.DONE) "?" else safe.substring(0, end)
    }

    /** FNV-1a over the normalised name: the same person gets the same colour on every screen and every run. */
    fun toneIndex(name: String, tones: Int): Int {
        if (tones <= 0) return 0
        var hash = 0x811C9DC5.toInt()
        for (c in name.trim().lowercase(Locale.ROOT)) {
            hash = hash xor c.code
            hash *= 0x01000193
        }
        // Murmur3's finaliser spreads short, similar names (Asha, Isha) across the palette.
        hash = hash xor (hash ushr 16)
        hash *= 0x85EBCA6B.toInt()
        hash = hash xor (hash ushr 13)
        hash *= 0xC2B2AE35.toInt()
        hash = hash xor (hash ushr 16)
        return Math.floorMod(hash, tones)
    }

    /** At most [maxVisible] faces plus "+N"; when only one would hide, its face takes the place of a "+1". */
    fun layout(count: Int, maxVisible: Int): StackLayout {
        val max = maxVisible.coerceAtLeast(0)
        val visible = if (count == max + 1) count else count.coerceAtLeast(0).coerceAtMost(max)
        return StackLayout(visible = visible, overflow = (count - visible).coerceAtLeast(0))
    }

    fun overflowLabel(overflow: Int): String = if (overflow > MAX_OVERFLOW_SHOWN) "$MAX_OVERFLOW_SHOWN+" else "+$overflow"

    /** One sentence for TalkBack: a few names and a count, never 200 names read aloud. */
    fun description(names: List<String>, maxNamed: Int = 4): String {
        if (names.isEmpty()) return "No members yet"
        val shown = names.take(maxNamed).map { DisplayText.safe(it).ifEmpty { UNNAMED } }
        val rest = names.size - shown.size
        return when {
            rest > 0 -> shown.joinToString(", ") + " and $rest more"
            shown.size == 1 -> shown.single()
            else -> shown.dropLast(1).joinToString(", ") + " and " + shown.last()
        }
    }

    private fun step(progress: Float, sizePx: Float, overlapFraction: Float, gapPx: Float): Float {
        val collapsed = sizePx * (1f - overlapFraction)
        val expanded = sizePx + gapPx
        return collapsed + (expanded - collapsed) * progress
    }

    /** Left edge of face [index]: overlapping at progress 0, spread with a gap at 1. */
    fun offsetX(index: Int, progress: Float, sizePx: Float, overlapFraction: Float, gapPx: Float): Float =
        index * step(progress, sizePx, overlapFraction, gapPx)

    fun width(count: Int, progress: Float, sizePx: Float, overlapFraction: Float, gapPx: Float): Float =
        if (count <= 0) 0f else offsetX(count - 1, progress, sizePx, overlapFraction, gapPx) + sizePx

    /** A little jump while fanning: 0 at rest, 1 halfway. */
    fun hop(progress: Float): Float = if (progress <= 0f || progress >= 1f) 0f else sin(progress * PI).toFloat()
}

enum class SheetTarget { Expanded, Dismissed }

/** Pure sheet physics. Offset 0 is fully open; positive is dragged down. */
object SheetPhysics {
    /** Above this downward speed (px/s) a release dismisses from anywhere. */
    const val FLING_VELOCITY = 1400f

    /** Fraction of the height past which a slow release dismisses. */
    const val DISMISS_FRACTION = 0.35f

    /** Upward drags past the top move this fraction of the finger. */
    const val OVERDRAG_RESISTANCE = 0.2f

    fun settle(offset: Float, height: Float, velocity: Float, dismissible: Boolean): SheetTarget = when {
        !dismissible -> SheetTarget.Expanded
        velocity >= FLING_VELOCITY -> SheetTarget.Dismissed
        velocity <= -FLING_VELOCITY -> SheetTarget.Expanded
        offset > height * DISMISS_FRACTION -> SheetTarget.Dismissed
        else -> SheetTarget.Expanded
    }

    fun applyDrag(offset: Float, delta: Float): Float {
        if (delta >= 0f) return offset + delta
        if (offset <= 0f) return offset + delta * OVERDRAG_RESISTANCE
        val next = offset + delta
        return if (next >= 0f) next else next * OVERDRAG_RESISTANCE
    }

    fun scrimAlpha(offset: Float, height: Float): Float = if (height <= 0f) 0f else (1f - offset / height).coerceIn(0f, 1f)
}

data class BlobShape(val centerX: Float, val width: Float, val height: Float)

/**
 * The bottom bar's selection blob. Its position is a float tab index on a Bouncy spring; while it travels it stretches along the
 * direction of travel and flattens, like a drop of honey, then settles back into its round-rect at rest.
 */
object BlobLogic {
    const val MAX_STRETCH = 0.6f
    const val STRETCH_PER_ITEM_PER_SECOND = 0.12f
    const val SQUASH_RATIO = 0.35f

    fun shape(position: Float, velocity: Float, itemWidth: Float, baseWidth: Float, baseHeight: Float): BlobShape {
        val stretch = (abs(velocity) * STRETCH_PER_ITEM_PER_SECOND).coerceAtMost(MAX_STRETCH)
        return BlobShape(
            centerX = (position + 0.5f) * itemWidth,
            width = baseWidth * (1f + stretch),
            height = baseHeight * (1f - stretch * SQUASH_RATIO),
        )
    }
}

/**
 * One confetti piece. Velocities are in "units" per second, where a unit is the smaller side of the canvas, so a burst looks the
 * same on any screen.
 */
data class ConfettiParticle(
    val vx: Float,
    val vy: Float,
    val spin: Float,
    val size: Float,
    val colorIndex: Int,
    val shape: Int,
    val emojiIndex: Int,
)

/** Pure, seeded confetti physics: deterministic for screenshot tests, allocation-free per frame. */
object ConfettiPhysics {
    const val MAX_PARTICLES = 80
    const val GRAVITY = 2.6f
    const val DRAG = 0.9f
    const val FADE_FROM = 0.75f
    const val SHAPES = 3
    private const val EMOJI_EVERY = 6

    fun burst(seed: Long, count: Int, colors: Int, emojis: Int): List<ConfettiParticle> {
        val random = Random(seed)
        return List(count.coerceIn(0, MAX_PARTICLES)) { index ->
            val angle = (random.nextFloat() - 0.5f) * PI.toFloat() * 0.9f
            val speed = 1.0f + random.nextFloat() * 1.3f
            ConfettiParticle(
                vx = sin(angle) * speed,
                vy = -(0.8f + random.nextFloat() * 1.7f),
                spin = (random.nextFloat() - 0.5f) * 1_080f,
                size = 0.6f + random.nextFloat() * 0.6f,
                colorIndex = if (colors > 0) random.nextInt(colors) else 0,
                shape = random.nextInt(SHAPES),
                emojiIndex = if (emojis > 0 && index % EMOJI_EVERY == 0) (index / EMOJI_EVERY) % emojis else -1,
            )
        }
    }

    /** Writes x, y (units from the origin), rotation (degrees) and alpha for time [tSec] into [out]. */
    fun place(particle: ConfettiParticle, tSec: Float, durationSec: Float, out: FloatArray) {
        val t = tSec.coerceAtLeast(0f)
        val travel = (1f - exp(-DRAG * t)) / DRAG
        out[0] = particle.vx * travel
        out[1] = particle.vy * travel + 0.5f * GRAVITY * t * t
        out[2] = particle.spin * t
        val fadeStart = durationSec * FADE_FROM
        out[3] = when {
            t <= fadeStart -> 1f
            t >= durationSec -> 0f
            else -> 1f - (t - fadeStart) / (durationSec - fadeStart)
        }
    }
}
