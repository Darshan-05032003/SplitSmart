package com.darshan.splitsmart.core.perf

import android.os.StrictMode

/** What was turned on, so tests and logs can state it. */
data class StrictModeInstalled(val threadPolicyDetects: Set<String>, val vmPolicyDetects: Set<String>, val penalty: String)

/**
 * Debug-only StrictMode: disk / network on the main thread and leaked objects are logged (not fatal) so they show
 * up in Logcat during development without crashing a tester. Release builds never install it.
 */
object PerformanceSetup {
    fun shouldInstallStrictMode(debuggable: Boolean): Boolean = debuggable

    fun installStrictMode(): StrictModeInstalled {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectActivityLeaks()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .detectFileUriExposure()
                .penaltyLog()
                .build(),
        )
        return StrictModeInstalled(
            threadPolicyDetects = setOf("diskReads", "diskWrites", "network", "customSlowCalls"),
            vmPolicyDetects = setOf("activityLeaks", "leakedClosableObjects", "leakedRegistrationObjects", "fileUriExposure"),
            penalty = "log",
        )
    }
}
