package com.darshan.splitsmart.realapi

import com.darshan.splitsmart.BuildConfig
import com.darshan.splitsmart.core.BackoffPolicy
import com.darshan.splitsmart.core.DefaultDispatcherProvider
import com.darshan.splitsmart.data.llm.GeminiExtractor
import com.darshan.splitsmart.data.llm.GeminiPins
import com.darshan.splitsmart.data.llm.LlmHttpClient
import com.darshan.splitsmart.domain.parser.JsonRepair
import com.darshan.splitsmart.domain.parser.ReceiptJsonParser
import com.darshan.splitsmart.domain.parser.ReceiptValidator
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * The ONE real call to Gemini, run manually with `./gradlew realApiTest` (needs LLM_API_KEY in local.properties).
 * Uses the production pinned client, so it also proves the certificate pins against the live host.
 * Excluded from the normal suite; prints status + latency only (never the key).
 */
class GeminiRealApiTest {
    @Test
    fun `tiny receipt round trip through the real pinned client`() = runBlocking {
        assumeTrue("LLM_API_KEY not configured", BuildConfig.LLM_API_KEY.isNotEmpty())
        // Production pins, production host, production timeouts (30 s read / 45 s call) and model list. One attempt, no retries.
        val client = LlmHttpClient.create(GeminiPins.pinner())
        val extractor = GeminiExtractor(
            client = client, apiKey = BuildConfig.LLM_API_KEY, baseUrl = GeminiExtractor.PRODUCTION_BASE_URL, models = GeminiExtractor.MODELS,
            backoff = BackoffPolicy.NONE, dispatchers = DefaultDispatcherProvider(),
        )
        var raw = ""
        val ms = measureTimeMillis {
            try {
                raw = extractor.extract("Cafe Udupi\nMasala Dosa 120.00\nFilter Coffee x2 80.00\nGST 5% 10.00\nTotal 210.00")
            } catch (e: Exception) {
                println("REAL_API ${GeminiExtractor.DEFAULT_MODEL} FAILED ${e.javaClass.simpleName}: ${e.message}")
                throw e
            }
        }
        val receipt = ReceiptJsonParser().parse(JsonRepair.repair(raw)!!)
        val validated = ReceiptValidator.validate(receipt)!!
        println("REAL_API ${GeminiExtractor.DEFAULT_MODEL} status=200 latency=${ms}ms items=${validated.receipt.items.size} total=${validated.receipt.totalPaise} warnings=${validated.warnings}")
        assertThat(validated.receipt.items).isNotEmpty()
        assertThat(validated.receipt.totalPaise).isEqualTo(21_000)
    }
}
