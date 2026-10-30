package com.darshan.splitsmart.data.local

import androidx.room.Room
import android.content.Context
import com.darshan.splitsmart.testutil.createLegacyDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.data.repository.ReceiptDraftRepositoryImpl
import com.darshan.splitsmart.testutil.FIXED_INSTANT
import com.darshan.splitsmart.testutil.TestDispatcherProvider
import com.darshan.splitsmart.testutil.testDraft
import com.darshan.splitsmart.testutil.testGroup
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ReceiptDraftDaoTest {
    private fun db() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SplitSmartDatabase::class.java).allowMainThreadQueries().build()

    @Test
    fun `draft round trip through the repository`() = runTest {
        val db = db()
        db.groupDao().insert(testGroup().toEntity())
        val repo = ReceiptDraftRepositoryImpl(db.receiptDraftDao(), TestDispatcherProvider(Dispatchers.Unconfined))
        val draft = testDraft().copy(imagePath = "/cache/receipts/a.jpg")
        repo.save(draft)
        assertThat(repo.get("d1")).isEqualTo(draft)
        assertThat(repo.observe("d1").first()).isEqualTo(draft)
        repo.save(draft.copy(ocrText = "edited"))
        assertThat(repo.get("d1")!!.ocrText).isEqualTo("edited")
        repo.delete("d1")
        assertThat(repo.get("d1")).isNull()
        db.close()
    }

    @Test
    fun `drafts are cascade deleted with their group and purged by age`() = runTest {
        val db = db()
        db.groupDao().insert(testGroup().toEntity())
        db.groupDao().insert(testGroup("g2", "Other").toEntity())
        val dao = db.receiptDraftDao()
        dao.upsert(testDraft("old", createdAt = FIXED_INSTANT.minusSeconds(100_000)).toEntity())
        dao.upsert(testDraft("fresh").toEntity())
        dao.upsert(testDraft("other", groupId = "g2").toEntity())
        assertThat(dao.deleteOlderThan(FIXED_INSTANT.minusSeconds(3_600).toEpochMilli())).isEqualTo(1)
        db.groupDao().deleteById("g1")
        assertThat(dao.getById("fresh")).isNull()
        assertThat(dao.getById("other")).isNotNull()
        db.close()
    }

    /**
     * Builds a real v1 database from the exported schema (`app/schemas/.../1.json`, the same file Room's AutoMigration
     * reads at compile time), then opens it with the current database class so Room runs the 1→2 AutoMigration and
     * validates every table against the v2 entities.
     */
    @Test
    fun `auto migration from v1 keeps existing groups and adds receipt_drafts`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        createLegacyDatabase(context, name, 1) { raw -> raw.execSQL("INSERT INTO split_groups (id, name, created_at) VALUES ('g1', 'Trip', 1)") }
        val migrated = Room.databaseBuilder(context, SplitSmartDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            assertThat(migrated.groupDao().getById("g1")!!.name).isEqualTo("Trip")
            migrated.groupDao().insert(testGroup("g2", "After").toEntity())
            migrated.receiptDraftDao().upsert(testDraft("d1", groupId = "g2").toEntity())
            assertThat(migrated.receiptDraftDao().getById("d1")!!.ocrText).contains("Paneer")
            assertThat(migrated.openHelper.readableDatabase.version).isAtLeast(2)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }
}
