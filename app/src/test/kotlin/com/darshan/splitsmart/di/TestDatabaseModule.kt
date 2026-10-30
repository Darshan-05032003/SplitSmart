package com.darshan.splitsmart.di

import android.content.Context
import androidx.room.Room
import com.darshan.splitsmart.data.local.SplitSmartDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/** Replaces the on-disk Room database with an in-memory one in Hilt Robolectric tests. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object TestDatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): SplitSmartDatabase =
        Room.inMemoryDatabaseBuilder(context, SplitSmartDatabase::class.java).build()

    @Provides fun groupDao(db: SplitSmartDatabase) = db.groupDao()
    @Provides fun memberDao(db: SplitSmartDatabase) = db.memberDao()
    @Provides fun billDao(db: SplitSmartDatabase) = db.billDao()
}
