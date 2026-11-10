package com.darshan.splitsmart.ui.review

import androidx.compose.runtime.Immutable
import com.darshan.splitsmart.core.Money
import com.darshan.splitsmart.domain.model.ExtractionSource
import com.darshan.splitsmart.domain.model.LlmFailure
import com.darshan.splitsmart.ui.designsystem.motion.MotionDurations

/**
 * Rows that just arrived from a reading (the on-device parser's draft, the AI's answer) and should type themselves in, in this
 * order. [id] grows with every reading, so the screen plays each reveal once and never replays it after a rotation.
 */
@Immutable
data class ItemReveal(val id: Long, val keys: List<String>)

/** The line-by-line reveal, as pure timing and diffing. */
object ItemRevealLogic {
    /** Rows after this many resolve together with the last one, so a 200-line receipt is not a queue. */
    const val MAX_STAGGERED = 8

    private val step: Long get() = MotionDurations.Micro.toLong()
    private val type: Long get() = MotionDurations.SmallSlow.toLong()

    /**
     * Keys of [next] rows that [previous] did not already show: compared on what the user sees (name without case or outer
     * spaces, quantity, amount in paise), one match per copy, so a duplicated line still counts once per copy.
     */
    fun changedKeys(previous: List<ItemRow>, next: List<ItemRow>): List<String> {
        val pool = previous.groupingBy { it.signature() }.eachCount().toMutableMap()
        return next.mapNotNull { row ->
            val signature = row.signature()
            val left = pool[signature] ?: 0
            if (left > 0) {
                pool[signature] = left - 1
                null
            } else {
                row.key
            }
        }
    }

    /** How far row number [order] of a reveal has typed in, [elapsedMs] after the reveal started: 0 still a skeleton, 1 done. */
    fun progress(order: Int, elapsedMs: Long): Float {
        val start = order.coerceIn(0, MAX_STAGGERED - 1) * step
        return ((elapsedMs - start).toFloat() / type).coerceIn(0f, 1f)
    }

    /** How long a reveal of [count] rows takes from start to the last row done. */
    fun durationMs(count: Int): Long = if (count <= 0) 0L else (count - 1).coerceAtMost(MAX_STAGGERED - 1) * step + type

    private fun ItemRow.signature(): Triple<String, String, String> =
        Triple(name.trim().lowercase(), qty.trim(), Money.parseInrToPaise(amount)?.toString() ?: amount.trim())
}

/** How items + tax + tip compare with the printed total. */
sealed interface Mismatch {
    /** No total to compare with yet. */
    data object Unknown : Mismatch
    data object AddsUp : Mismatch

    /** The lines come to more than the printed total (a discount, a misread line). */
    data class Over(val paise: Long) : Mismatch

    /** The lines come to less (a service charge or a line the reading missed). */
    data class Under(val paise: Long) : Mismatch
}

/** The tone of the card that says who read the bill. */
enum class SourceTone {
    /** The AI read it, the AI is still refining, or the build has no key: normal states on a plain card. */
    Plain,

    /** The AI was tried and did not answer in a way trying again can fix (offline, timeout, busy, a bad answer). */
    Warning,

    /** A real failure trying again will not fix: the key was rejected, or the server's certificate did not match the pins. */
    Danger,
}

object ReviewLogic {
    /**
     * Who read the bill decides the card's tone. A build without a Gemini key is how SplitSmart ships to anyone who builds it
     * themselves, so it reads like any other reading, not like an error; the danger tone is kept for real failures.
     */
    fun sourceTone(source: ExtractionSource, refining: Boolean, failure: LlmFailure?): SourceTone = when {
        refining || source != ExtractionSource.REGEX -> SourceTone.Plain
        failure == LlmFailure.KEY_MISSING -> SourceTone.Plain
        failure == LlmFailure.UNAUTHORIZED || failure == LlmFailure.INSECURE -> SourceTone.Danger
        else -> SourceTone.Warning
    }

    fun mismatch(mismatchPaise: Long?): Mismatch = when {
        mismatchPaise == null -> Mismatch.Unknown
        mismatchPaise == 0L -> Mismatch.AddsUp
        mismatchPaise > 0L -> Mismatch.Over(mismatchPaise)
        else -> Mismatch.Under(-mismatchPaise)
    }
}
