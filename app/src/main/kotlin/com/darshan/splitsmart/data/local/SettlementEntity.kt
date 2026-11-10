package com.darshan.splitsmart.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.darshan.splitsmart.domain.model.Settlement
import com.darshan.splitsmart.domain.model.SettlementMethod
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * DB v5: a payment someone marked as made. Deleting the group deletes its payments; removing a member keeps the payment for the
 * history (the names are copied in) and clears that side's id, which takes it out of the balances.
 */
@Entity(
    tableName = "settlements",
    foreignKeys = [
        ForeignKey(entity = GroupEntity::class, parentColumns = ["id"], childColumns = ["group_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MemberEntity::class, parentColumns = ["id"], childColumns = ["from_member_id"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = MemberEntity::class, parentColumns = ["id"], childColumns = ["to_member_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("group_id", "created_at"), Index("from_member_id"), Index("to_member_id")],
)
data class SettlementEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "from_member_id") val fromMemberId: String?,
    @ColumnInfo(name = "to_member_id") val toMemberId: String?,
    @ColumnInfo(name = "from_name") val fromName: String,
    @ColumnInfo(name = "to_name") val toName: String,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    val method: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Dao
interface SettlementDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(settlement: SettlementEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(settlements: List<SettlementEntity>)

    @Query("DELETE FROM settlements WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Newest first, like the bills. */
    @Query("SELECT * FROM settlements WHERE group_id = :groupId ORDER BY created_at DESC, id DESC")
    fun observeByGroup(groupId: String): Flow<List<SettlementEntity>>

    @Query("SELECT * FROM settlements ORDER BY created_at DESC, id DESC")
    fun observeAll(): Flow<List<SettlementEntity>>

    @Query("SELECT COUNT(*) FROM settlements")
    suspend fun count(): Int
}

fun Settlement.toEntity() = SettlementEntity(id, groupId, fromMemberId, toMemberId, fromName, toName, amountPaise, method.name, createdAt.toEpochMilli())

fun SettlementEntity.toDomain() = Settlement(
    id = id, groupId = groupId, fromMemberId = fromMemberId, toMemberId = toMemberId, fromName = fromName, toName = toName,
    amountPaise = amountPaise,
    method = runCatching { SettlementMethod.valueOf(method) }.getOrDefault(SettlementMethod.OTHER),
    createdAt = Instant.ofEpochMilli(createdAt),
)
