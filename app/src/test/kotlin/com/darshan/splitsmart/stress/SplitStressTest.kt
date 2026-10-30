package com.darshan.splitsmart.stress

import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.BillItem
import com.darshan.splitsmart.domain.model.BillSource
import com.darshan.splitsmart.domain.model.BillSplit
import com.darshan.splitsmart.domain.model.BillStatus
import com.darshan.splitsmart.domain.model.SplitConfig
import com.darshan.splitsmart.domain.model.SplitMode
import com.darshan.splitsmart.domain.model.SplitResult
import com.darshan.splitsmart.domain.split.DebtSimplifier
import com.darshan.splitsmart.domain.split.SplitCalculator
import com.darshan.splitsmart.testutil.FIXED_INSTANT
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/** Run with `./gradlew stressTest`. */
class SplitStressTest {
    private fun usedMb() = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1_048_576

    private fun item(i: Int, random: Random, maxUnit: Long = 50_000): BillItem {
        val qty = random.nextInt(1, 4)
        val unit = random.nextLong(1, maxUnit)
        return BillItem("i$i", "b", "Item $i", qty = qty, unitPaise = unit, totalPaise = unit * qty, sortOrder = i)
    }

    @Test
    fun `1000 people x 10000 items split by item - paise exact`() {
        val random = Random(1)
        val members = List(1_000) { "m$it" }
        val items = List(10_000) { i -> item(i, random) }
        val subtotal = items.sumOf { it.totalPaise }
        val tax = subtotal / 20
        val tip = 12_345L
        val total = subtotal + tax + tip + 7
        val assignments = items.associate { it.id to List(random.nextInt(1, 6)) { members[random.nextInt(members.size)] }.toSet() }
        val config = SplitConfig(SplitMode.BY_ITEM, members, itemAssignments = assignments)
        val before = usedMb()
        lateinit var result: SplitResult
        val ms = measureTimeMillis { result = SplitCalculator.splitAmounts(items, tax, tip, total, config) }
        println("STRESS [split-1000x10000] by-item split of ${items.size} items over ${members.size} members in ${ms}ms, total=$total paise, heap ${before}→${usedMb()} MB")
        assertThat(result.totalPaise).isEqualTo(total)
        assertThat(result.shares.sumOf { it.itemsPaise }).isEqualTo(subtotal)
        assertThat(result.shares.sumOf { it.taxPaise }).isEqualTo(tax)
        assertThat(result.shares.sumOf { it.tipPaise }).isEqualTo(tip)
        assertThat(result.shares).hasSize(1_000)
        assertThat(ms).isLessThan(10_000)

        val equalMs = measureTimeMillis { result = SplitCalculator.splitAmounts(items, tax, tip, total, SplitConfig(SplitMode.EQUAL, members)) }
        assertThat(result.totalPaise).isEqualTo(total)
        val shareMs = measureTimeMillis {
            result = SplitCalculator.splitAmounts(items, tax, tip, total, SplitConfig(SplitMode.BY_SHARE, members, shares = members.associateWith { random.nextInt(1, 10) }))
        }
        assertThat(result.totalPaise).isEqualTo(total)
        println("STRESS [split-1000x10000] equal=${equalMs}ms by-share=${shareMs}ms")
    }

    @Test
    fun `10000 random splits always sum exactly`() {
        val random = Random(3)
        var checked = 0
        val ms = measureTimeMillis {
            repeat(10_000) {
                val members = List(random.nextInt(1, 25)) { "m$it" }
                val items = List(random.nextInt(0, 30)) { i -> item(i, random, maxUnit = 20_000) }
                val subtotal = items.sumOf { it.totalPaise }
                val tax = random.nextLong(0, 3_000)
                val tip = random.nextLong(0, 1_000)
                val total = (subtotal + tax + tip + random.nextLong(-300, 300)).coerceAtLeast(1)
                val config = SplitConfig(
                    SplitMode.entries[random.nextInt(3)], members,
                    itemAssignments = items.associate { it.id to members.filter { random.nextBoolean() }.toSet() },
                    shares = members.associateWith { random.nextInt(1, 7) },
                )
                val r = SplitCalculator.splitAmounts(items, tax, tip, total, config)
                assertThat(r.totalPaise).isEqualTo(total)
                assertThat(r.shares.sumOf { it.itemsPaise }).isEqualTo(subtotal)
                checked++
            }
        }
        println("STRESS [split-random] $checked random splits verified exact in ${ms}ms")
    }

    @Test
    fun `debt simplification over 1000 members and 1000 bills`() {
        val random = Random(5)
        val members = List(1_000) { "m$it" }
        val bills = List(1_000) { i ->
            val participants = List(random.nextInt(2, 12)) { members[random.nextInt(members.size)] }.distinct()
            val total = random.nextLong(10_000, 500_000)
            val config = SplitConfig(SplitMode.EQUAL, participants)
            val bill = Bill("b$i", "g", "Bill $i", total, 0, 0, participants.first(), BillSource.MANUAL, BillStatus.SPLIT, FIXED_INSTANT, emptyList())
            bill.copy(split = BillSplit(config, SplitCalculator.split(bill, config).shares))
        }
        val before = usedMb()
        var transfers = 0
        val ms = measureTimeMillis {
            val balances = DebtSimplifier.balances(bills)
            val debts = DebtSimplifier.simplify(balances)
            transfers = debts.size
            assertThat(balances.values.sum()).isEqualTo(0)
            assertThat(debts.size).isLessThan(members.size)
            val settled = balances.toMutableMap()
            debts.forEach { d -> settled[d.fromMemberId] = settled[d.fromMemberId]!! + d.amountPaise; settled[d.toMemberId] = settled[d.toMemberId]!! - d.amountPaise }
            assertThat(settled.values.all { it == 0L }).isTrue()
        }
        println("STRESS [debts-1000] ${bills.size} bills → ${members.size} members → $transfers transfers in ${ms}ms, heap ${before}→${usedMb()} MB")
        assertThat(ms).isLessThan(5_000)
    }
}
