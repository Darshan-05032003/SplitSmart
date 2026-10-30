package com.darshan.splitsmart.core

import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Exponential backoff with full jitter (AWS style): the delay before retry `n` is a random value in
 * `[0, min(max, initial × multiplier^n)]`, so a thousand clients throttled at the same instant do not retry at the
 * same instant. A server `Retry-After` wins over the computed delay but is still capped at [maxDelayMs].
 */
data class BackoffPolicy(
    val initialDelayMs: Long,
    val maxDelayMs: Long,
    val multiplier: Double = 2.0,
    /** Total attempts including the first one. */
    val maxAttempts: Int,
    val fullJitter: Boolean = true,
) {
    init {
        require(initialDelayMs >= 0 && maxDelayMs >= 0 && multiplier >= 1.0 && maxAttempts >= 1)
    }

    fun canRetry(attemptsMade: Int): Boolean = attemptsMade < maxAttempts

    fun delayMs(retryIndex: Int, random: Random, retryAfterMs: Long? = null): Long {
        if (retryAfterMs != null && retryAfterMs > 0) return min(retryAfterMs, maxDelayMs)
        val ceiling = min(maxDelayMs.toDouble(), initialDelayMs * multiplier.pow(retryIndex)).toLong()
        if (!fullJitter || ceiling <= 0) return ceiling
        return random.nextLong(0, ceiling + 1)
    }

    companion object {
        /** Gemini: 0.5 s → 8 s, four attempts in total (one call plus three retries). */
        val LLM = BackoffPolicy(initialDelayMs = 500, maxDelayMs = 8_000, multiplier = 2.0, maxAttempts = 4)
        val NONE = BackoffPolicy(initialDelayMs = 0, maxDelayMs = 0, multiplier = 1.0, maxAttempts = 1)
    }
}
