package com.darshan.splitsmart.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [GroupEntity::class, MemberEntity::class, BillEntity::class, BillItemEntity::class, ReceiptDraftEntity::class, BillShareEntity::class, SettlementEntity::class],
    version = 5,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5)],
)
abstract class SplitSmartDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun memberDao(): MemberDao
    abstract fun billDao(): BillDao
    abstract fun receiptDraftDao(): ReceiptDraftDao
    abstract fun settlementDao(): SettlementDao
    abstract fun trashDao(): TrashDao

    companion object {
        const val NAME = "splitsmart.db"
    }
}
