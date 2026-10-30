package com.darshan.splitsmart.domain.split

import java.math.BigInteger

/**
 * Hamilton / largest-remainder apportionment: each weight gets `floor(total × w / W)`, then the leftover paise go one
 * each to the largest fractional remainders (ties broken by position). The parts always sum exactly to [total] and
 * never deviate from the ideal share by a full paisa. All-zero weights mean "equal".
 */
object LargestRemainder {
    fun distribute(total: Long, weights: List<Long>): List<Long> {
        require(total >= 0) { "total must not be negative: $total" }
        require(weights.all { it >= 0 }) { "weights must not be negative" }
        if (weights.isEmpty()) return emptyList()
        val effective = if (weights.all { it == 0L }) List(weights.size) { 1L } else weights
        val weightSum = effective.fold(BigInteger.ZERO) { acc, w -> acc + BigInteger.valueOf(w) }
        val bigTotal = BigInteger.valueOf(total)
        val parts = LongArray(effective.size)
        val remainders = Array(effective.size) { BigInteger.ZERO }
        var assigned = 0L
        for (i in effective.indices) {
            val product = bigTotal * BigInteger.valueOf(effective[i])
            val (quotient, remainder) = product.divideAndRemainder(weightSum)
            parts[i] = quotient.toLong()
            remainders[i] = remainder
            assigned += parts[i]
        }
        var leftover = (total - assigned).toInt()
        if (leftover > 0) {
            val order = effective.indices.sortedWith(compareByDescending<Int> { remainders[it] }.thenBy { it })
            for (i in order) {
                if (leftover == 0) break
                if (remainders[i].signum() == 0) break
                parts[i]++
                leftover--
            }
        }
        return parts.toList()
    }
}
