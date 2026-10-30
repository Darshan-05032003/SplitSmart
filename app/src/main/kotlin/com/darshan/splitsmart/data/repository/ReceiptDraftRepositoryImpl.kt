package com.darshan.splitsmart.data.repository

import com.darshan.splitsmart.core.DispatcherProvider
import com.darshan.splitsmart.data.local.ReceiptDraftDao
import com.darshan.splitsmart.data.local.toDomain
import com.darshan.splitsmart.data.local.toEntity
import com.darshan.splitsmart.domain.model.ReceiptDraft
import com.darshan.splitsmart.domain.repository.ReceiptDraftRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReceiptDraftRepositoryImpl @Inject constructor(
    private val dao: ReceiptDraftDao,
    private val dispatchers: DispatcherProvider,
) : ReceiptDraftRepository {
    override suspend fun save(draft: ReceiptDraft) = withContext(dispatchers.io) { dao.upsert(draft.toEntity()) }
    override suspend fun get(id: String): ReceiptDraft? = withContext(dispatchers.io) { dao.getById(id)?.toDomain() }
    override fun observe(id: String): Flow<ReceiptDraft?> = dao.observeById(id).map { it?.toDomain() }.flowOn(dispatchers.io)
    override suspend fun delete(id: String) = withContext(dispatchers.io) { dao.deleteById(id) }
    override suspend fun deleteOlderThan(cutoff: Instant): Int = withContext(dispatchers.io) { dao.deleteOlderThan(cutoff.toEpochMilli()) }
}
