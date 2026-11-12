package com.darshan.splitsmart.testutil

import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.Group
import com.darshan.splitsmart.domain.model.GroupSummary
import com.darshan.splitsmart.domain.model.Member
import com.darshan.splitsmart.domain.repository.BillRepository
import com.darshan.splitsmart.domain.repository.GroupRepository
import com.darshan.splitsmart.domain.repository.Removal
import com.darshan.splitsmart.domain.repository.RemovedRows
import com.darshan.splitsmart.domain.repository.TrashRepository
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

    override suspend fun setArchived(groupId: String, archived: Boolean) {
        maybeFail()
        groups.update { list -> list.map { if (it.id == groupId) it.copy(archivedAt = if (archived) clock.instant() else null) else it } }
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

/** In-memory settled payments; [failWith] makes every write throw. */
open class FakeSettlementRepository(private val clock: Clock = fixedClock) : com.darshan.splitsmart.domain.repository.SettlementRepository {
    val settlements = MutableStateFlow<List<com.darshan.splitsmart.domain.model.Settlement>>(emptyList())
    var failWith: Throwable? = null
    var recordCalls = 0

    override fun observeSettlements(groupId: String): Flow<List<com.darshan.splitsmart.domain.model.Settlement>> =
        settlements.map { list -> list.filter { it.groupId == groupId }.sortedByDescending { it.createdAt } }

    override fun observeAllSettlements(): Flow<List<com.darshan.splitsmart.domain.model.Settlement>> =
        settlements.map { list -> list.sortedByDescending { it.createdAt } }

    override suspend fun record(
        from: Member,
        to: Member,
        amountPaise: Long,
        method: com.darshan.splitsmart.domain.model.SettlementMethod,
    ): com.darshan.splitsmart.domain.model.Settlement {
        recordCalls++
        failWith?.let { throw it }
        val s = com.darshan.splitsmart.domain.model.Settlement(UUID.randomUUID().toString(), from.groupId, from.id, to.id, from.name, to.name, amountPaise, method, clock.instant())
        settlements.update { it + s }
        return s
    }

    override suspend fun delete(settlementId: String) {
        failWith?.let { throw it }
        settlements.update { list -> list.filterNot { it.id == settlementId } }
    }
}

/**
 * Undoable deletes over the fakes: deletes through them (so their counters and [FakeGroupRepository.failWith] apply) and keeps
 * what it took; [restore] puts it back unless [restoreFails].
 */
class FakeTrashRepository(
    private val groups: FakeGroupRepository = FakeGroupRepository(),
    private val bills: FakeBillRepository = FakeBillRepository(),
) : TrashRepository {
    data class Rows(val group: Group?, val members: List<Member>, val bills: List<Bill>) : RemovedRows

    var restoreFails = false
    val restores = mutableListOf<Removal>()

    override suspend fun removeGroup(groupId: String): Removal.Group? {
        val group = groups.groups.value.find { it.id == groupId }
        val rows = Rows(group, groups.members.value.filter { it.groupId == groupId }, bills.bills.value.filter { it.groupId == groupId })
        groups.deleteGroup(groupId)
        bills.bills.update { list -> list.filterNot { it.groupId == groupId } }
        return group?.let { Removal.Group(groupId, it.name, rows) }
    }

    override suspend fun removeBill(billId: String): Removal.Bill? {
        val bill = bills.bills.value.find { it.id == billId }
        bills.deleteBill(billId)
        return bill?.let { Removal.Bill(billId, it.groupId, it.title, Rows(null, emptyList(), listOf(it))) }
    }

    override suspend fun restore(removal: Removal): Boolean {
        restores += removal
        if (restoreFails) return false
        val rows = removal.rows as Rows
        rows.group?.let { g -> groups.groups.update { it + g } }
        groups.members.update { it + rows.members }
        bills.bills.update { it + rows.bills }
        return true
    }
}
