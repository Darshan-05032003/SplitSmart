package com.darshan.splitsmart.ui.history

import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.SettlementMethod
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testMember
import com.darshan.splitsmart.testutil.testSettlement
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Test

class HistoryLogicTest {
    private val zone = ZoneOffset.UTC
    private val members = listOf(testMember("a", name = "Asha"), testMember("r", name = "Ravi"), testMember("m", name = "Meera"))
    private fun at(iso: String): Instant = Instant.parse(iso)
    private fun bill(id: String, title: String, paise: Long, payer: String?, time: String): Bill =
        testBill(id, title = title, totalPaise = paise, items = emptyList(), paidByMemberId = payer, createdAt = at(time))

    private val bills = listOf(
        bill("b1", "Dinner at Marigold Cafe", 1_262_50, "a", "2026-11-08T20:00:00Z"),
        bill("b2", "Taxi to the airport", 900_00, "r", "2026-11-02T08:00:00Z"),
        bill("b3", "Villa", 12_000_00, "a", "2026-10-28T12:00:00Z"),
        bill("b4", "Groceries", 450_00, null, "2026-10-29T12:00:00Z"),
        bill("b5", "Museum tickets", 600_00, "m", "2026-09-30T23:30:00Z"),
    )
    private val settlements = listOf(
        testSettlement("s1", from = "r", to = "a", amountPaise = 420_84, createdAt = at("2026-11-09T10:00:00Z")),
        testSettlement("s2", from = null, to = "a", fromName = "Kabir", amountPaise = 100_00, method = SettlementMethod.OTHER, createdAt = at("2026-10-30T10:00:00Z")),
    )

    @Test
    fun `bills and payments merge newest first into months with their totals`() {
        val months = HistoryLogic.timeline(bills, settlements, members, zone)
        assertThat(months.map { it.month }).containsExactly(YearMonth.of(2026, 11), YearMonth.of(2026, 10), YearMonth.of(2026, 9)).inOrder()
        val november = months.first()
        assertThat(november.entries.map { it.key }).containsExactly("s:s1", "b:b1", "b:b2").inOrder()
        assertThat(november.spentPaise).isEqualTo(1_262_50 + 900_00)
        assertThat(november.settledPaise).isEqualTo(420_84)
        val october = months[1]
        assertThat(october.entries.map { it.key }).containsExactly("s:s2", "b:b4", "b:b3").inOrder()
        assertThat((october.entries[1] as HistoryEntry.BillEntry).payerName).isNull()
        assertThat((november.entries[1] as HistoryEntry.BillEntry).payerName).isEqualTo("Asha")
        // A payment keeps the names it was made with, even after someone left.
        assertThat((october.entries[0] as HistoryEntry.PaymentEntry).settlement.fromName).isEqualTo("Kabir")
    }

    @Test
    fun `months follow the phone's time zone`() {
        val ist = java.time.ZoneId.of("Asia/Kolkata")
        // 30 Sep 23:30 UTC is already 1 Oct in India.
        assertThat(HistoryLogic.timeline(listOf(bills.last()), emptyList(), members, ist).single().month).isEqualTo(YearMonth.of(2026, 10))
    }

    @Test
    fun `nothing yet is no months`() {
        assertThat(HistoryLogic.timeline(emptyList(), emptyList(), members, zone)).isEmpty()
    }

    @Test
    fun `insights - who paid most, top categories and what was settled`() {
        val insights = InsightsLogic.of(bills, members, settlements)
        assertThat(insights.spentPaise).isEqualTo(1_262_50 + 900_00 + 12_000_00 + 450_00 + 600_00)
        assertThat(insights.billCount).isEqualTo(5)
        assertThat(insights.topPayers.map { it.label to it.paise }).containsExactly(
            "Asha" to 13_262_50L, "Ravi" to 900_00L, "Meera" to 600_00L,
        ).inOrder()
        assertThat(insights.topPayers.first().fraction).isEqualTo(1f)
        assertThat(insights.topPayers[1].fraction).isWithin(0.001f).of(900_00f / 13_262_50f)
        assertThat(insights.topCategories.map { it.key }).containsExactly("stay", "food", "travel", "fun", "groceries").inOrder()
        assertThat(insights.settledPaise).isEqualTo(520_84)
        assertThat(insights.settledCount).isEqualTo(2)
    }

    @Test
    fun `categories come from the words in a bill's name, the rest is other`() {
        assertThat(BillCategory.of("Dinner at Marigold Cafe")).isEqualTo(BillCategory.Food)
        assertThat(BillCategory.of("CAB to station")).isEqualTo(BillCategory.Travel)
        assertThat(BillCategory.of("Hotel Marigold Bhavan")).isEqualTo(BillCategory.Stay)
        assertThat(BillCategory.of("Weekly groceries")).isEqualTo(BillCategory.Groceries)
        assertThat(BillCategory.of("Movie night")).isEqualTo(BillCategory.Fun)
        assertThat(BillCategory.of("Electricity bill")).isEqualTo(BillCategory.Home)
        assertThat(BillCategory.of("Mystery box")).isEqualTo(BillCategory.Other)
        // Whole words only: "bus" in "business" is not a bus.
        assertThat(BillCategory.of("Business lunch")).isEqualTo(BillCategory.Food)
        assertThat(BillCategory.of("Business cards")).isEqualTo(BillCategory.Other)
    }

    @Test
    fun `bars cap at five, ties break by name, and huge sums never overflow`() {
        val people = (1..8).map { testMember("p$it", name = "Person $it") }
        val many = people.mapIndexed { i, p -> bill("x$i", "Lunch", 1_000_00, p.id, "2026-11-01T00:00:00Z") }
        val insights = InsightsLogic.of(many, people, emptyList())
        assertThat(insights.topPayers).hasSize(InsightsLogic.MAX_BARS)
        assertThat(insights.topPayers.map { it.label }).containsExactly("Person 1", "Person 2", "Person 3", "Person 4", "Person 5").inOrder()
        val rich = List(3) { bill("r$it", "Villa", com.darshan.splitsmart.core.Money.MAX_PAISE, "a", "2026-11-01T00:00:00Z") }
        val big = InsightsLogic.of(rich, members, emptyList())
        assertThat(big.spentPaise).isEqualTo(3 * com.darshan.splitsmart.core.Money.MAX_PAISE)
        assertThat(InsightsLogic.of(emptyList(), members, emptyList()).topPayers).isEmpty()
    }
}
