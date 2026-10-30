package com.darshan.splitsmart.domain.split

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

class LargestRemainderTest {
    @Test
    fun `equal weights split 100 paise three ways as 34 33 33`() {
        assertThat(LargestRemainder.distribute(100, listOf(1, 1, 1))).containsExactly(34L, 33L, 33L).inOrder()
    }

    @Test
    fun `remainder goes to the largest fractional parts`() {
        // 1000 over 3:2:2 → ideal 428.57, 285.71, 285.71 → the two largest fractional parts (.71, .71) get the 2 leftover paise
        assertThat(LargestRemainder.distribute(1_000, listOf(3, 2, 2))).containsExactly(428L, 286L, 286L).inOrder()
        // 100 over 5:3:1 → 55.5, 33.3, 11.1 → 56, 33, 11
        assertThat(LargestRemainder.distribute(100, listOf(5, 3, 1))).containsExactly(56L, 33L, 11L).inOrder()
    }

    @Test
    fun `all zero weights fall back to an equal split`() {
        assertThat(LargestRemainder.distribute(10, listOf(0, 0, 0, 0))).containsExactly(3L, 3L, 2L, 2L).inOrder()
    }

    @Test
    fun `zero weight members receive nothing when others have weight`() {
        assertThat(LargestRemainder.distribute(100, listOf(0, 1, 0))).containsExactly(0L, 100L, 0L).inOrder()
    }

    @Test
    fun `edge cases`() {
        assertThat(LargestRemainder.distribute(0, listOf(1, 2))).containsExactly(0L, 0L)
        assertThat(LargestRemainder.distribute(7, listOf(5))).containsExactly(7L)
        assertThat(LargestRemainder.distribute(5, emptyList())).isEmpty()
        assertThat(runCatching { LargestRemainder.distribute(5, listOf(-1, 2)) }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(runCatching { LargestRemainder.distribute(-5, listOf(1, 2)) }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `property - always sums exactly and never deviates more than one paisa from the ideal`() {
        val random = Random(2026)
        repeat(2_000) {
            val n = random.nextInt(1, 40)
            val weights = List(n) { if (random.nextInt(10) == 0) 0L else random.nextLong(0, 100_000) }
            val total = random.nextLong(0, 10_000_000)
            val parts = LargestRemainder.distribute(total, weights)
            assertThat(parts.sum()).isEqualTo(total)
            val weightSum = weights.sum()
            parts.forEachIndexed { i, p ->
                assertThat(p).isAtLeast(0L)
                val ideal = if (weightSum == 0L) total.toDouble() / n else total.toDouble() * weights[i] / weightSum
                assertThat(Math.abs(p - ideal)).isLessThan(1.0 + 1e-6)
            }
        }
    }

    @Test
    fun `handles huge totals without overflow`() {
        val parts = LargestRemainder.distribute(1_000_000_000L, List(1_000) { 999_999_999L })
        assertThat(parts.sum()).isEqualTo(1_000_000_000L)
    }
}
