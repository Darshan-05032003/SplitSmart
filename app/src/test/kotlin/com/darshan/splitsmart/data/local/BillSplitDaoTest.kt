package com.darshan.splitsmart.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.data.repository.BillRepositoryImpl
import com.darshan.splitsmart.domain.model.BillSplit
import com.darshan.splitsmart.domain.model.BillStatus
import com.darshan.splitsmart.domain.model.SplitConfig
import com.darshan.splitsmart.domain.model.SplitMode
import com.darshan.splitsmart.domain.split.SplitCalculator
import com.darshan.splitsmart.testutil.TestDispatcherProvider
import com.darshan.splitsmart.testutil.createLegacyDatabase
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testGroup
import com.darshan.splitsmart.testutil.testItem
import com.darshan.splitsmart.testutil.testMember
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BillSplitDaoTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private suspend fun seeded(db: SplitSmartDatabase) {
        db.groupDao().insert(testGroup().toEntity())
        db.memberDao().insert(testMember("a", name = "Asha").toEntity())
        db.memberDao().insert(testMember("b", name = "Ravi").toEntity())
    }

    @Test
    fun `split config and shares round trip and are replaced on re-save`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, SplitSmartDatabase::class.java).allowMainThreadQueries().build()
        seeded(db)
        val repo = BillRepositoryImpl(db.billDao(), TestDispatcherProvider(Dispatchers.Unconfined))
        val bill = testBill(totalPaise = 1_001, items = listOf(testItem(unitPaise = 1_001)), paidByMemberId = "a")
        val config = SplitConfig(SplitMode.BY_ITEM, listOf("a", "b"), itemAssignments = mapOf("i1" to setOf("a", "b")), shares = mapOf("a" to 1))
        val withSplit = bill.copy(status = BillStatus.SPLIT, split = BillSplit(config, SplitCalculator.split(bill, config).shares))
        repo.saveBill(withSplit)
        assertThat(repo.getBill("b1")).isEqualTo(withSplit)
        assertThat(repo.observeBills("g1").first().single().split!!.shares.map { it.totalPaise }).containsExactly(501L, 500L).inOrder()

        val equal = SplitConfig(SplitMode.EQUAL, listOf("a"))
        repo.saveBill(withSplit.copy(split = BillSplit(equal, SplitCalculator.split(bill, equal).shares)))
        assertThat(repo.getBill("b1")!!.split!!.shares.map { it.memberId }).containsExactly("a")
        assertThat(db.billDao().countShares()).isEqualTo(1)

        repo.saveBill(bill) // split removed
        assertThat(repo.getBill("b1")!!.split).isNull()
        assertThat(db.billDao().countShares()).isEqualTo(0)
        db.close()
    }

    @Test
    fun `shares are cascade deleted with the bill and with the member`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, SplitSmartDatabase::class.java).allowMainThreadQueries().build()
        seeded(db)
        val repo = BillRepositoryImpl(db.billDao(), TestDispatcherProvider(Dispatchers.Unconfined))
        val bill = testBill(totalPaise = 1_000, items = emptyList(), paidByMemberId = "a")
        val config = SplitConfig(SplitMode.EQUAL, listOf("a", "b"))
        repo.saveBill(bill.copy(split = BillSplit(config, SplitCalculator.split(bill, config).shares)))
        db.memberDao().deleteById("b")
        assertThat(db.billDao().countShares()).isEqualTo(1)
        // a split whose shares no longer sum to the total is reported as no split rather than a corrupt bill
        assertThat(repo.getBill("b1")!!.split).isNull()
        repo.deleteBill("b1")
        assertThat(db.billDao().countShares()).isEqualTo(0)
        db.close()
    }

    @Test
    fun `auto migration from v2 keeps bills and adds the shares table`() = runTest {
        val name = "migration-v2.db"
        createLegacyDatabase(context, name, 2) { raw ->
            raw.execSQL("INSERT INTO split_groups (id, name, created_at) VALUES ('g1', 'Trip', 1)")
            raw.execSQL("INSERT INTO bills (id, group_id, title, total_paise, tax_paise, tip_paise, paid_by_member_id, source, status, created_at) VALUES ('b1', 'g1', 'Dinner', 500, 0, 0, NULL, 'MANUAL', 'DRAFT', 1)")
        }
        val migrated = Room.databaseBuilder(context, SplitSmartDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            val bill = migrated.billDao().getById("b1")!!
            assertThat(bill.bill.title).isEqualTo("Dinner")
            assertThat(bill.bill.splitConfig).isNull()
            assertThat(bill.shares).isEmpty()
            assertThat(migrated.billDao().countShares()).isEqualTo(0)
            assertThat(migrated.openHelper.readableDatabase.version).isEqualTo(3)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }
}
