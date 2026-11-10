package com.darshan.splitsmart.di

import com.darshan.splitsmart.data.llm.InMemoryModelCache
import com.darshan.splitsmart.data.llm.ModelCache
import com.darshan.splitsmart.data.settings.InMemoryOnboardingRepository
import com.darshan.splitsmart.data.settings.InMemoryProfileRepository
import com.darshan.splitsmart.domain.repository.OnboardingRepository
import com.darshan.splitsmart.domain.repository.ProfileRepository
import com.darshan.splitsmart.ui.designsystem.effects.InMemoryUiPreferences
import com.darshan.splitsmart.ui.designsystem.effects.UiPreferences
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
    @Provides @Singleton fun uiPreferences(): UiPreferences = InMemoryUiPreferences()

    /** Done by default, so app-level tests land on the groups list; onboarding tests set it back to false before launching. */
    @Provides @Singleton fun onboardingRepository(): OnboardingRepository = InMemoryOnboardingRepository(completed = true)
}
