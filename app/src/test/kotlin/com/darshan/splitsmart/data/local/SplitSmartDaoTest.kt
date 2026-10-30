package com.darshan.splitsmart.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.testutil.FIXED_INSTANT
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testGroup
import com.darshan.splitsmart.testutil.testItem
import com.darshan.splitsmart.testutil.testMember
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SplitSmartDaoTest {
    private lateinit var db: SplitSmartDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SplitSmartDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `group summaries count members and bills, newest first`() = runTest {
        db.groupDao().insert(testGroup("g1", "Old", createdAt = FIXED_INSTANT.minusSeconds(10)).toEntity())
        db.groupDao().insert(testGroup("g2", "New").toEntity())
        db.memberDao().insert(testMember("m1", "g1").toEntity())
        db.memberDao().insert(testMember("m2", "g1", name = "Ravi").toEntity())
        db.billDao().upsertWithItems(testBill("b1", "g1").toEntity(), listOf(testItem(billId = "b1").toEntity()))
        val rows = db.groupDao().observeSummaries().first()
        assertThat(rows.map { it.group.name }).containsExactly("New", "Old").inOrder()
        assertThat(rows[1].memberCount).isEqualTo(2)
        assertThat(rows[1].billCount).isEqualTo(1)
        assertThat(rows[0].memberCount).isEqualTo(0)
    }

    @Test
    fun `member requires an existing group and is cascade deleted`() = runTest {
        val ex = runCatching { db.memberDao().insert(testMember("m1", "missing").toEntity()) }.exceptionOrNull()
        assertThat(ex).isInstanceOf(SQLiteConstraintException::class.java)
        db.groupDao().insert(testGroup().toEntity())
        db.memberDao().insert(testMember().toEntity())
        db.billDao().upsertWithItems(testBill().toEntity(), listOf(testItem().toEntity()))
        db.groupDao().deleteById("g1")
        assertThat(db.memberDao().getByGroup("g1")).isEmpty()
        assertThat(db.billDao().count()).isEqualTo(0)
        assertThat(db.billDao().countItems()).isEqualTo(0)
    }

    @Test
    fun `upsert replaces bill items atomically`() = runTest {
        db.groupDao().insert(testGroup().toEntity())
        db.billDao().upsertWithItems(testBill().toEntity(), listOf(testItem("i1").toEntity(), testItem("i2").toEntity()))
        db.billDao().upsertWithItems(testBill(title = "Dinner v2").toEntity(), listOf(testItem("i3", name = "Naan").toEntity()))
        val bill = db.billDao().getById("b1")!!
        assertThat(bill.bill.title).isEqualTo("Dinner v2")
        assertThat(bill.items.map { it.name }).containsExactly("Naan")
        assertThat(db.billDao().countItems()).isEqualTo(1)
    }

    @Test
    fun `bills are observed newest first per group with items in sort order`() = runTest {
        db.groupDao().insert(testGroup().toEntity())
        db.groupDao().insert(testGroup("g2", "Other").toEntity())
        db.billDao().upsertWithItems(testBill("b1", createdAt = FIXED_INSTANT.minusSeconds(5)).toEntity(), emptyList())
        db.billDao().upsertWithItems(
            testBill("b2", title = "Later").toEntity(),
            listOf(testItem("i2", "b2", name = "second", sortOrder = 1).toEntity(), testItem("i1", "b2", name = "first", sortOrder = 0).toEntity()),
        )
        db.billDao().upsertWithItems(testBill("b3", groupId = "g2").toEntity(), emptyList())
        val bills = db.billDao().observeByGroup("g1").first()
        assertThat(bills.map { it.bill.id }).containsExactly("b2", "b1").inOrder()
        assertThat(bills[0].items.map { it.name }).containsExactly("first", "second").inOrder()
        assertThat(db.billDao().observeRecent(10).first().map { it.bill.id }).containsExactly("b2", "b3", "b1")
    }

    @Test
    fun `delete bill removes its items and observeById emits null`() = runTest {
        db.groupDao().insert(testGroup().toEntity())
        db.billDao().upsertWithItems(testBill().toEntity(), listOf(testItem().toEntity()))
        db.billDao().deleteById("b1")
        assertThat(db.billDao().observeById("b1").first()).isNull()
        assertThat(db.billDao().countItems()).isEqualTo(0)
    }

    @Test
    fun `member removal sets bill payer to null instead of deleting the bill`() = runTest {
        db.groupDao().insert(testGroup().toEntity())
        db.memberDao().insert(testMember().toEntity())
        db.billDao().upsertWithItems(testBill(paidByMemberId = "m1").toEntity(), emptyList())
        db.memberDao().deleteById("m1")
        assertThat(db.billDao().getById("b1")!!.bill.paidByMemberId).isNull()
    }
}
