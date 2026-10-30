package com.darshan.splitsmart.data.repository

import com.darshan.splitsmart.core.DispatcherProvider
import com.darshan.splitsmart.data.local.BillDao
import com.darshan.splitsmart.data.local.toDomain
import com.darshan.splitsmart.data.local.shareEntities
import com.darshan.splitsmart.data.local.toEntity
import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.repository.BillRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillRepositoryImpl @Inject constructor(
    private val billDao: BillDao,
    private val dispatchers: DispatcherProvider,
) : BillRepository {

    override fun observeBills(groupId: String): Flow<List<Bill>> =
        billDao.observeByGroup(groupId).map { list -> list.mapNotNull { it.toDomainOrNull() } }.flowOn(dispatchers.io)

    override fun observeRecentBills(limit: Int): Flow<List<Bill>> =
        billDao.observeRecent(limit).map { list -> list.mapNotNull { it.toDomainOrNull() } }.flowOn(dispatchers.io)

    override fun observeBill(billId: String): Flow<Bill?> =
        billDao.observeById(billId).map { it?.toDomainOrNull() }.flowOn(dispatchers.io)

    override suspend fun getBill(billId: String): Bill? = withContext(dispatchers.io) { billDao.getById(billId)?.toDomainOrNull() }

    override suspend fun saveBill(bill: Bill): Bill = withContext(dispatchers.io) {
        billDao.upsertWithItems(bill.toEntity(), bill.items.map { it.copy(billId = bill.id).toEntity() }, bill.shareEntities())
        bill
    }

    override suspend fun deleteBill(billId: String) = withContext(dispatchers.io) { billDao.deleteById(billId) }

    /** A corrupt row (e.g. hand-edited database) is skipped instead of crashing the whole list. */
    private fun com.darshan.splitsmart.data.local.BillWithItems.toDomainOrNull(): Bill? = runCatching { toDomain() }.getOrNull()
}
