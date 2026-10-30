package com.darshan.splitsmart.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

class BackoffPolicyTest {
    private val fixed = object : Random() { override fun nextBits(bitCount: Int): Int = 0; override fun nextLong(from: Long, until: Long): Long = until - 1 }

    @Test
    fun `grows exponentially and is capped`() {
        val policy = BackoffPolicy(initialDelayMs = 100, maxDelayMs = 1_000, multiplier = 2.0, maxAttempts = 6, fullJitter = false)
        assertThat((0 until 6).map { policy.delayMs(it, fixed) }).containsExactly(100L, 200L, 400L, 800L, 1_000L, 1_000L).inOrder()
    }

    @Test
    fun `full jitter stays within zero and the exponential ceiling`() {
        val policy = BackoffPolicy(initialDelayMs = 100, maxDelayMs = 10_000, multiplier = 2.0, maxAttempts = 5)
        val random = Random(42)
        repeat(1_000) {
            val d = policy.delayMs(3, random)
            assertThat(d).isAtLeast(0L)
            assertThat(d).isAtMost(800L)
        }
        assertThat((0 until 50).map { policy.delayMs(3, random) }.toSet().size).isGreaterThan(10)
    }

    @Test
    fun `retry-after wins over the computed delay but is capped`() {
        val policy = BackoffPolicy(initialDelayMs = 100, maxDelayMs = 5_000, multiplier = 2.0, maxAttempts = 3)
        assertThat(policy.delayMs(0, fixed, retryAfterMs = 3_000)).isEqualTo(3_000)
        assertThat(policy.delayMs(0, fixed, retryAfterMs = 999_999)).isEqualTo(5_000)
        assertThat(policy.delayMs(0, fixed, retryAfterMs = -5)).isEqualTo(100)
    }

    @Test
    fun `shouldRetry respects maxAttempts`() {
        val policy = BackoffPolicy(initialDelayMs = 1, maxDelayMs = 1, multiplier = 1.0, maxAttempts = 3)
        assertThat(policy.canRetry(attemptsMade = 1)).isTrue()
        assertThat(policy.canRetry(attemptsMade = 2)).isTrue()
        assertThat(policy.canRetry(attemptsMade = 3)).isFalse()
        assertThat(BackoffPolicy.NONE.canRetry(1)).isFalse()
    }
}
