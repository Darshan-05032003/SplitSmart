package com.darshan.splitsmart.data.repository

import com.darshan.splitsmart.core.DispatcherProvider
import com.darshan.splitsmart.data.local.GroupDao
import com.darshan.splitsmart.data.local.MemberDao
import com.darshan.splitsmart.data.local.toDomain
import com.darshan.splitsmart.data.local.toEntity
import com.darshan.splitsmart.domain.model.Group
import com.darshan.splitsmart.domain.model.GroupSummary
import com.darshan.splitsmart.domain.model.Member
import com.darshan.splitsmart.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepositoryImpl @Inject constructor(
    private val groupDao: GroupDao,
    private val memberDao: MemberDao,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) : GroupRepository {

    override fun observeGroupSummaries(): Flow<List<GroupSummary>> =
        groupDao.observeSummaries().map { rows -> rows.map { it.toDomain() } }.flowOn(dispatchers.io)

    override fun observeGroup(groupId: String): Flow<Group?> =
        groupDao.observeById(groupId).map { it?.toDomain() }.flowOn(dispatchers.io)

    override suspend fun createGroup(name: String): Group = withContext(dispatchers.io) {
        val group = Group(UUID.randomUUID().toString(), name, clock.instant())
        groupDao.insert(group.toEntity())
        group
    }

    override suspend fun renameGroup(groupId: String, name: String) = withContext(dispatchers.io) { groupDao.rename(groupId, name) }

    override suspend fun deleteGroup(groupId: String) = withContext(dispatchers.io) { groupDao.deleteById(groupId) }

    override fun observeMembers(groupId: String): Flow<List<Member>> =
        memberDao.observeByGroup(groupId).map { list -> list.map { it.toDomain() } }.flowOn(dispatchers.io)

    override suspend fun getMembers(groupId: String): List<Member> =
        withContext(dispatchers.io) { memberDao.getByGroup(groupId).map { it.toDomain() } }

    override suspend fun addMember(groupId: String, name: String, vpa: String?): Member = withContext(dispatchers.io) {
        val member = Member(UUID.randomUUID().toString(), groupId, name, vpa, clock.instant())
        memberDao.insert(member.toEntity())
        member
    }

    override suspend fun updateMember(member: Member) = withContext(dispatchers.io) { memberDao.update(member.toEntity()) }

    override suspend fun removeMember(memberId: String) = withContext(dispatchers.io) { memberDao.deleteById(memberId) }
}
