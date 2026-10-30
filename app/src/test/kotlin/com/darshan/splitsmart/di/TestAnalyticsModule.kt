package com.darshan.splitsmart.di

import com.darshan.splitsmart.domain.analytics.AnalyticsTracker
import com.darshan.splitsmart.domain.analytics.CrashReporter
import com.darshan.splitsmart.testutil.RecordingAnalyticsTracker
import com.darshan.splitsmart.testutil.RecordingCrashReporter
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/** Hilt Robolectric tests never touch Firebase: recording fakes replace the production bindings. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AnalyticsModule::class])
object TestAnalyticsModule {
    @Provides @Singleton fun tracker(): RecordingAnalyticsTracker = RecordingAnalyticsTracker()
    @Provides @Singleton fun reporter(): RecordingCrashReporter = RecordingCrashReporter()
    @Provides fun analyticsTracker(impl: RecordingAnalyticsTracker): AnalyticsTracker = impl
    @Provides fun crashReporter(impl: RecordingCrashReporter): CrashReporter = impl
}
