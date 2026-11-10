package com.darshan.splitsmart.domain.split

import com.darshan.splitsmart.domain.model.BillSplit
import com.darshan.splitsmart.domain.model.Debt
import com.darshan.splitsmart.domain.model.SplitConfig
import com.darshan.splitsmart.domain.model.SplitMode
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testSettlement
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

class DebtSimplifierTest {
    @Test
    fun `two people - one transfer`() {
        assertThat(DebtSimplifier.simplify(mapOf("a" to 500L, "b" to -500L))).containsExactly(Debt("b", "a", 500))
    }

    @Test
    fun `chains collapse - a owes b and b owes c becomes a pays c`() {
        // a paid nothing and owes 1000; b is net zero; c is owed 1000
        assertThat(DebtSimplifier.simplify(mapOf("a" to -1_000L, "b" to 0L, "c" to 1_000L))).containsExactly(Debt("a", "c", 1_000))
    }

    @Test
    fun `greedy netting uses at most n minus 1 transfers and settles everyone`() {
        val balances = mapOf("a" to 3_000L, "b" to 2_000L, "c" to -1_500L, "d" to -1_500L, "e" to -2_000L)
        val debts = DebtSimplifier.simplify(balances)
        assertThat(debts.size).isAtMost(4)
        assertThat(settle(balances, debts).values.all { it == 0L }).isTrue()
        debts.forEach { assertThat(it.amountPaise).isGreaterThan(0L) }
    }

    @Test
    fun `largest debtor pays largest creditor first - deterministic order`() {
        val debts = DebtSimplifier.simplify(mapOf("asha" to 7_000L, "ravi" to -4_000L, "meera" to 1_000L, "kabir" to -4_000L))
        assertThat(debts.first()).isEqualTo(Debt("kabir", "asha", 4_000)) // ties broken alphabetically
        assertThat(debts).containsExactly(Debt("kabir", "asha", 4_000), Debt("ravi", "asha", 3_000), Debt("ravi", "meera", 1_000)).inOrder()
    }

    @Test
    fun `zero and empty balances produce no debts, unbalanced input is rejected`() {
        assertThat(DebtSimplifier.simplify(emptyMap())).isEmpty()
        assertThat(DebtSimplifier.simplify(mapOf("a" to 0L, "b" to 0L))).isEmpty()
        assertThat(runCatching { DebtSimplifier.simplify(mapOf("a" to 5L, "b" to -4L)) }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `balances come from split bills with a known payer, others are skipped`() {
        val config = SplitConfig(SplitMode.EQUAL, listOf("a", "b"))
        val split = SplitCalculator.split(testBill("b1", totalPaise = 1_000, items = emptyList()), config)
        val paid = testBill("b1", totalPaise = 1_000, items = emptyList(), paidByMemberId = "a").copy(split = BillSplit(config, split.shares))
        val noPayer = testBill("b2", totalPaise = 1_000, items = emptyList()).copy(split = BillSplit(config, split.shares))
        val notSplit = testBill("b3", totalPaise = 1_000, items = emptyList(), paidByMemberId = "b")
        val balances = DebtSimplifier.balances(listOf(paid, noPayer, notSplit))
        assertThat(balances).containsExactly("a", 500L, "b", -500L)
        assertThat(DebtSimplifier.simplify(balances)).containsExactly(Debt("b", "a", 500))
        assertThat(DebtSimplifier.unsettledCount(listOf(paid, noPayer, notSplit))).isEqualTo(2)
    }

    @Test
    fun `a settled payment moves balances like a bill in reverse, and paying in full clears the debt`() {
        val config = SplitConfig(SplitMode.EQUAL, listOf("a", "b"))
        val bill = testBill("b1", totalPaise = 1_000, items = emptyList(), paidByMemberId = "a")
        val paid = bill.copy(split = BillSplit(config, SplitCalculator.split(bill, config).shares))
        val part = DebtSimplifier.balances(listOf(paid), listOf(testSettlement(from = "b", to = "a", amountPaise = 200)))
        assertThat(part).containsExactly("a", 300L, "b", -300L)
        assertThat(DebtSimplifier.simplify(part)).containsExactly(Debt("b", "a", 300))
        val full = DebtSimplifier.balances(listOf(paid), listOf(testSettlement("s1", amountPaise = 200), testSettlement("s2", amountPaise = 300)))
        assertThat(DebtSimplifier.simplify(full)).isEmpty()
        // Paying too much turns the debt around rather than hiding it.
        val over = DebtSimplifier.balances(listOf(paid), listOf(testSettlement(amountPaise = 800)))
        assertThat(DebtSimplifier.simplify(over)).containsExactly(Debt("a", "b", 300))
    }

    @Test
    fun `a payment whose member left the group no longer counts, and settlements alone still balance`() {
        val gone = testSettlement(from = null, to = "a", amountPaise = 500)
        assertThat(gone.counts).isFalse()
        assertThat(DebtSimplifier.balances(emptyList(), listOf(gone))).isEmpty()
        val only = DebtSimplifier.balances(emptyList(), listOf(testSettlement(from = "b", to = "a", amountPaise = 500)))
        assertThat(only.values.sum()).isEqualTo(0L)
        assertThat(DebtSimplifier.simplify(only)).containsExactly(Debt("a", "b", 500))
    }

    @Test
    fun `property - random balances always settle with at most n-1 transfers`() {
        val random = Random(11)
        repeat(1_000) {
            val n = random.nextInt(1, 30)
            val raw = List(n) { random.nextLong(-100_000, 100_000) }
            val fix = raw.sum()
            val balances = raw.mapIndexed { i, v -> "m$i" to if (i == 0) v - fix else v }.toMap()
            val debts = DebtSimplifier.simplify(balances)
            assertThat(debts.size).isAtMost((n - 1).coerceAtLeast(0))
            assertThat(settle(balances, debts).values.all { it == 0L }).isTrue()
        }
    }

    private fun settle(balances: Map<String, Long>, debts: List<Debt>): Map<String, Long> {
        val m = balances.toMutableMap()
        debts.forEach { d -> m[d.fromMemberId] = m[d.fromMemberId]!! + d.amountPaise; m[d.toMemberId] = m[d.toMemberId]!! - d.amountPaise }
        return m
    }
}
