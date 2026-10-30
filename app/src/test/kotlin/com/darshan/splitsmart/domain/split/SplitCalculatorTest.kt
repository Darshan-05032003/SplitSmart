package com.darshan.splitsmart.domain.split

import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.SplitConfig
import com.darshan.splitsmart.domain.model.SplitError
import com.darshan.splitsmart.domain.model.SplitException
import com.darshan.splitsmart.domain.model.SplitMode
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testItem
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

class SplitCalculatorTest {
    private val a = "a"; private val b = "b"; private val c = "c"
    private val bill = testBill(
        totalPaise = 110_000, taxPaise = 9_000, tipPaise = 1_000,
        items = listOf(testItem("i1", name = "Paneer", unitPaise = 60_000), testItem("i2", name = "Naan", qty = 2, unitPaise = 10_000), testItem("i3", name = "Lassi", unitPaise = 20_000)),
    ) // items 100_000 + tax 9_000 + tip 1_000 = 110_000

    @Test
    fun `equal split distributes the whole bill with remainder to the first members`() {
        val r = SplitCalculator.split(bill, SplitConfig(SplitMode.EQUAL, listOf(a, b, c)))
        assertThat(r.shares.map { it.totalPaise }).containsExactly(36_667L, 36_667L, 36_666L).inOrder()
        assertThat(r.totalPaise).isEqualTo(110_000)
        assertThat(r.shares.map { it.taxPaise }).containsExactly(3_000L, 3_000L, 3_000L)
        assertThat(r.shares.map { it.tipPaise }).containsExactly(334L, 333L, 333L).inOrder()
    }

    @Test
    fun `by item assigns items to their eaters and shares unassigned items with everyone`() {
        val config = SplitConfig(SplitMode.BY_ITEM, listOf(a, b, c), itemAssignments = mapOf("i1" to setOf(a), "i2" to setOf(a, b)))
        val r = SplitCalculator.split(bill, config)
        val byId = r.shares.associateBy { it.memberId }
        // a: 60_000 + 10_000 + 6_667 (lassi/3) ; b: 10_000 + 6_667 ; c: 6_666
        assertThat(byId[a]!!.itemsPaise).isEqualTo(76_667)
        assertThat(byId[b]!!.itemsPaise).isEqualTo(16_667)
        assertThat(byId[c]!!.itemsPaise).isEqualTo(6_666)
        // tax 9_000 proportional to item shares: 6900.03, 1500.03, 599.94 → 6900, 1500, 600
        assertThat(byId[a]!!.taxPaise).isEqualTo(6_900)
        assertThat(byId[b]!!.taxPaise).isEqualTo(1_500)
        assertThat(byId[c]!!.taxPaise).isEqualTo(600)
        assertThat(r.shares.sumOf { it.tipPaise }).isEqualTo(1_000)
        assertThat(r.totalPaise).isEqualTo(110_000)
    }

    @Test
    fun `by item ignores assignees who are not participants and empty assignment sets`() {
        val config = SplitConfig(SplitMode.BY_ITEM, listOf(a, b), itemAssignments = mapOf("i1" to setOf("ghost"), "i2" to emptySet(), "i3" to setOf(b, "ghost")))
        val r = SplitCalculator.split(bill, config)
        val byId = r.shares.associateBy { it.memberId }
        assertThat(byId[a]!!.itemsPaise).isEqualTo(30_000 + 10_000)        // i1 & i2 shared by a, b
        assertThat(byId[b]!!.itemsPaise).isEqualTo(30_000 + 10_000 + 20_000)
        assertThat(r.totalPaise).isEqualTo(110_000)
    }

    @Test
    fun `by share uses weights or percentages`() {
        val weights = SplitCalculator.split(bill, SplitConfig(SplitMode.BY_SHARE, listOf(a, b, c), shares = mapOf(a to 2, b to 1, c to 1)))
        assertThat(weights.shares.map { it.totalPaise }).containsExactly(55_000L, 27_500L, 27_500L).inOrder()
        val percent = SplitCalculator.split(bill, SplitConfig(SplitMode.BY_SHARE, listOf(a, b, c), shares = mapOf(a to 50, b to 30, c to 20)))
        assertThat(percent.shares.map { it.totalPaise }).containsExactly(55_000L, 33_000L, 22_000L).inOrder()
        assertThat(percent.shares.map { it.taxPaise }).containsExactly(4_500L, 2_700L, 1_800L).inOrder()
    }

    @Test
    fun `missing shares count as zero and a member with zero share pays nothing`() {
        val r = SplitCalculator.split(bill, SplitConfig(SplitMode.BY_SHARE, listOf(a, b, c), shares = mapOf(a to 1, b to 1)))
        assertThat(r.shares.map { it.totalPaise }).containsExactly(55_000L, 55_000L, 0L).inOrder()
    }

    @Test
    fun `a total that does not add up is spread as an adjustment so paise still sum exactly`() {
        val odd = testBill(totalPaise = 100_057, taxPaise = 0, tipPaise = 0, items = listOf(testItem("i1", unitPaise = 100_000)))
        val r = SplitCalculator.split(odd, SplitConfig(SplitMode.BY_ITEM, listOf(a, b, c), itemAssignments = mapOf("i1" to setOf(a))))
        val byId = r.shares.associateBy { it.memberId }
        assertThat(byId[a]!!.itemsPaise).isEqualTo(100_000)
        assertThat(byId[a]!!.adjustmentPaise).isEqualTo(57)   // proportional to item share → all to a
        assertThat(r.totalPaise).isEqualTo(100_057)
        val discounted = testBill(totalPaise = 90_000, taxPaise = 0, tipPaise = 0, items = listOf(testItem("i1", unitPaise = 100_000)))
        val d = SplitCalculator.split(discounted, SplitConfig(SplitMode.EQUAL, listOf(a, b)))
        assertThat(d.shares.map { it.totalPaise }).containsExactly(45_000L, 45_000L)
        assertThat(d.shares.map { it.adjustmentPaise }).containsExactly(-5_000L, -5_000L)
    }

    @Test
    fun `bill without items splits tax tip and total by participants`() {
        val plain = testBill(totalPaise = 1_001, items = emptyList())
        val r = SplitCalculator.split(plain, SplitConfig(SplitMode.BY_ITEM, listOf(a, b)))
        assertThat(r.shares.map { it.totalPaise }).containsExactly(501L, 500L).inOrder()
    }

    @Test
    fun `single participant gets everything`() {
        val r = SplitCalculator.split(bill, SplitConfig(SplitMode.EQUAL, listOf(a)))
        assertThat(r.shares.single().totalPaise).isEqualTo(110_000)
    }

    @Test
    fun `invalid configurations are rejected with a reason`() {
        assertThat(runCatching { SplitCalculator.split(bill, SplitConfig(SplitMode.EQUAL, emptyList())) }.exceptionOrNull())
            .isEqualTo(SplitException(SplitError.NO_PARTICIPANTS))
        assertThat(runCatching { SplitCalculator.split(bill, SplitConfig(SplitMode.BY_SHARE, listOf(a, b), shares = mapOf(a to 0, b to 0))) }.exceptionOrNull())
            .isEqualTo(SplitException(SplitError.NO_SHARES))
        assertThat(runCatching { SplitCalculator.split(bill, SplitConfig(SplitMode.BY_SHARE, listOf(a, b), shares = mapOf(a to -1, b to 2))) }.exceptionOrNull())
            .isEqualTo(SplitException(SplitError.NEGATIVE_SHARE))
        assertThat(runCatching { SplitCalculator.split(bill, SplitConfig(SplitMode.EQUAL, listOf(a, a))) }.exceptionOrNull())
            .isEqualTo(SplitException(SplitError.DUPLICATE_PARTICIPANT))
    }

    @Test
    fun `property - random bills and modes always sum exactly to the bill total`() {
        val random = Random(7)
        repeat(1_000) {
            val members = List(random.nextInt(1, 12)) { "m$it" }
            val items = List(random.nextInt(0, 15)) { i -> testItem("i$i", qty = random.nextInt(1, 5), unitPaise = random.nextLong(1, 50_000)) }
            val subtotal = items.sumOf { it.totalPaise }
            val tax = random.nextLong(0, 5_000); val tip = random.nextLong(0, 2_000)
            val total = (subtotal + tax + tip + random.nextLong(-500, 500)).coerceAtLeast(1)
            val b = testBill(totalPaise = total, taxPaise = tax, tipPaise = tip, items = items)
            val mode = SplitMode.entries[random.nextInt(3)]
            val config = SplitConfig(
                mode, members,
                itemAssignments = items.associate { it.id to members.filter { random.nextBoolean() }.toSet() },
                shares = members.associateWith { random.nextInt(0, 5) }.let { s -> if (s.values.all { it == 0 }) s + (members.first() to 1) else s },
            )
            val r = SplitCalculator.split(b, config)
            assertThat(r.totalPaise).isEqualTo(total)
            assertThat(r.shares.map { it.memberId }).containsExactlyElementsIn(members).inOrder()
            assertThat(r.shares.sumOf { it.taxPaise }).isEqualTo(tax)
            assertThat(r.shares.sumOf { it.tipPaise }).isEqualTo(tip)
            assertThat(r.shares.sumOf { it.itemsPaise }).isEqualTo(subtotal)
        }
    }

    @Test
    fun `Bill with a split enforces that shares sum to the total`() {
        val r = SplitCalculator.split(bill, SplitConfig(SplitMode.EQUAL, listOf(a, b)))
        val ok = bill.copy(split = com.darshan.splitsmart.domain.model.BillSplit(SplitConfig(SplitMode.EQUAL, listOf(a, b)), r.shares))
        assertThat(ok.split!!.shares).hasSize(2)
        val bad = runCatching { bill.copy(split = com.darshan.splitsmart.domain.model.BillSplit(SplitConfig(SplitMode.EQUAL, listOf(a, b)), r.shares.take(1))) }.exceptionOrNull()
        assertThat(bad).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(Bill.MAX_ITEMS).isEqualTo(200)
    }
}
