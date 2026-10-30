package com.darshan.splitsmart.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.BillItem
import com.darshan.splitsmart.domain.model.BillSource
import com.darshan.splitsmart.domain.model.BillStatus
import com.darshan.splitsmart.domain.model.Group
import com.darshan.splitsmart.domain.model.GroupSummary
import com.darshan.splitsmart.domain.model.Member
import java.time.Instant

@Entity(tableName = "split_groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "group_members",
    foreignKeys = [ForeignKey(entity = GroupEntity::class, parentColumns = ["id"], childColumns = ["group_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("group_id")],
)
data class MemberEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    val name: String,
    val vpa: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "bills",
    foreignKeys = [
        ForeignKey(entity = GroupEntity::class, parentColumns = ["id"], childColumns = ["group_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MemberEntity::class, parentColumns = ["id"], childColumns = ["paid_by_member_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("group_id", "created_at"), Index("paid_by_member_id"), Index("created_at")],
)
data class BillEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    val title: String,
    @ColumnInfo(name = "total_paise") val totalPaise: Long,
    @ColumnInfo(name = "tax_paise") val taxPaise: Long,
    @ColumnInfo(name = "tip_paise") val tipPaise: Long,
    @ColumnInfo(name = "paid_by_member_id") val paidByMemberId: String?,
    val source: String,
    val status: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "bill_items",
    foreignKeys = [ForeignKey(entity = BillEntity::class, parentColumns = ["id"], childColumns = ["bill_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bill_id")],
)
data class BillItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "bill_id") val billId: String,
    val name: String,
    val qty: Int,
    @ColumnInfo(name = "unit_paise") val unitPaise: Long,
    @ColumnInfo(name = "total_paise") val totalPaise: Long,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

data class BillWithItems(
    @Embedded val bill: BillEntity,
    @Relation(parentColumn = "id", entityColumn = "bill_id") val rawItems: List<BillItemEntity>,
) {
    /** Room cannot order a @Relation, so the receipt order is restored here. */
    val items: List<BillItemEntity> get() = rawItems.sortedBy { it.sortOrder }
}

data class GroupSummaryRow(
    @Embedded val group: GroupEntity,
    @ColumnInfo(name = "member_count") val memberCount: Int,
    @ColumnInfo(name = "bill_count") val billCount: Int,
)

// ----- mappers -----

fun Group.toEntity() = GroupEntity(id, name, createdAt.toEpochMilli())
fun GroupEntity.toDomain() = Group(id, name, Instant.ofEpochMilli(createdAt))
fun GroupSummaryRow.toDomain() = GroupSummary(group.toDomain(), memberCount, billCount)

fun Member.toEntity() = MemberEntity(id, groupId, name, vpa, createdAt.toEpochMilli())
fun MemberEntity.toDomain() = Member(id, groupId, name, vpa, Instant.ofEpochMilli(createdAt))

fun BillItem.toEntity() = BillItemEntity(id, billId, name, qty, unitPaise, totalPaise, sortOrder)
fun BillItemEntity.toDomain() = BillItem(id, billId, name, qty, unitPaise, totalPaise, sortOrder)

fun Bill.toEntity() = BillEntity(
    id = id, groupId = groupId, title = title, totalPaise = totalPaise, taxPaise = taxPaise, tipPaise = tipPaise,
    paidByMemberId = paidByMemberId, source = source.name, status = status.name, createdAt = createdAt.toEpochMilli(),
)

fun BillWithItems.toDomain() = Bill(
    id = bill.id, groupId = bill.groupId, title = bill.title, totalPaise = bill.totalPaise, taxPaise = bill.taxPaise,
    tipPaise = bill.tipPaise, paidByMemberId = bill.paidByMemberId,
    source = runCatching { BillSource.valueOf(bill.source) }.getOrDefault(BillSource.MANUAL),
    status = runCatching { BillStatus.valueOf(bill.status) }.getOrDefault(BillStatus.DRAFT),
    createdAt = Instant.ofEpochMilli(bill.createdAt),
    items = items.map { it.toDomain() },
)
