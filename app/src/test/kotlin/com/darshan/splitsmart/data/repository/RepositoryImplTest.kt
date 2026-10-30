package com.darshan.splitsmart.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.data.local.SplitSmartDatabase
import com.darshan.splitsmart.domain.model.BillStatus
import com.darshan.splitsmart.testutil.TestDispatcherProvider
import com.darshan.splitsmart.testutil.fixedClock
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testItem
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RepositoryImplTest {
    private lateinit var db: SplitSmartDatabase
    private lateinit var groups: GroupRepositoryImpl
    private lateinit var bills: BillRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SplitSmartDatabase::class.java)
            .allowMainThreadQueries().build()
        val dispatchers = TestDispatcherProvider(Dispatchers.Unconfined)
        groups = GroupRepositoryImpl(db.groupDao(), db.memberDao(), fixedClock, dispatchers)
        bills = BillRepositoryImpl(db.billDao(), dispatchers)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `group and member round trip through room`() = runTest {
        val g = groups.createGroup("Trip")
        val m = groups.addMember(g.id, "Asha", "asha@upi")
        assertThat(groups.observeGroup(g.id).first()).isEqualTo(g)
        assertThat(groups.observeMembers(g.id).first()).containsExactly(m)
        assertThat(groups.observeGroupSummaries().first().single().memberCount).isEqualTo(1)
        groups.updateMember(m.copy(name = "Asha R"))
        assertThat(groups.getMembers(g.id).single().name).isEqualTo("Asha R")
        groups.renameGroup(g.id, "Trip 2")
        assertThat(groups.observeGroup(g.id).first()!!.name).isEqualTo("Trip 2")
        groups.removeMember(m.id)
        groups.deleteGroup(g.id)
        assertThat(groups.observeGroup(g.id).first()).isNull()
    }

    @Test
    fun `bill with items round trips and keeps domain invariants`() = runTest {
        val g = groups.createGroup("Trip")
        val bill = testBill(groupId = g.id, status = BillStatus.SPLIT, items = listOf(testItem("i1"), testItem("i2", name = "Roti", qty = 4, unitPaise = 1_500, sortOrder = 1)))
        bills.saveBill(bill)
        val loaded = bills.observeBill("b1").first()!!
        assertThat(loaded).isEqualTo(bill)
        assertThat(bills.observeBills(g.id).first()).containsExactly(bill)
        assertThat(bills.observeRecentBills(5).first()).containsExactly(bill)
        assertThat(bills.getBill("b1")).isEqualTo(bill)
        bills.deleteBill("b1")
        assertThat(bills.getBill("b1")).isNull()
    }

    @Test
    fun `all members and all bills across groups for the home screen`() = runTest {
        val a = groups.createGroup("Goa")
        val b = groups.createGroup("Flat")
        val m1 = groups.addMember(a.id, "Asha", null)
        val m2 = groups.addMember(b.id, "Ravi", null)
        assertThat(groups.observeAllMembers().first()).containsExactly(m1, m2)
        val older = testBill(id = "old", groupId = a.id, createdAt = java.time.Instant.EPOCH)
        val newer = testBill(id = "new", groupId = b.id)
        bills.saveBill(older)
        bills.saveBill(newer)
        assertThat(bills.observeAllBills().first().map { it.id }).containsExactly("new", "old").inOrder()
    }
}
