package com.darshan.splitsmart

import android.app.Application
import com.darshan.splitsmart.core.perf.PerformanceSetup
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SplitSmartApp : Application() {
    override fun onCreate() {
        // StrictMode before anything else so the first disk read on the main thread is already caught (debug only).
        if (PerformanceSetup.shouldInstallStrictMode(BuildConfig.DEBUG)) PerformanceSetup.installStrictMode()
        super.onCreate()
    }
}
