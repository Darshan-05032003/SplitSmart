package com.darshan.splitsmart.data.analytics

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.domain.analytics.AnalyticsEvent
import com.darshan.splitsmart.domain.analytics.ScanSource
import com.darshan.splitsmart.domain.model.ScanError
import com.google.common.truth.Truth.assertThat
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class FirebaseAdaptersTest {
    @Test
    fun `analytics tracker logs the sanitised event as a bundle`() {
        val analytics = mockk<FirebaseAnalytics>(relaxed = true)
        val bundle = slot<Bundle>()
        every { analytics.logEvent("scan_failure", capture(bundle)) } returns Unit
        FirebaseAnalyticsTracker(analytics).track(AnalyticsEvent.ScanFailure(ScanSource.CAMERA, ScanError.CAPTURE_FAILED))
        verify(exactly = 1) { analytics.logEvent("scan_failure", any()) }
        assertThat(bundle.captured.getString("source")).isEqualTo("camera")
        assertThat(bundle.captured.getString("error")).isEqualTo("capture_failed")
        assertThat(bundle.captured.keySet()).containsExactly("source", "error")
    }

    @Test
    fun `crash reporter forwards logs, keys and non-fatals with keys`() {
        val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        val reporter = FirebaseCrashReporter(crashlytics)
        reporter.log("extraction started")
        reporter.setKey("group_size", 4)
        reporter.setKey("llm_model", "gemini")
        val boom = IllegalStateException("boom")
        reporter.recordNonFatal(boom, mapOf("item_count" to 7, "llm_failure" to "timeout"))
        verify { crashlytics.log("extraction started") }
        verify { crashlytics.setCustomKey("group_size", 4) }
        verify { crashlytics.setCustomKey("llm_model", "gemini") }
        verify { crashlytics.setCustomKey("item_count", 7) }
        verify { crashlytics.setCustomKey("llm_failure", "timeout") }
        verify { crashlytics.recordException(boom) }
    }

    @Test
    fun `a pii-looking custom key never reaches crashlytics`() {
        val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        val reporter = FirebaseCrashReporter(crashlytics)
        reporter.recordNonFatal(RuntimeException("x"), mapOf("payee_vpa" to "asha@okicici", "item_count" to 2))
        verify(exactly = 0) { crashlytics.setCustomKey("payee_vpa", any<String>()) }
        verify { crashlytics.setCustomKey("item_count", 2) }
        verify { crashlytics.recordException(any()) }
        reporter.log("Pay asha@okicici 250") // messages are scrubbed, not dropped
        verify { crashlytics.log(match { !it.contains("@") }) }
    }
}
