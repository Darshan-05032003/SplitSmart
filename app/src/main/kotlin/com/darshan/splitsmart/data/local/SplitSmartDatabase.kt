package com.darshan.splitsmart.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [GroupEntity::class, MemberEntity::class, BillEntity::class, BillItemEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SplitSmartDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun memberDao(): MemberDao
    abstract fun billDao(): BillDao

    companion object {
        const val NAME = "splitsmart.db"
    }
}
