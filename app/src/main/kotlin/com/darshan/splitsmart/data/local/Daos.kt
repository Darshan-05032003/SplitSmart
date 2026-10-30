package com.darshan.splitsmart.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(group: GroupEntity)

    @Query("UPDATE split_groups SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("DELETE FROM split_groups WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM split_groups WHERE id = :id")
    fun observeById(id: String): Flow<GroupEntity?>

    @Query("SELECT * FROM split_groups WHERE id = :id")
    suspend fun getById(id: String): GroupEntity?

    @Query(
        """
        SELECT g.*,
               (SELECT COUNT(*) FROM group_members m WHERE m.group_id = g.id) AS member_count,
               (SELECT COUNT(*) FROM bills b WHERE b.group_id = g.id) AS bill_count
        FROM split_groups g
        ORDER BY g.created_at DESC, g.id DESC
        """,
    )
    fun observeSummaries(): Flow<List<GroupSummaryRow>>

    @Query("SELECT COUNT(*) FROM split_groups")
    suspend fun count(): Int
}

@Dao
interface MemberDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(member: MemberEntity)

    @Update
    suspend fun update(member: MemberEntity)

    @Query("DELETE FROM group_members WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM group_members WHERE group_id = :groupId ORDER BY created_at ASC, id ASC")
    fun observeByGroup(groupId: String): Flow<List<MemberEntity>>

    @Query("SELECT * FROM group_members WHERE group_id = :groupId ORDER BY created_at ASC, id ASC")
    suspend fun getByGroup(groupId: String): List<MemberEntity>

    @Query("SELECT COUNT(*) FROM group_members WHERE group_id = :groupId")
    suspend fun countByGroup(groupId: String): Int
}

@Dao
interface BillDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBill(bill: BillEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<BillItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBills(bills: List<BillEntity>)

    @Query("DELETE FROM bill_items WHERE bill_id = :billId")
    suspend fun deleteItemsForBill(billId: String)

    /** Replaces the bill and all of its items atomically. */
    @Transaction
    suspend fun upsertWithItems(bill: BillEntity, items: List<BillItemEntity>) {
        insertBill(bill)
        deleteItemsForBill(bill.id)
        if (items.isNotEmpty()) insertItems(items)
    }

    /** Bulk path used by the stress tests. */
    @Transaction
    suspend fun insertBillsAndItems(bills: List<BillEntity>, items: List<BillItemEntity>) {
        insertBills(bills)
        insertItems(items)
    }

    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun deleteById(id: String)

    @Transaction
    @Query("SELECT * FROM bills WHERE group_id = :groupId ORDER BY created_at DESC, id DESC")
    fun observeByGroup(groupId: String): Flow<List<BillWithItems>>

    @Transaction
    @Query("SELECT * FROM bills WHERE group_id = :groupId ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset")
    suspend fun getPage(groupId: String, limit: Int, offset: Int): List<BillWithItems>

    @Transaction
    @Query("SELECT * FROM bills ORDER BY created_at DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<BillWithItems>>

    @Transaction
    @Query("SELECT * FROM bills WHERE id = :id")
    fun observeById(id: String): Flow<BillWithItems?>

    @Transaction
    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun getById(id: String): BillWithItems?

    @Query("SELECT COUNT(*) FROM bills")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM bill_items")
    suspend fun countItems(): Int
}
