package com.darshan.splitsmart.stress

import com.darshan.splitsmart.domain.analytics.AnalyticsEvent
import com.darshan.splitsmart.domain.analytics.AnalyticsPolicy
import com.darshan.splitsmart.domain.analytics.ScanSource
import com.darshan.splitsmart.domain.analytics.SettleUpMethod
import com.darshan.splitsmart.domain.model.LlmFailure
import com.darshan.splitsmart.domain.model.ScanError
import com.darshan.splitsmart.domain.model.SplitMode
import com.darshan.splitsmart.testutil.RecordingAnalyticsTracker
import com.darshan.splitsmart.testutil.RecordingCrashReporter
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/** Run with `./gradlew stressTest`. */
class AnalyticsStressTest {
    @Test
    fun `10000 concurrent events pass the pii policy and none carries an at-sign or a long digit run`() = runBlocking {
        val random = Random(8)
        val tracker = RecordingAnalyticsTracker()
        val events = List(10_000) { i ->
            when (i % 6) {
                0 -> AnalyticsEvent.ScanSuccess(ScanSource.entries[random.nextInt(2)], random.nextInt(0, 500))
                1 -> AnalyticsEvent.ScanFailure(ScanSource.entries[random.nextInt(2)], ScanError.entries[random.nextInt(ScanError.entries.size)])
                2 -> AnalyticsEvent.LlmParseSuccess(random.nextInt(0, 200), random.nextInt(0, 8), random.nextInt(0, 20_000))
                3 -> AnalyticsEvent.LlmParseFailure(LlmFailure.entries[random.nextInt(LlmFailure.entries.size)], random.nextBoolean(), random.nextInt(0, 200))
                4 -> AnalyticsEvent.SplitCreated(SplitMode.entries[random.nextInt(3)], random.nextInt(1, 50), random.nextInt(0, 200))
                else -> AnalyticsEvent.SettleUpTap(SettleUpMethod.entries[random.nextInt(3)], random.nextBoolean())
            }
        }
        val ms = measureTimeMillis { events.chunked(100).map { chunk -> async(Dispatchers.Default) { chunk.forEach(tracker::track) } }.awaitAll() }
        assertThat(tracker.events).hasSize(10_000)
        tracker.events.forEach { (name, params) ->
            assertThat(name).matches("[a-z_]+")
            params.values.forEach { v -> val s = v.toString(); assertThat(s).doesNotContain("@"); assertThat(s).doesNotContainMatch("\\d{7,}") }
        }
        println("STRESS [analytics-10k] 10000 events sanitised + recorded concurrently in ${ms}ms")
    }

    @Test
    fun `1000 non-fatals with custom keys`() {
        val reporter = RecordingCrashReporter()
        val ms = measureTimeMillis {
            repeat(1_000) { i ->
                runCatching { AnalyticsPolicy.sanitizeKeys(mapOf("item_count" to i, "llm_failure" to "timeout")) }
                    .onSuccess { reporter.recordNonFatal(IllegalStateException("non-fatal $i"), it) }
            }
        }
        assertThat(reporter.nonFatals).hasSize(1_000)
        println("STRESS [crash-1000] 1000 non-fatals recorded in ${ms}ms")
    }
}
