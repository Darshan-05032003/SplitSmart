package com.darshan.splitsmart.domain.usecase

import com.darshan.splitsmart.data.llm.LlmException
import com.darshan.splitsmart.domain.model.ExtractionSource
import com.darshan.splitsmart.domain.model.ExtractionWarning
import com.darshan.splitsmart.domain.model.LlmFailure
import com.darshan.splitsmart.testutil.FakeLlmExtractor
import com.darshan.splitsmart.testutil.SAMPLE_OCR_TEXT
import com.darshan.splitsmart.testutil.TestDispatcherProvider
import com.darshan.splitsmart.testutil.RecordingAnalyticsTracker
import com.darshan.splitsmart.testutil.RecordingCrashReporter
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ExtractReceiptUseCaseTest {
    private val llm = FakeLlmExtractor()
    private val analytics = RecordingAnalyticsTracker()
    private val crashes = RecordingCrashReporter()
    private val useCase = ExtractReceiptUseCase(llm, TestDispatcherProvider(Dispatchers.Unconfined), analytics, crashes)

    @Test
    fun `rate limit carries the server's retry-after in whole seconds`() = runTest {
        llm.failWith = LlmException.RateLimited(retryAfterMs = 12_500)
        val r = useCase.extract(SAMPLE_OCR_TEXT)
        assertThat(r.llmFailure).isEqualTo(LlmFailure.RATE_LIMITED)
        assertThat(r.retryAfterSeconds).isEqualTo(13)
        llm.failWith = LlmException.RateLimited(null)
        assertThat(useCase.extract(SAMPLE_OCR_TEXT).retryAfterSeconds).isNull()
    }

    @Test
    fun `offline and pin failures fall back with their own reason`() = runTest {
        llm.failWith = LlmException.Offline(java.net.UnknownHostException())
        assertThat(useCase.extract(SAMPLE_OCR_TEXT).llmFailure).isEqualTo(LlmFailure.OFFLINE)
        assertThat(crashes.nonFatals).isEmpty() // being offline is a user condition, not a defect
        llm.failWith = LlmException.Insecure(javax.net.ssl.SSLPeerUnverifiedException("Certificate pinning failure!"))
        val r = useCase.extract(SAMPLE_OCR_TEXT)
        assertThat(r.llmFailure).isEqualTo(LlmFailure.INSECURE)
        assertThat(r.source).isEqualTo(ExtractionSource.REGEX)
        assertThat(crashes.nonFatals).hasSize(1) // a pin failure is worth knowing about
        assertThat(analytics.last("llm_parse_failure")["failure"]).isEqualTo("insecure")
    }

    @Test
    fun `provisional result is the on-device parse, instant and without analytics or an llm call`() = runTest {
        val r = useCase.provisional(SAMPLE_OCR_TEXT)
        assertThat(r.source).isEqualTo(ExtractionSource.REGEX)
        assertThat(r.llmFailure).isNull()
        assertThat(r.receipt.totalPaise).isEqualTo(35_700)
        assertThat(llm.calls.get()).isEqualTo(0)
        assertThat(analytics.events).isEmpty()
    }

    @Test
    fun `uses the LLM result when it is valid`() = runTest {
        val r = useCase.extract(SAMPLE_OCR_TEXT)
        assertThat(r.source).isEqualTo(ExtractionSource.LLM)
        assertThat(r.receipt.items.map { it.name }).containsExactly("Paneer Tikka", "Butter Naan").inOrder()
        assertThat(r.receipt.totalPaise).isEqualTo(35_700)
        assertThat(r.llmFailure).isNull()
        assertThat(llm.lastText).isEqualTo(SAMPLE_OCR_TEXT)
    }

    @Test
    fun `repairs fenced json with trailing commas`() = runTest {
        llm.response = "```json\n{\"items\":[{\"name\":\"Dosa\",\"qty\":1,\"unitPaise\":12000,\"totalPaise\":12000,},],\"taxPaise\":0,\"tipPaise\":0,\"totalPaise\":12000,}\n```"
        val r = useCase.extract("Dosa 120.00")
        assertThat(r.source).isEqualTo(ExtractionSource.LLM)
        assertThat(r.receipt.items.single().name).isEqualTo("Dosa")
    }

    @Test
    fun `falls back to regex when the LLM key is missing`() = runTest {
        llm.failWith = LlmException.KeyMissing()
        val r = useCase.extract(SAMPLE_OCR_TEXT)
        assertThat(r.source).isEqualTo(ExtractionSource.REGEX)
        assertThat(r.llmFailure).isEqualTo(LlmFailure.KEY_MISSING)
        assertThat(r.receipt.items).hasSize(2)
        assertThat(r.receipt.totalPaise).isEqualTo(35_700)
    }

    @Test
    fun `falls back on network, rate limit and server failures`() = runTest {
        listOf(LlmException.Network(RuntimeException()) to LlmFailure.NETWORK, LlmException.RateLimited(null) to LlmFailure.RATE_LIMITED, LlmException.ServerError(503) to LlmFailure.SERVER_ERROR, LlmException.Timeout(RuntimeException()) to LlmFailure.TIMEOUT).forEach { (ex, kind) ->
            llm.failWith = ex
            val r = useCase.extract(SAMPLE_OCR_TEXT)
            assertThat(r.source).isEqualTo(ExtractionSource.REGEX)
            assertThat(r.llmFailure).isEqualTo(kind)
        }
    }

    @Test
    fun `unrepairable json falls back with MALFORMED`() = runTest {
        llm.response = "I cannot help with that."
        val r = useCase.extract(SAMPLE_OCR_TEXT)
        assertThat(r.source).isEqualTo(ExtractionSource.REGEX)
        assertThat(r.llmFailure).isEqualTo(LlmFailure.MALFORMED)
    }

    @Test
    fun `json that validates to nothing usable falls back`() = runTest {
        llm.response = """{"items":[],"taxPaise":0,"tipPaise":0,"totalPaise":0}"""
        val r = useCase.extract(SAMPLE_OCR_TEXT)
        assertThat(r.source).isEqualTo(ExtractionSource.REGEX)
        assertThat(r.llmFailure).isEqualTo(LlmFailure.MALFORMED)
    }

    @Test
    fun `validator warnings are propagated`() = runTest {
        llm.response = """{"items":[{"name":"","qty":0,"unitPaise":100,"totalPaise":100}],"taxPaise":0,"tipPaise":0,"totalPaise":0}"""
        val r = useCase.extract("x 1.00")
        assertThat(r.source).isEqualTo(ExtractionSource.LLM)
        assertThat(r.warnings).containsAtLeast(ExtractionWarning.NAME_FILLED, ExtractionWarning.QTY_FIXED, ExtractionWarning.TOTAL_DERIVED)
    }

    @Test
    fun `blank text never calls the LLM`() = runTest {
        val r = useCase.extract("   ")
        assertThat(r.source).isEqualTo(ExtractionSource.REGEX)
        assertThat(llm.calls.get()).isEqualTo(0)
        assertThat(r.receipt.items).isEmpty()
    }

    @Test
    fun `unexpected exceptions from the client are treated as network failures`() = runTest {
        val crashing = object : com.darshan.splitsmart.data.llm.LlmExtractor {
            override suspend fun extract(ocrText: String): String = throw IllegalStateException("boom")
        }
        val r = ExtractReceiptUseCase(crashing, TestDispatcherProvider(Dispatchers.Unconfined), RecordingAnalyticsTracker(), RecordingCrashReporter()).extract(SAMPLE_OCR_TEXT)
        assertThat(r.source).isEqualTo(ExtractionSource.REGEX)
        assertThat(r.llmFailure).isEqualTo(LlmFailure.NETWORK)
    }
}
