package com.darshan.splitsmart.stress

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.core.DefaultDispatcherProvider
import com.darshan.splitsmart.data.local.SplitSmartDatabase
import com.darshan.splitsmart.data.local.toEntity
import com.darshan.splitsmart.data.repository.BillRepositoryImpl
import com.darshan.splitsmart.data.repository.GroupRepositoryImpl
import com.darshan.splitsmart.testutil.fixedClock
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testGroup
import com.darshan.splitsmart.testutil.testItem
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.system.measureTimeMillis

/** Run with `./gradlew stressTest`. Prints `STRESS [...]` lines that are copied into docs/LOAD_TEST_REPORT.md. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RoomStressTest {
    private lateinit var db: SplitSmartDatabase

    @Before
    fun setUp() {
        db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), SplitSmartDatabase::class.java, "stress.db")
            .fallbackToDestructiveMigration(true).build()
    }

    @After
    fun tearDown() {
        db.close()
        ApplicationProvider.getApplicationContext<android.content.Context>().deleteDatabase("stress.db")
    }

    private fun usedMb() = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1_048_576

    @Test
    fun `100k bills in room - insert, history query and paged query time`() = runBlocking {
        val groups = 100
        val billsPerGroup = 1_000
        val total = groups * billsPerGroup
        withContext(Dispatchers.IO) { repeat(groups) { db.groupDao().insert(testGroup("g$it", "Group $it").toEntity()) } }
        val before = usedMb()
        val insertMs = measureTimeMillis {
            withContext(Dispatchers.IO) {
                (0 until total).chunked(2_000).forEach { chunk ->
                    val bills = chunk.map { i -> testBill("b$i", groupId = "g${i % groups}", title = "Bill $i", totalPaise = 100L + i, items = emptyList(), createdAt = fixedClock.instant().plusSeconds(i.toLong())).toEntity() }
                    val items = chunk.map { i -> testItem("i$i", "b$i", unitPaise = 100L + i).toEntity() }
                    db.billDao().insertBillsAndItems(bills, items)
                }
            }
        }
        val afterInsert = usedMb()
        assertThat(db.billDao().count()).isEqualTo(total)
        val historyMs = measureTimeMillis { assertThat(db.billDao().observeByGroup("g7").first()).hasSize(billsPerGroup) }
        val pageMs = measureTimeMillis { assertThat(db.billDao().getPage("g7", limit = 50, offset = 500)).hasSize(50) }
        val recentMs = measureTimeMillis { assertThat(db.billDao().observeRecent(50).first()).hasSize(50) }
        val summaryMs = measureTimeMillis { assertThat(db.groupDao().observeSummaries().first()).hasSize(groups) }
        println("STRESS [room-100k] insert=${insertMs}ms (${total * 1000L / insertMs.coerceAtLeast(1)} rows/s) heap ${before}→${afterInsert} MB; history(1k bills+items)=${historyMs}ms; page(50)=${pageMs}ms; recent(50)=${recentMs}ms; summaries(100 groups)=${summaryMs}ms")
        assertThat(historyMs).isLessThan(5_000)
        assertThat(pageMs).isLessThan(500)
    }

    @Test
    fun `1000 concurrent member and group writes all land`() = runBlocking {
        val dispatchers = DefaultDispatcherProvider()
        val groups = GroupRepositoryImpl(db.groupDao(), db.memberDao(), fixedClock, dispatchers)
        val bills = BillRepositoryImpl(db.billDao(), dispatchers)
        val g = groups.createGroup("Stress")
        val ms = measureTimeMillis {
            (0 until 1_000).map { i ->
                async(Dispatchers.IO) {
                    groups.addMember(g.id, "Member $i", null)
                    bills.saveBill(testBill("sb$i", groupId = g.id, title = "Bill $i", items = listOf(testItem("si$i", "sb$i"))))
                }
            }.awaitAll()
        }
        assertThat(groups.getMembers(g.id)).hasSize(1_000)
        assertThat(bills.observeBills(g.id).first()).hasSize(1_000)
        println("STRESS [room-concurrent] 1000 members + 1000 bills written concurrently in ${ms}ms, heap ${usedMb()} MB")
    }
}
