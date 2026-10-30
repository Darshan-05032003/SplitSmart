package com.darshan.splitsmart.stress

import com.darshan.splitsmart.core.BackoffPolicy
import com.darshan.splitsmart.core.DefaultDispatcherProvider
import com.darshan.splitsmart.data.llm.GeminiExtractor
import com.darshan.splitsmart.data.llm.LlmException
import com.darshan.splitsmart.data.llm.LlmHttpClient
import com.darshan.splitsmart.testutil.SAMPLE_LLM_JSON
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/** 1,000 concurrent Gemini calls against MockWebServer that rate-limits the first wave. Run with `./gradlew stressTest`. */
class LlmStressTest {
    private lateinit var server: MockWebServer
    private val hits = AtomicInteger()
    private val rateLimited = AtomicInteger()
    private val arrivals = Collections.synchronizedList(mutableListOf<Long>())

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val n = hits.incrementAndGet()
                arrivals += System.nanoTime()
                // First 1,000 requests: 60 % are throttled with 429; everything after that succeeds.
                return if (n <= 1_000 && n % 5 < 3) {
                    rateLimited.incrementAndGet()
                    MockResponse.Builder().code(429).build()
                } else {
                    MockResponse.Builder().code(200).body(candidate(SAMPLE_LLM_JSON)).build()
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.close()

    private fun candidate(text: String) = """{"candidates":[{"content":{"parts":[{"text":${Json.encodeToString(kotlinx.serialization.serializer<String>(), text)}}]}}]}"""

    @Test
    fun `1000 concurrent calls with 429s - backoff with jitter must not thunder`() = runBlocking {
        val client = LlmHttpClient.create(pinner = null, dispatcher = okhttp3.Dispatcher().apply { maxRequests = 128; maxRequestsPerHost = 128 })
        val sleeps = Collections.synchronizedList(mutableListOf<Long>())
        val policy = BackoffPolicy(initialDelayMs = 200, maxDelayMs = 2_000, multiplier = 2.0, maxAttempts = 4)
        val extractor = GeminiExtractor(client, "stress-key", server.url("/"), backoff = policy, dispatchers = DefaultDispatcherProvider(), random = Random(7), sleep = { sleeps += it; delay(it) })
        var failures = 0
        val before = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1_048_576
        val ms = measureTimeMillis {
            val results = (0 until 1_000).map { i -> async(Dispatchers.IO) { runCatching { extractor.extract("Receipt $i\nChai 20.00\nTotal 20.00") } } }.awaitAll()
            failures = results.count { it.isFailure }
            results.filter { it.isFailure }.forEach { assertThat(it.exceptionOrNull()).isInstanceOf(LlmException::class.java) }
        }
        val after = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1_048_576
        // Thundering-herd check: retries scheduled after the 429 wave must be spread across the jitter window, not stacked at one instant.
        val firstWave = sleeps.take(rateLimited.get().coerceAtMost(sleeps.size))
        val buckets = firstWave.groupBy { it / 20 }.values.map { it.size }
        val maxBucket = buckets.maxOrNull() ?: 0
        println("STRESS [llm-1000] total=${ms}ms requests=${hits.get()} rateLimited=${rateLimited.get()} retriesScheduled=${sleeps.size} failures=$failures heap ${before}→${after} MB; retry delays spread over ${firstWave.minOrNull()}..${firstWave.maxOrNull()} ms, max per 20ms bucket=$maxBucket of ${firstWave.size}")
        assertThat(failures).isEqualTo(0)
        assertThat(hits.get()).isAtLeast(1_000 + rateLimited.get())
        assertThat(maxBucket).isLessThan(firstWave.size / 3) // full jitter: no single 20 ms slot holds a third of the retries
        client.dispatcher.executorService.shutdown()
    }
}
