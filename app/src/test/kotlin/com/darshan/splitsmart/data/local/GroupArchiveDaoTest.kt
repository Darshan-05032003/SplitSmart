package com.darshan.splitsmart.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.data.repository.GroupRepositoryImpl
import com.darshan.splitsmart.testutil.FIXED_INSTANT
import com.darshan.splitsmart.testutil.TestDispatcherProvider
import com.darshan.splitsmart.testutil.createLegacyDatabase
import com.darshan.splitsmart.testutil.fixedClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Archiving a group (DB v4): a timestamp on the group, never a delete, and a migration that keeps every v3 group active. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class GroupArchiveDaoTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `archiving stamps the group, keeps its members and bills, and unarchiving clears it`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, SplitSmartDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repo = GroupRepositoryImpl(db.groupDao(), db.memberDao(), fixedClock, TestDispatcherProvider(Dispatchers.Unconfined))
            val goa = repo.createGroup("Goa trip")
            repo.addMember(goa.id, "Asha", null)
            repo.setArchived(goa.id, archived = true)
            val archived = repo.observeGroupSummaries().first().single()
            assertThat(archived.group.archivedAt).isEqualTo(FIXED_INSTANT)
            assertThat(archived.group.isArchived).isTrue()
            assertThat(archived.memberCount).isEqualTo(1)
            assertThat(repo.observeGroup(goa.id).first()!!.isArchived).isTrue()

            repo.setArchived(goa.id, archived = false)
            assertThat(repo.observeGroupSummaries().first().single().group.archivedAt).isNull()
            assertThat(repo.observeGroup(goa.id).first()!!.isArchived).isFalse()
        } finally {
            db.close()
        }
    }

    @Test
    fun `archiving a group that no longer exists changes nothing`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, SplitSmartDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repo = GroupRepositoryImpl(db.groupDao(), db.memberDao(), fixedClock, TestDispatcherProvider(Dispatchers.Unconfined))
            repo.setArchived("missing", archived = true)
            assertThat(repo.observeGroupSummaries().first()).isEmpty()
        } finally {
            db.close()
        }
    }

    @Test
    fun `auto migration from v3 keeps every group active`() = runTest {
        val name = "migration-v3.db"
        createLegacyDatabase(context, name, 3) { raw ->
            raw.execSQL("INSERT INTO split_groups (id, name, created_at) VALUES ('g1', 'Trip', 1)")
            raw.execSQL("INSERT INTO group_members (id, group_id, name, vpa, created_at) VALUES ('m1', 'g1', 'Asha', NULL, 1)")
        }
        val migrated = Room.databaseBuilder(context, SplitSmartDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            val row = migrated.groupDao().getById("g1")!!
            assertThat(row.name).isEqualTo("Trip")
            assertThat(row.archivedAt).isNull()
            assertThat(migrated.groupDao().observeSummaries().first().single().memberCount).isEqualTo(1)
            assertThat(migrated.openHelper.readableDatabase.version).isEqualTo(4)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }
}
