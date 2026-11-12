package com.darshan.splitsmart.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.data.repository.BillRepositoryImpl
import com.darshan.splitsmart.data.repository.GroupRepositoryImpl
import com.darshan.splitsmart.data.repository.ReceiptDraftRepositoryImpl
import com.darshan.splitsmart.data.repository.SettlementRepositoryImpl
import com.darshan.splitsmart.data.repository.TrashRepositoryImpl
import com.darshan.splitsmart.domain.model.BillSplit
import com.darshan.splitsmart.domain.model.BillStatus
import com.darshan.splitsmart.domain.model.MemberShare
import com.darshan.splitsmart.domain.model.ReceiptDraft
import com.darshan.splitsmart.domain.model.SettlementMethod
import com.darshan.splitsmart.domain.model.SplitConfig
import com.darshan.splitsmart.domain.model.SplitMode
import com.darshan.splitsmart.domain.split.DebtSimplifier
import com.darshan.splitsmart.testutil.FIXED_INSTANT
import com.darshan.splitsmart.testutil.TestDispatcherProvider
import com.darshan.splitsmart.testutil.fixedClock
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testItem
import com.darshan.splitsmart.testutil.testSettlement
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Delete with Undo, through Room with real foreign keys: a group delete cascades to members, bills, lines, shares, settled
 * payments and unsaved scans; Undo must put back every row exactly (the whole database is compared row by row) and the same
 * balances, or nothing at all.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TrashDaoTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: SplitSmartDatabase
    private lateinit var groups: GroupRepositoryImpl
    private lateinit var bills: BillRepositoryImpl
    private lateinit var settlements: SettlementRepositoryImpl
    private lateinit var drafts: ReceiptDraftRepositoryImpl
    private lateinit var trash: TrashRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, SplitSmartDatabase::class.java).allowMainThreadQueries().build()
        open(db)
    }

    private fun open(database: SplitSmartDatabase) {
        val dispatchers = TestDispatcherProvider(Dispatchers.Unconfined)
        groups = GroupRepositoryImpl(database.groupDao(), database.memberDao(), fixedClock, dispatchers)
        bills = BillRepositoryImpl(database.billDao(), dispatchers)
        settlements = SettlementRepositoryImpl(database.settlementDao(), fixedClock, dispatchers)
        drafts = ReceiptDraftRepositoryImpl(database.receiptDraftDao(), dispatchers)
        trash = TrashRepositoryImpl(database.trashDao(), dispatchers)
    }

    @After
    fun tearDown() = db.close()

    /** A full group: archived, three people (one with a UPI ID), a split bill with lines and shares, a draft bill, payments, a scan. */
    private suspend fun goaTrip(): String {
        val goa = groups.createGroup("Goa trip")
        val asha = groups.addMember(goa.id, "Asha", "asha@okfake")
        val ravi = groups.addMember(goa.id, "Ravi", null)
        val meera = groups.addMember(goa.id, "Meera", null)
        val items = listOf(
            testItem(id = "i1", billId = "dinner", name = "Paneer tikka", qty = 2, unitPaise = 32_000, sortOrder = 0),
            testItem(id = "i2", billId = "dinner", name = "Lime soda", qty = 3, unitPaise = 6_000, sortOrder = 1),
        )
        val config = SplitConfig(SplitMode.EQUAL, listOf(asha.id, ravi.id, meera.id))
        bills.saveBill(
            testBill(id = "dinner", groupId = goa.id, title = "Beach shack dinner", totalPaise = 85_000, taxPaise = 3_000, items = items, paidByMemberId = asha.id)
                .copy(status = BillStatus.SPLIT, split = BillSplit(config, listOf(MemberShare(asha.id, 28_334, 0, 0, 0), MemberShare(ravi.id, 28_333, 0, 0, 0), MemberShare(meera.id, 28_333, 0, 0, 0)))),
        )
        bills.saveBill(testBill(id = "ferry", groupId = goa.id, title = "Ferry tickets", totalPaise = 20_000, items = listOf(testItem(id = "i3", billId = "ferry", unitPaise = 20_000)), paidByMemberId = ravi.id, createdAt = FIXED_INSTANT.minusSeconds(60)))
        settlements.record(ravi, asha, 10_000, SettlementMethod.UPI_APP)
        settlements.record(meera, asha, 5_000, SettlementMethod.OTHER)
        drafts.save(ReceiptDraft("scan1", goa.id, "/cache/receipts/scan1.jpg", "Hotel Marigold Bhavan\nKulfi 130.00", FIXED_INSTANT))
        groups.setArchived(goa.id, true)
        return goa.id
    }

    /** Another group that must never be touched. */
    private suspend fun flat(): String {
        val flat = groups.createGroup("Flat 302")
        val d = groups.addMember(flat.id, "Darshan", null)
        groups.addMember(flat.id, "Priya", null)
        bills.saveBill(testBill(id = "rent", groupId = flat.id, title = "Rent", totalPaise = 1_200_000, items = listOf(testItem(id = "r1", billId = "rent", name = "Rent", unitPaise = 1_200_000)), paidByMemberId = d.id))
        return flat.id
    }

    @Test
    fun `deleting a group and undoing it puts back every row and the same balances`() = runTest {
        val goa = goaTrip()
        flat()
        val before = dump()
        val balancesBefore = balances(goa)
        assertThat(balancesBefore.values.any { it != 0L }).isTrue()

        val removal = trash.removeGroup(goa)!!
        assertThat(removal.name).isEqualTo("Goa trip")
        for (table in TABLES) assertThat(groupRows(dump(), table, goa)).isEmpty()
        assertThat(dump()["bills"]!!.single()).contains("Rent")
        assertThat(dump()["bill_items"]!!.single()).contains("r1")
        assertThat(dump()["bill_shares"]).isEmpty()

        assertThat(trash.restore(removal)).isTrue()
        assertThat(dump()).isEqualTo(before)
        assertThat(balances(goa)).isEqualTo(balancesBefore)
        assertThat(groups.observeGroup(goa).first()!!.isArchived).isTrue()
    }

    @Test
    fun `deleting a bill and undoing it puts back its lines, its split and the same balances`() = runTest {
        val goa = goaTrip()
        val before = dump()
        val balancesBefore = balances(goa)

        val removal = trash.removeBill("dinner")!!
        assertThat(removal.name).isEqualTo("Beach shack dinner")
        assertThat(removal.groupId).isEqualTo(goa)
        assertThat(bills.getBill("dinner")).isNull()
        assertThat(dump()["bill_items"]!!.single()).contains("i3")
        assertThat(dump()["bill_shares"]).isEmpty()
        assertThat(balances(goa)).isNotEqualTo(balancesBefore)

        assertThat(trash.restore(removal)).isTrue()
        assertThat(dump()).isEqualTo(before)
        assertThat(balances(goa)).isEqualTo(balancesBefore)
    }

    @Test
    fun `a bill whose payer was removed meanwhile does not come back half way`() = runTest {
        val goa = goaTrip()
        val removal = trash.removeBill("dinner")!!
        val asha = groups.getMembers(goa).first { it.name == "Asha" }
        groups.removeMember(asha.id)
        val after = dump()

        assertThat(trash.restore(removal)).isFalse()
        assertThat(dump()).isEqualTo(after)
        assertThat(bills.getBill("dinner")).isNull()
    }

    @Test
    fun `a bill whose group was deleted meanwhile does not come back`() = runTest {
        val goa = goaTrip()
        val bill = trash.removeBill("dinner")!!
        trash.removeGroup(goa)!!
        val after = dump()
        assertThat(trash.restore(bill)).isFalse()
        assertThat(dump()).isEqualTo(after)
    }

    @Test
    fun `a group put-back that fails part way writes nothing`() = runTest {
        val goa = goaTrip()
        val other = flat()
        val removal = trash.removeGroup(goa)!!
        // The last rows written (settled payments) clash with a row that appeared meanwhile: the group, its people and its
        // bills were already inserted when that fails, and must all be rolled back.
        val clash = (removal.rows as GroupRows).settlements.first().id
        db.settlementDao().insert(testSettlement(clash, other, from = null, to = null).toEntity())
        val after = dump()

        assertThat(trash.restore(removal)).isFalse()
        assertThat(dump()).isEqualTo(after)
        assertThat(groups.observeGroup(goa).first()).isNull()
    }

    @Test
    fun `undoing twice restores once and the second is refused`() = runTest {
        val goa = goaTrip()
        val before = dump()
        val removal = trash.removeGroup(goa)!!
        assertThat(trash.restore(removal)).isTrue()
        assertThat(trash.restore(removal)).isFalse()
        assertThat(dump()).isEqualTo(before)
    }

    @Test
    fun `deleting what is already gone takes nothing`() = runTest {
        goaTrip()
        val before = dump()
        assertThat(trash.removeGroup("nope")).isNull()
        assertThat(trash.removeBill("nope")).isNull()
        assertThat(dump()).isEqualTo(before)
    }

    @Test
    fun `the copy lives only in memory, so a delete without Undo stands after a restart`() = runTest {
        db.close()
        val name = "trash-restart.db"
        context.deleteDatabase(name)
        db = Room.databaseBuilder(context, SplitSmartDatabase::class.java, name).allowMainThreadQueries().build()
        open(db)
        val goa = goaTrip()
        trash.removeGroup(goa)!!
        db.close()
        // The process dies inside the Undo window: what comes back is the database as the delete left it.
        db = Room.databaseBuilder(context, SplitSmartDatabase::class.java, name).allowMainThreadQueries().build()
        open(db)
        assertThat(groups.observeGroup(goa).first()).isNull()
        for (table in TABLES) assertThat(groupRows(dump(), table, goa)).isEmpty()
    }

    private suspend fun balances(groupId: String): Map<String, Long> =
        DebtSimplifier.balances(bills.observeBills(groupId).first(), settlements.observeSettlements(groupId).first())

    /** Every row of every table, as text, sorted: two dumps are equal only if the databases hold the same rows. */
    private fun dump(): Map<String, List<String>> = TABLES.associateWith { table ->
        val rows = mutableListOf<String>()
        db.openHelper.readableDatabase.query("SELECT * FROM $table").use { c ->
            while (c.moveToNext()) rows += (0 until c.columnCount).joinToString("|") { c.getColumnName(it) + "=" + c.getString(it) }
        }
        rows.sorted()
    }

    private fun groupRows(dump: Map<String, List<String>>, table: String, groupId: String) = dump[table]!!.filter { groupId in it }

    private companion object {
        val TABLES = listOf("split_groups", "group_members", "bills", "bill_items", "bill_shares", "settlements", "receipt_drafts")
    }
}
