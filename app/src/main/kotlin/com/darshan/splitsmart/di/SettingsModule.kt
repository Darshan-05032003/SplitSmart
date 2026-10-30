package com.darshan.splitsmart.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.darshan.splitsmart.data.llm.DataStoreModelCache
import com.darshan.splitsmart.data.llm.ModelCache
import com.darshan.splitsmart.data.settings.DataStoreProfileRepository
import com.darshan.splitsmart.domain.repository.ProfileRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** One preferences DataStore (`splitsmart_settings`) for small app settings. Replaced by in-memory fakes in Hilt tests. */
@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {
    @Provides
    @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("splitsmart_settings") })

    @Provides @Singleton fun modelCache(dataStore: DataStore<Preferences>): ModelCache = DataStoreModelCache(dataStore)
    @Provides @Singleton fun profileRepository(dataStore: DataStore<Preferences>): ProfileRepository = DataStoreProfileRepository(dataStore)
}
