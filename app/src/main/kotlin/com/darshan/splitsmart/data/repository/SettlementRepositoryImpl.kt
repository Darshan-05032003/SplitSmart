package com.darshan.splitsmart.data.repository

import com.darshan.splitsmart.core.DispatcherProvider
import com.darshan.splitsmart.data.local.SettlementDao
import com.darshan.splitsmart.data.local.SettlementEntity
import com.darshan.splitsmart.data.local.toDomain
import com.darshan.splitsmart.data.local.toEntity
import com.darshan.splitsmart.domain.model.Member
import com.darshan.splitsmart.domain.model.Settlement
import com.darshan.splitsmart.domain.model.SettlementMethod
import com.darshan.splitsmart.domain.repository.SettlementRepository
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class SettlementRepositoryImpl @Inject constructor(
    private val dao: SettlementDao,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) : SettlementRepository {

    override fun observeSettlements(groupId: String): Flow<List<Settlement>> =
        dao.observeByGroup(groupId).map { list -> list.mapNotNull { it.toDomainOrNull() } }.flowOn(dispatchers.io)

    override fun observeAllSettlements(): Flow<List<Settlement>> =
        dao.observeAll().map { list -> list.mapNotNull { it.toDomainOrNull() } }.flowOn(dispatchers.io)

    override suspend fun record(from: Member, to: Member, amountPaise: Long, method: SettlementMethod): Settlement {
        require(from.groupId == to.groupId) { "members of different groups" }
        val settlement = Settlement(UUID.randomUUID().toString(), from.groupId, from.id, to.id, from.name, to.name, amountPaise, method, clock.instant())
        withContext(dispatchers.io) { dao.insert(settlement.toEntity()) }
        return settlement
    }

    override suspend fun delete(settlementId: String) = withContext(dispatchers.io) { dao.deleteById(settlementId) }

    /** A corrupt row (a hand-edited database) is skipped instead of taking the whole history down. */
    private fun SettlementEntity.toDomainOrNull(): Settlement? = runCatching { toDomain() }.getOrNull()
}
