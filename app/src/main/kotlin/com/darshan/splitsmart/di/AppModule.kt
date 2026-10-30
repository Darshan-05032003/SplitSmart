package com.darshan.splitsmart.di

import android.content.Context
import androidx.room.Room
import com.darshan.splitsmart.core.DefaultDispatcherProvider
import com.darshan.splitsmart.core.DispatcherProvider
import com.darshan.splitsmart.data.local.BillDao
import com.darshan.splitsmart.data.local.GroupDao
import com.darshan.splitsmart.data.local.MemberDao
import com.darshan.splitsmart.data.local.ReceiptDraftDao
import com.darshan.splitsmart.data.local.SplitSmartDatabase
import com.darshan.splitsmart.data.repository.BillRepositoryImpl
import com.darshan.splitsmart.data.repository.GroupRepositoryImpl
import com.darshan.splitsmart.data.repository.ReceiptDraftRepositoryImpl
import com.darshan.splitsmart.domain.repository.BillRepository
import com.darshan.splitsmart.domain.repository.GroupRepository
import com.darshan.splitsmart.domain.repository.ReceiptDraftRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun clock(): Clock = Clock.systemUTC()
    @Provides @Singleton fun dispatchers(): DispatcherProvider = DefaultDispatcherProvider()
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): SplitSmartDatabase =
        Room.databaseBuilder(context, SplitSmartDatabase::class.java, SplitSmartDatabase.NAME).build()

    @Provides fun groupDao(db: SplitSmartDatabase): GroupDao = db.groupDao()
    @Provides fun memberDao(db: SplitSmartDatabase): MemberDao = db.memberDao()
    @Provides fun billDao(db: SplitSmartDatabase): BillDao = db.billDao()
    @Provides fun receiptDraftDao(db: SplitSmartDatabase): ReceiptDraftDao = db.receiptDraftDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun groupRepository(impl: GroupRepositoryImpl): GroupRepository
    @Binds abstract fun billRepository(impl: BillRepositoryImpl): BillRepository
    @Binds abstract fun receiptDraftRepository(impl: ReceiptDraftRepositoryImpl): ReceiptDraftRepository
}
