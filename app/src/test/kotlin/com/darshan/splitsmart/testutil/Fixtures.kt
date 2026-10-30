package com.darshan.splitsmart.testutil

import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.BillItem
import com.darshan.splitsmart.domain.model.BillSource
import com.darshan.splitsmart.domain.model.BillStatus
import com.darshan.splitsmart.domain.model.Group
import com.darshan.splitsmart.domain.model.Member
import java.time.Instant

fun testGroup(id: String = "g1", name: String = "Goa trip", createdAt: Instant = FIXED_INSTANT) =
    Group(id = id, name = name, createdAt = createdAt)

fun testMember(id: String = "m1", groupId: String = "g1", name: String = "Asha", vpa: String? = null) =
    Member(id = id, groupId = groupId, name = name, vpa = vpa, createdAt = FIXED_INSTANT)

fun testItem(id: String = "i1", billId: String = "b1", name: String = "Paneer tikka", qty: Int = 1, unitPaise: Long = 25_000, sortOrder: Int = 0) =
    BillItem(id = id, billId = billId, name = name, qty = qty, unitPaise = unitPaise, totalPaise = unitPaise * qty, sortOrder = sortOrder)

fun testBill(
    id: String = "b1",
    groupId: String = "g1",
    title: String = "Dinner",
    totalPaise: Long = 25_000,
    taxPaise: Long = 0,
    tipPaise: Long = 0,
    items: List<BillItem> = listOf(testItem(billId = id)),
    createdAt: Instant = FIXED_INSTANT,
    status: BillStatus = BillStatus.DRAFT,
    source: BillSource = BillSource.MANUAL,
    paidByMemberId: String? = null,
) = Bill(
    id = id, groupId = groupId, title = title, totalPaise = totalPaise, taxPaise = taxPaise, tipPaise = tipPaise,
    paidByMemberId = paidByMemberId, source = source, status = status, createdAt = createdAt, items = items,
)
