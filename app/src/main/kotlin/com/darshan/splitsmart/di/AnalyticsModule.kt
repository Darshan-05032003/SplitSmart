package com.darshan.splitsmart.di

import android.content.Context
import com.darshan.splitsmart.BuildConfig
import com.darshan.splitsmart.data.analytics.FirebaseAnalyticsTracker
import com.darshan.splitsmart.data.analytics.FirebaseCrashReporter
import com.darshan.splitsmart.domain.analytics.AnalyticsTracker
import com.darshan.splitsmart.domain.analytics.CrashReporter
import com.darshan.splitsmart.domain.analytics.NoOpAnalyticsTracker
import com.darshan.splitsmart.domain.analytics.NoOpCrashReporter
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Real Firebase only when `app/google-services.json` was present at build time (`BuildConfig.FIREBASE_CONFIGURED`);
 * a clean clone without it gets the no-op implementations and still builds and runs.
 */
@Module
@InstallIn(SingletonComponent::class)
object AnalyticsModule {
    @Provides
    @Singleton
    fun analyticsTracker(@ApplicationContext context: Context): AnalyticsTracker =
        if (BuildConfig.FIREBASE_CONFIGURED) FirebaseAnalyticsTracker(FirebaseAnalytics.getInstance(context)) else NoOpAnalyticsTracker

    @Provides
    @Singleton
    fun crashReporter(): CrashReporter =
        if (BuildConfig.FIREBASE_CONFIGURED) FirebaseCrashReporter(FirebaseCrashlytics.getInstance()) else NoOpCrashReporter
}
