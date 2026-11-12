package com.darshan.splitsmart.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.darshan.splitsmart.domain.repository.RemovedRows

/** Everything deleting a group takes with it: the foreign keys cascade to all of these. */
data class GroupRows(
    val group: GroupEntity,
    val members: List<MemberEntity>,
    val bills: List<BillEntity>,
    val items: List<BillItemEntity>,
    val shares: List<BillShareEntity>,
    val settlements: List<SettlementEntity>,
    val drafts: List<ReceiptDraftEntity>,
) : RemovedRows

/** Everything deleting a bill takes with it. */
data class BillRows(val bill: BillEntity, val items: List<BillItemEntity>, val shares: List<BillShareEntity>) : RemovedRows

/**
 * Deletes that can be undone. Each take reads the rows the cascade would remove and deletes in the same transaction, so the
 * copy is exactly what was deleted; each put-back inserts parents before children and aborts on any conflict or missing
 * parent, which rolls the whole put-back back.
 */
@Dao
interface TrashDao {
    @Query("SELECT * FROM split_groups WHERE id = :groupId")
    suspend fun group(groupId: String): GroupEntity?

    @Query("SELECT * FROM group_members WHERE group_id = :groupId")
    suspend fun membersOf(groupId: String): List<MemberEntity>

    @Query("SELECT * FROM bills WHERE group_id = :groupId")
    suspend fun billsOf(groupId: String): List<BillEntity>

    @Query("SELECT * FROM bills WHERE id = :billId")
    suspend fun bill(billId: String): BillEntity?

    @Query("SELECT i.* FROM bill_items i JOIN bills b ON b.id = i.bill_id WHERE b.group_id = :groupId")
    suspend fun itemsOfGroup(groupId: String): List<BillItemEntity>

    @Query("SELECT * FROM bill_items WHERE bill_id = :billId")
    suspend fun itemsOfBill(billId: String): List<BillItemEntity>

    @Query("SELECT s.* FROM bill_shares s JOIN bills b ON b.id = s.bill_id WHERE b.group_id = :groupId")
    suspend fun sharesOfGroup(groupId: String): List<BillShareEntity>

    @Query("SELECT * FROM bill_shares WHERE bill_id = :billId")
    suspend fun sharesOfBill(billId: String): List<BillShareEntity>

    @Query("SELECT * FROM settlements WHERE group_id = :groupId")
    suspend fun settlementsOf(groupId: String): List<SettlementEntity>

    @Query("SELECT * FROM receipt_drafts WHERE group_id = :groupId")
    suspend fun draftsOf(groupId: String): List<ReceiptDraftEntity>

    @Query("DELETE FROM split_groups WHERE id = :groupId")
    suspend fun deleteGroup(groupId: String): Int

    @Query("DELETE FROM bills WHERE id = :billId")
    suspend fun deleteBill(billId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGroup(group: GroupEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMembers(members: List<MemberEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBills(bills: List<BillEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItems(items: List<BillItemEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertShares(shares: List<BillShareEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSettlements(settlements: List<SettlementEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDrafts(drafts: List<ReceiptDraftEntity>)

    @Transaction
    suspend fun takeGroup(groupId: String): GroupRows? {
        val group = group(groupId) ?: return null
        val rows = GroupRows(
            group = group,
            members = membersOf(groupId),
            bills = billsOf(groupId),
            items = itemsOfGroup(groupId),
            shares = sharesOfGroup(groupId),
            settlements = settlementsOf(groupId),
            drafts = draftsOf(groupId),
        )
        deleteGroup(groupId)
        return rows
    }

    @Transaction
    suspend fun putBackGroup(rows: GroupRows) {
        insertGroup(rows.group)
        insertMembers(rows.members)
        insertBills(rows.bills)
        insertItems(rows.items)
        insertShares(rows.shares)
        insertSettlements(rows.settlements)
        insertDrafts(rows.drafts)
    }

    @Transaction
    suspend fun takeBill(billId: String): BillRows? {
        val bill = bill(billId) ?: return null
        val rows = BillRows(bill, itemsOfBill(billId), sharesOfBill(billId))
        deleteBill(billId)
        return rows
    }

    @Transaction
    suspend fun putBackBill(rows: BillRows) {
        insertBills(listOf(rows.bill))
        insertItems(rows.items)
        insertShares(rows.shares)
    }
}
