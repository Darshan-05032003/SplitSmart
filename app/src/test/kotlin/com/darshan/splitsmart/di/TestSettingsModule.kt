package com.darshan.splitsmart.di

import com.darshan.splitsmart.data.llm.InMemoryModelCache
import com.darshan.splitsmart.data.llm.ModelCache
import com.darshan.splitsmart.data.settings.InMemoryProfileRepository
import com.darshan.splitsmart.domain.repository.ProfileRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/** In-memory settings for Hilt Robolectric tests (a real DataStore file cannot be opened twice in one process). */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [SettingsModule::class])
object TestSettingsModule {
    @Provides @Singleton fun modelCache(): ModelCache = InMemoryModelCache()
    @Provides @Singleton fun profileRepository(): ProfileRepository = InMemoryProfileRepository()
}
