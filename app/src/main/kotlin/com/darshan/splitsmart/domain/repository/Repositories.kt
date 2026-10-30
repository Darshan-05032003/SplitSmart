package com.darshan.splitsmart.domain.repository

import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.Group
import com.darshan.splitsmart.domain.model.GroupSummary
import com.darshan.splitsmart.domain.model.Member
import kotlinx.coroutines.flow.Flow

/** Offline-first: Room is the single source of truth; every read is a Flow that updates as the data changes. */
interface GroupRepository {
    fun observeGroupSummaries(): Flow<List<GroupSummary>>
    fun observeGroup(groupId: String): Flow<Group?>
    suspend fun createGroup(name: String): Group
    suspend fun renameGroup(groupId: String, name: String)
    suspend fun deleteGroup(groupId: String)
    fun observeMembers(groupId: String): Flow<List<Member>>
    /** Every member of every group (home screen avatars and balances). */
    fun observeAllMembers(): Flow<List<Member>>
    suspend fun getMembers(groupId: String): List<Member>
    suspend fun addMember(groupId: String, name: String, vpa: String?): Member
    suspend fun updateMember(member: Member)
    suspend fun removeMember(memberId: String)
}

interface BillRepository {
    fun observeBills(groupId: String): Flow<List<Bill>>
    fun observeRecentBills(limit: Int): Flow<List<Bill>>
    /** Every bill of every group, newest first (home screen balances and last activity). */
    fun observeAllBills(): Flow<List<Bill>>
    fun observeBill(billId: String): Flow<Bill?>
    suspend fun getBill(billId: String): Bill?
    /** Upserts the bill and replaces its items in one transaction. */
    suspend fun saveBill(bill: Bill): Bill
    suspend fun deleteBill(billId: String)
}
