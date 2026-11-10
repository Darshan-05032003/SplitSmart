package com.darshan.splitsmart.ui.history

import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.SettlementMethod
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testGroup
import com.darshan.splitsmart.testutil.testMember
import com.darshan.splitsmart.testutil.testSettlement
import com.darshan.splitsmart.ui.LoadState
import java.time.Instant
import java.time.ZoneOffset

/** A fictional Goa trip over three months: bills with made-up places, payments marked in a UPI app and in cash. */
internal object HistoryFixtures {
    val group = testGroup("goa", "Goa trip")
    val members = listOf(testMember("a", "goa", "Asha"), testMember("r", "goa", "Ravi"), testMember("m", "goa", "Meera"), testMember("d", "goa", "Darshan"))

    private fun bill(id: String, title: String, paise: Long, payer: String?, at: String): Bill =
        testBill(id, "goa", title = title, totalPaise = paise, items = emptyList(), paidByMemberId = payer, createdAt = Instant.parse(at))

    val bills = listOf(
        bill("b1", "Dinner at Marigold Cafe", 1_262_50, "a", "2026-11-08T14:00:00Z"),
        bill("b2", "Taxi to the airport", 900_00, "r", "2026-11-02T04:00:00Z"),
        bill("b3", "Villa", 12_000_00, "a", "2026-10-28T06:00:00Z"),
        bill("b4", "Groceries", 450_00, null, "2026-10-26T06:00:00Z"),
        bill("b5", "Museum tickets", 600_00, "m", "2026-09-30T08:00:00Z"),
        bill("b6", "Ferry", 320_00, "d", "2026-09-29T08:00:00Z"),
    )
    val settlements = listOf(
        testSettlement("s1", "goa", from = "r", to = "a", amountPaise = 420_84, createdAt = Instant.parse("2026-11-09T10:00:00Z")),
        testSettlement("s2", "goa", from = "m", to = "a", fromName = "Meera", amountPaise = 1_500_00, method = SettlementMethod.OTHER, createdAt = Instant.parse("2026-10-30T10:00:00Z")),
    )

    val content = HistoryContent(group, HistoryLogic.timeline(bills, settlements, members, ZoneOffset.UTC), InsightsLogic.of(bills, members, settlements))

    fun state(selected: Boolean = false) = HistoryUiState(LoadState.Content(content), selected = if (selected) settlements.first() else null)

    val callbacks = HistoryCallbacks({})
}
