package com.darshan.splitsmart.core.perf

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PerformanceSetupTest {
    @Test
    fun `strict mode is installed only for debuggable builds`() {
        assertThat(PerformanceSetup.shouldInstallStrictMode(debuggable = true)).isTrue()
        assertThat(PerformanceSetup.shouldInstallStrictMode(debuggable = false)).isFalse()
    }

    @Test
    fun `installing strict mode does not throw and reports the active policies`() {
        val installed = PerformanceSetup.installStrictMode()
        assertThat(installed.threadPolicyDetects).containsAtLeast("diskReads", "diskWrites", "network", "customSlowCalls")
        assertThat(installed.vmPolicyDetects).containsAtLeast("activityLeaks", "leakedClosableObjects", "leakedRegistrationObjects")
        assertThat(installed.penalty).isEqualTo("log")
    }
}
