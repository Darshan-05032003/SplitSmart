package com.darshan.splitsmart.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [GroupEntity::class, MemberEntity::class, BillEntity::class, BillItemEntity::class, ReceiptDraftEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class SplitSmartDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun memberDao(): MemberDao
    abstract fun billDao(): BillDao
    abstract fun receiptDraftDao(): ReceiptDraftDao

    companion object {
        const val NAME = "splitsmart.db"
    }
}
