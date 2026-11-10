package com.darshan.splitsmart.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.data.repository.GroupRepositoryImpl
import com.darshan.splitsmart.data.repository.SettlementRepositoryImpl
import com.darshan.splitsmart.domain.model.SettlementMethod
import com.darshan.splitsmart.testutil.FIXED_INSTANT
import com.darshan.splitsmart.testutil.TestDispatcherProvider
import com.darshan.splitsmart.testutil.createLegacyDatabase
import com.darshan.splitsmart.testutil.fixedClock
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

/** Settled payments (DB v5): recorded by a tap, kept in history when a member leaves, gone with their group. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SettlementDaoTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: SplitSmartDatabase
    private lateinit var groups: GroupRepositoryImpl
    private lateinit var settlements: SettlementRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, SplitSmartDatabase::class.java).allowMainThreadQueries().build()
        val dispatchers = TestDispatcherProvider(Dispatchers.Unconfined)
        groups = GroupRepositoryImpl(db.groupDao(), db.memberDao(), fixedClock, dispatchers)
        settlements = SettlementRepositoryImpl(db.settlementDao(), fixedClock, dispatchers)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `recording keeps who paid whom, how much, how and when, and undo deletes it`() = runTest {
        val goa = groups.createGroup("Goa trip")
        val asha = groups.addMember(goa.id, "Asha", "asha@okfake")
        val ravi = groups.addMember(goa.id, "Ravi", null)
        val paid = settlements.record(ravi, asha, 42_084, SettlementMethod.UPI_APP)
        val stored = settlements.observeSettlements(goa.id).first().single()
        assertThat(stored).isEqualTo(paid)
        assertThat(stored.fromMemberId).isEqualTo(ravi.id)
        assertThat(stored.toName).isEqualTo("Asha")
        assertThat(stored.amountPaise).isEqualTo(42_084)
        assertThat(stored.method).isEqualTo(SettlementMethod.UPI_APP)
        assertThat(stored.createdAt).isEqualTo(FIXED_INSTANT)
        assertThat(settlements.observeAllSettlements().first()).containsExactly(paid)
        settlements.delete(paid.id)
        assertThat(settlements.observeSettlements(goa.id).first()).isEmpty()
    }

    @Test
    fun `newest first, per group`() = runTest {
        val goa = groups.createGroup("Goa trip")
        val flat = groups.createGroup("Flat")
        groups.addMember(goa.id, "Asha", null)
        db.settlementDao().insertAll(
            listOf(
                testSettlement("old", goa.id, from = null, to = null, createdAt = FIXED_INSTANT.minusSeconds(60)).toEntity(),
                testSettlement("new", goa.id, from = null, to = null).toEntity(),
                testSettlement("other", flat.id, from = null, to = null).toEntity(),
            ),
        )
        assertThat(settlements.observeSettlements(goa.id).first().map { it.id }).containsExactly("new", "old").inOrder()
        assertThat(db.settlementDao().count()).isEqualTo(3)
    }

    @Test
    fun `removing a member keeps the payment with its names but takes it out of the balances`() = runTest {
        val goa = groups.createGroup("Goa trip")
        val asha = groups.addMember(goa.id, "Asha", null)
        val ravi = groups.addMember(goa.id, "Ravi", null)
        settlements.record(ravi, asha, 500, SettlementMethod.OTHER)
        groups.removeMember(ravi.id)
        val kept = settlements.observeSettlements(goa.id).first().single()
        assertThat(kept.fromMemberId).isNull()
        assertThat(kept.toMemberId).isEqualTo(asha.id)
        assertThat(kept.fromName).isEqualTo("Ravi")
        assertThat(kept.counts).isFalse()
    }

    @Test
    fun `deleting the group deletes its payments, and members of two groups cannot settle`() = runTest {
        val goa = groups.createGroup("Goa trip")
        val flat = groups.createGroup("Flat")
        val asha = groups.addMember(goa.id, "Asha", null)
        val ravi = groups.addMember(goa.id, "Ravi", null)
        val meera = groups.addMember(flat.id, "Meera", null)
        settlements.record(ravi, asha, 500, SettlementMethod.OTHER)
        assertThat(runCatching { settlements.record(meera, asha, 500, SettlementMethod.OTHER) }.isFailure).isTrue()
        groups.deleteGroup(goa.id)
        assertThat(db.settlementDao().count()).isEqualTo(0)
    }

    @Test
    fun `a corrupt row is skipped instead of failing the history`() = runTest {
        val goa = groups.createGroup("Goa trip")
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO settlements (id, group_id, from_member_id, to_member_id, from_name, to_name, amount_paise, method, created_at) " +
                "VALUES ('bad', '${goa.id}', NULL, NULL, 'Ravi', 'Asha', -5, 'UPI_APP', 1), ('odd', '${goa.id}', NULL, NULL, 'Ravi', 'Asha', 5, 'CARRIER_PIGEON', 2)",
        )
        val read = settlements.observeSettlements(goa.id).first()
        assertThat(read.map { it.id }).containsExactly("odd")
        assertThat(read.single().method).isEqualTo(SettlementMethod.OTHER)
    }

    @Test
    fun `auto migration from v4 keeps every group, member and bill and adds an empty history`() = runTest {
        val name = "migration-v4.db"
        createLegacyDatabase(context, name, 4) { raw ->
            raw.execSQL("INSERT INTO split_groups (id, name, created_at, archived_at) VALUES ('g1', 'Trip', 1, NULL)")
            raw.execSQL("INSERT INTO group_members (id, group_id, name, vpa, created_at) VALUES ('m1', 'g1', 'Asha', NULL, 1)")
            raw.execSQL(
                "INSERT INTO bills (id, group_id, title, total_paise, tax_paise, tip_paise, paid_by_member_id, source, status, created_at, split_config) " +
                    "VALUES ('b1', 'g1', 'Dinner', 1000, 0, 0, 'm1', 'MANUAL', 'DRAFT', 1, NULL)",
            )
        }
        val migrated = Room.databaseBuilder(context, SplitSmartDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            assertThat(migrated.groupDao().getById("g1")!!.name).isEqualTo("Trip")
            assertThat(migrated.memberDao().countByGroup("g1")).isEqualTo(1)
            assertThat(migrated.billDao().count()).isEqualTo(1)
            assertThat(migrated.settlementDao().count()).isEqualTo(0)
            migrated.settlementDao().insert(testSettlement("s1", "g1", from = "m1", to = null).toEntity())
            assertThat(migrated.settlementDao().observeByGroup("g1").first().single().fromMemberId).isEqualTo("m1")
            assertThat(migrated.openHelper.readableDatabase.version).isEqualTo(5)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }
}
