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
import com.darshan.splitsmart.domain.model.ReceiptDraft
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Entity(
    tableName = "receipt_drafts",
    foreignKeys = [ForeignKey(entity = GroupEntity::class, parentColumns = ["id"], childColumns = ["group_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("group_id"), Index("created_at")],
)
data class ReceiptDraftEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "group_id") val groupId: String,
    @ColumnInfo(name = "image_path") val imagePath: String?,
    @ColumnInfo(name = "ocr_text") val ocrText: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

fun ReceiptDraft.toEntity() = ReceiptDraftEntity(id, groupId, imagePath, ocrText, createdAt.toEpochMilli())
fun ReceiptDraftEntity.toDomain() = ReceiptDraft(id, groupId, imagePath, ocrText, Instant.ofEpochMilli(createdAt))

@Dao
interface ReceiptDraftDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: ReceiptDraftEntity)

    @Query("SELECT * FROM receipt_drafts WHERE id = :id")
    suspend fun getById(id: String): ReceiptDraftEntity?

    @Query("SELECT * FROM receipt_drafts WHERE id = :id")
    fun observeById(id: String): Flow<ReceiptDraftEntity?>

    @Query("DELETE FROM receipt_drafts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM receipt_drafts WHERE created_at < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int
}
