package com.darshan.splitsmart.ui.settleup

import com.darshan.splitsmart.data.upi.UpiLinkBuilder
import com.darshan.splitsmart.domain.model.BillStatus
import com.darshan.splitsmart.domain.model.Debt
import com.darshan.splitsmart.domain.model.Member
import com.darshan.splitsmart.domain.model.SettlementMethod
import com.darshan.splitsmart.domain.split.BillCheck
import com.darshan.splitsmart.domain.split.BillProblem
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testGroup
import com.darshan.splitsmart.testutil.testMember
import com.darshan.splitsmart.ui.LoadState

/** A fictional Goa trip settling up. UPI IDs use made-up handles. Shared by the screen, screenshot and stress tests. */
internal object SettleUpFixtures {
    val group = testGroup("goa", "Goa trip")
    val asha = testMember("a", "goa", "Asha", vpa = "asha@okfake")
    val ravi = testMember("r", "goa", "Ravi")
    val meera = testMember("m", "goa", "Meera", vpa = "meera@fakebank")
    val darshan = testMember("d", "goa", "Darshan")

    fun item(payer: Member, payee: Member, paise: Long): SettleUpItem {
        val link = payee.vpa?.let { runCatching { UpiLinkBuilder.build(it, payee.name, paise, "Goa trip via SplitSmart") }.getOrNull() }
        return SettleUpItem(Debt(payer.id, payee.id, paise), payer, payee, link)
    }

    val items = listOf(
        item(ravi, asha, 42_084),
        item(darshan, meera, 31_650),
        item(meera, ravi, 12_000),
    )

    val toFix = listOf(
        BillCheck(testBill("f1", "goa", title = "Ferry tickets", totalPaise = 90_000, items = emptyList(), status = BillStatus.SPLIT), BillProblem.MEMBER_LEFT),
        BillCheck(testBill("f2", "goa", title = "Snacks at the beach", totalPaise = 24_000, items = emptyList()), BillProblem.NOT_SPLIT),
    )

    fun content(items: List<SettleUpItem> = SettleUpFixtures.items, toFix: List<BillCheck> = emptyList(), history: Int = 0) =
        SettleUpContent(group, items, toFix, countedBills = 4, history = history)

    fun state(
        items: List<SettleUpItem> = SettleUpFixtures.items,
        toFix: List<BillCheck> = emptyList(),
        check: SettleCheck? = null,
        settled: SettledMoment? = null,
        recording: Boolean = false,
        history: Int = 0,
    ) = SettleUpUiState(LoadState.Content(content(items, toFix, history)), check = check, settled = settled, recording = recording)

    fun check(item: SettleUpItem = items.first(), method: SettlementMethod = SettlementMethod.UPI_APP, asking: Boolean = true) =
        SettleCheck(item.payer.id, item.payee.id, item.payer.name, item.payee.name, item.debt.amountPaise, method, asking)

    fun moment(allSquare: Boolean = false, method: SettlementMethod = SettlementMethod.UPI_APP) =
        SettledMoment("s1", "Ravi", "Asha", 42_084, method, allSquare)

    val callbacks = SettleUpCallbacks({}, {}, {}, {}, {}, {}, {}, {}, {})
}
