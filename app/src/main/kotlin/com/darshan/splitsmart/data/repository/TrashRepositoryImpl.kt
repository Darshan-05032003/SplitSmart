package com.darshan.splitsmart.data.repository

import android.database.SQLException
import com.darshan.splitsmart.core.DispatcherProvider
import com.darshan.splitsmart.data.local.BillRows
import com.darshan.splitsmart.data.local.GroupRows
import com.darshan.splitsmart.data.local.TrashDao
import com.darshan.splitsmart.domain.repository.Removal
import com.darshan.splitsmart.domain.repository.TrashRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrashRepositoryImpl @Inject constructor(
    private val dao: TrashDao,
    private val dispatchers: DispatcherProvider,
) : TrashRepository {

    override suspend fun removeGroup(groupId: String): Removal.Group? = withContext(dispatchers.io) {
        dao.takeGroup(groupId)?.let { Removal.Group(groupId, it.group.name, it) }
    }

    override suspend fun removeBill(billId: String): Removal.Bill? = withContext(dispatchers.io) {
        dao.takeBill(billId)?.let { Removal.Bill(billId, it.bill.groupId, it.bill.title, it) }
    }

    override suspend fun restore(removal: Removal): Boolean = withContext(dispatchers.io) {
        try {
            when (val rows = removal.rows) {
                is GroupRows -> dao.putBackGroup(rows)
                is BillRows -> dao.putBackBill(rows)
                else -> return@withContext false
            }
            true
        } catch (e: SQLException) {
            // A constraint failed (missing parent, same id): the transaction rolled back and nothing was written.
            false
        }
    }
}
