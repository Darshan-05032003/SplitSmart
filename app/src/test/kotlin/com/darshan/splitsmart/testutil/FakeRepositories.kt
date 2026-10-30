package com.darshan.splitsmart.testutil

import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.Group
import com.darshan.splitsmart.domain.model.GroupSummary
import com.darshan.splitsmart.domain.model.Member
import com.darshan.splitsmart.domain.repository.BillRepository
import com.darshan.splitsmart.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.util.UUID

/** In-memory repository for ViewModel tests. Set [failWith] to make every suspend call throw. */
open class FakeGroupRepository(private val clock: Clock = fixedClock) : GroupRepository {
    val groups = MutableStateFlow<List<Group>>(emptyList())
    val members = MutableStateFlow<List<Member>>(emptyList())
    var failWith: Throwable? = null
    var billCounts: Map<String, Int> = emptyMap()

    private fun maybeFail() { failWith?.let { throw it } }

    override fun observeGroupSummaries(): Flow<List<GroupSummary>> = groups.map { list ->
        list.sortedByDescending { it.createdAt }.map { g ->
            GroupSummary(g, memberCount = members.value.count { it.groupId == g.id }, billCount = billCounts[g.id] ?: 0)
        }
    }

    override fun observeGroup(groupId: String): Flow<Group?> = groups.map { list -> list.find { it.id == groupId } }

    override suspend fun createGroup(name: String): Group {
        maybeFail()
        val g = Group(UUID.randomUUID().toString(), name, clock.instant())
        groups.update { it + g }
        return g
    }

    override suspend fun renameGroup(groupId: String, name: String) {
        maybeFail()
        groups.update { list -> list.map { if (it.id == groupId) it.copy(name = name) else it } }
    }

    override suspend fun deleteGroup(groupId: String) {
        maybeFail()
        groups.update { list -> list.filterNot { it.id == groupId } }
        members.update { list -> list.filterNot { it.groupId == groupId } }
    }

    override fun observeMembers(groupId: String): Flow<List<Member>> =
        members.map { list -> list.filter { it.groupId == groupId }.sortedBy { it.createdAt } }

    override fun observeAllMembers(): Flow<List<Member>> = members.map { list -> list.sortedBy { it.createdAt } }

    override suspend fun getMembers(groupId: String): List<Member> = members.value.filter { it.groupId == groupId }

    override suspend fun addMember(groupId: String, name: String, vpa: String?): Member {
        maybeFail()
        val m = Member(UUID.randomUUID().toString(), groupId, name, vpa, clock.instant())
        members.update { it + m }
        return m
    }

    override suspend fun updateMember(member: Member) {
        maybeFail()
        members.update { list -> list.map { if (it.id == member.id) member else it } }
    }

    override suspend fun removeMember(memberId: String) {
        maybeFail()
        members.update { list -> list.filterNot { it.id == memberId } }
    }
}

open class FakeBillRepository : BillRepository {
    val bills = MutableStateFlow<List<Bill>>(emptyList())
    var failWith: Throwable? = null

    private fun maybeFail() { failWith?.let { throw it } }

    override fun observeBills(groupId: String): Flow<List<Bill>> =
        bills.map { list -> list.filter { it.groupId == groupId }.sortedByDescending { it.createdAt } }

    override fun observeRecentBills(limit: Int): Flow<List<Bill>> =
        bills.map { list -> list.sortedByDescending { it.createdAt }.take(limit) }

    override fun observeAllBills(): Flow<List<Bill>> = bills.map { list -> list.sortedByDescending { it.createdAt } }

    override fun observeBill(billId: String): Flow<Bill?> = bills.map { list -> list.find { it.id == billId } }

    override suspend fun getBill(billId: String): Bill? = bills.value.find { it.id == billId }

    override suspend fun saveBill(bill: Bill): Bill {
        maybeFail()
        bills.update { list -> list.filterNot { it.id == bill.id } + bill }
        return bill
    }

    override suspend fun deleteBill(billId: String) {
        maybeFail()
        bills.update { list -> list.filterNot { it.id == billId } }
    }
}
