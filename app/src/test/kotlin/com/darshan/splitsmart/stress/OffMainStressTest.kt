package com.darshan.splitsmart.stress

import com.darshan.splitsmart.core.DefaultDispatcherProvider
import com.darshan.splitsmart.data.llm.LlmExtractor
import com.darshan.splitsmart.domain.usecase.ExtractReceiptUseCase
import com.darshan.splitsmart.testutil.RecordingAnalyticsTracker
import com.darshan.splitsmart.testutil.RecordingCrashReporter
import com.darshan.splitsmart.testutil.SAMPLE_LLM_JSON
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.Collections
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/** Run with `./gradlew stressTest`. */
class OffMainStressTest {
    @Test
    fun `1000 concurrent extractions never touch the calling thread`() = runBlocking {
        val caller = Thread.currentThread().name
        val threads = Collections.synchronizedSet(mutableSetOf<String>())
        val llm = object : LlmExtractor {
            override suspend fun extract(ocrText: String): String { threads += Thread.currentThread().name; delay(Random(ocrText.hashCode()).nextLong(1, 10)); return SAMPLE_LLM_JSON }
        }
        val useCase = ExtractReceiptUseCase(llm, DefaultDispatcherProvider(), RecordingAnalyticsTracker(), RecordingCrashReporter())
        val ms = measureTimeMillis {
            (0 until 1_000).map { i -> async { useCase.extract("Receipt $i\n" + SyntheticReceipts.ocrText(Random(i))) } }.awaitAll()
        }
        // kotlinx-coroutines debug mode appends "@coroutine#n" to thread names; strip it to count real worker threads.
        val workers = threads.map { it.substringBefore(" @") }.toSet()
        println("STRESS [off-main-1000] 1000 concurrent extractions in ${ms}ms on ${workers.size} worker threads (${Runtime.getRuntime().availableProcessors()} cores); caller thread used: ${caller in threads}")
        assertThat(threads).doesNotContain(caller)
        assertThat(ms).isLessThan(30_000)
    }
}
