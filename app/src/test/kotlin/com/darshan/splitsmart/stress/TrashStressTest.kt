package com.darshan.splitsmart.stress

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.data.local.GroupEntity
import com.darshan.splitsmart.data.local.MemberEntity
import com.darshan.splitsmart.data.local.SettlementEntity
import com.darshan.splitsmart.data.local.SplitSmartDatabase
import com.darshan.splitsmart.data.repository.BillRepositoryImpl
import com.darshan.splitsmart.data.repository.SettlementRepositoryImpl
import com.darshan.splitsmart.data.repository.TrashRepositoryImpl
import com.darshan.splitsmart.domain.model.BillSplit
import com.darshan.splitsmart.domain.model.BillStatus
import com.darshan.splitsmart.domain.model.MemberShare
import com.darshan.splitsmart.domain.model.SplitConfig
import com.darshan.splitsmart.domain.model.SplitMode
import com.darshan.splitsmart.domain.split.DebtSimplifier
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testItem
import java.time.Instant
import com.darshan.splitsmart.testutil.TestDispatcherProvider
import com.darshan.splitsmart.testutil.fixedClock
import com.google.common.truth.Truth.assertThat
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Delete + Undo at scale through Room: a big group goes and comes back exactly, many times, in bounded time. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TrashStressTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: SplitSmartDatabase
    private val dispatchers = TestDispatcherProvider(Dispatchers.Unconfined)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, SplitSmartDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    @Test
    fun `a 200-person group with 2,000 split bills and 500 payments survives 20 delete and undo cycles exactly`() = runTest {
        val groupId = "big"
        val billRepo = BillRepositoryImpl(db.billDao(), dispatchers)
        val settlementRepo = SettlementRepositoryImpl(db.settlementDao(), fixedClock, dispatchers)
        db.groupDao().insert(GroupEntity(groupId, "Wedding", 0, null))
        repeat(PEOPLE) { db.memberDao().insert(MemberEntity("m$it", groupId, "Person $it", if (it % 3 == 0) "p$it@okfake" else null, it.toLong())) }
        repeat(BILLS) { i ->
            val payer = "m${i % PEOPLE}"
            val other = "m${(i + 1) % PEOPLE}"
            val total = 10_000L + i
            val lines = List(3) { l -> testItem(id = "b$i-l$l", billId = "b$i", name = "Line $l", unitPaise = if (l == 0) total - 2 else 1, sortOrder = l) }
            billRepo.saveBill(
                testBill(id = "b$i", groupId = groupId, title = "Bill $i", totalPaise = total, items = lines, paidByMemberId = payer, createdAt = Instant.ofEpochSecond(i.toLong()))
                    .copy(status = BillStatus.SPLIT, split = BillSplit(SplitConfig(SplitMode.EQUAL, listOf(payer, other)), listOf(MemberShare(payer, total / 2, 0, 0, 0), MemberShare(other, total - total / 2, 0, 0, 0)))),
            )
        }
        db.settlementDao().insertAll(List(PAYMENTS) { SettlementEntity("s$it", groupId, "m${it % PEOPLE}", "m${(it + 7) % PEOPLE}", "Person ${it % PEOPLE}", "Person ${(it + 7) % PEOPLE}", 1_000L + it, "OTHER", it.toLong()) })
        val trash = TrashRepositoryImpl(db.trashDao(), dispatchers)
        suspend fun balances() = DebtSimplifier.balances(billRepo.observeBills(groupId).first(), settlementRepo.observeSettlements(groupId).first())
        val before = balances()

        var deleteMs = 0L
        var restoreMs = 0L
        repeat(CYCLES) {
            var removal: com.darshan.splitsmart.domain.repository.Removal.Group? = null
            deleteMs += measureTimeMillis { removal = trash.removeGroup(groupId) }
            assertThat(db.groupDao().count()).isEqualTo(0)
            restoreMs += measureTimeMillis { assertThat(trash.restore(removal!!)).isTrue() }
        }
        val rows = PEOPLE + BILLS * 6 + PAYMENTS + 1
        println("STRESS [undo-group] $rows rows (200 people, 2,000 bills, 6,000 lines, 4,000 shares, 500 payments) x $CYCLES cycles: delete ${deleteMs / CYCLES} ms, undo ${restoreMs / CYCLES} ms per cycle; balances identical=${balances() == before}")
        assertThat(before.values.count { it != 0L }).isGreaterThan(100)
        assertThat(balances()).isEqualTo(before)
        assertThat(db.billDao().countShares()).isEqualTo(BILLS * 2)
        assertThat(db.settlementDao().count()).isEqualTo(PAYMENTS)
    }

    private companion object {
        const val PEOPLE = 200
        const val BILLS = 2_000
        const val PAYMENTS = 500
        const val CYCLES = 20
    }
}
