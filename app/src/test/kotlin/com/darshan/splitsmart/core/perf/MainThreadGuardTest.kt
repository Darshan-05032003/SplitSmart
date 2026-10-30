package com.darshan.splitsmart.core.perf

import com.darshan.splitsmart.core.DefaultDispatcherProvider
import com.darshan.splitsmart.data.llm.LlmExtractor
import com.darshan.splitsmart.data.ocr.OcrLine
import com.darshan.splitsmart.data.ocr.OcrResult
import com.darshan.splitsmart.data.ocr.TextRecognizer
import com.darshan.splitsmart.domain.model.SplitConfig
import com.darshan.splitsmart.domain.model.SplitMode
import com.darshan.splitsmart.domain.usecase.ExtractReceiptUseCase
import com.darshan.splitsmart.domain.usecase.SaveSplitUseCase
import com.darshan.splitsmart.domain.usecase.ScanReceiptUseCase
import com.darshan.splitsmart.testutil.FakeBillRepository
import com.darshan.splitsmart.testutil.FakeReceiptDraftRepository
import com.darshan.splitsmart.testutil.FakeReceiptImageStore
import com.darshan.splitsmart.testutil.RecordingAnalyticsTracker
import com.darshan.splitsmart.testutil.RecordingCrashReporter
import com.darshan.splitsmart.testutil.SAMPLE_LLM_JSON
import com.darshan.splitsmart.testutil.SAMPLE_OCR_TEXT
import com.darshan.splitsmart.testutil.fixedClock
import com.darshan.splitsmart.testutil.testBill
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File

/**
 * ANR safety: the heavy paths (OCR, LLM, split persistence) must run on the injected background dispatchers,
 * never on the thread that called them (the main thread in the app).
 */
class MainThreadGuardTest {
    private val caller = Thread.currentThread().name
    private val dispatchers = DefaultDispatcherProvider()

    @Test
    fun `LLM extraction runs off the calling thread`() = runBlocking {
        var llmThread = ""
        val llm = object : LlmExtractor { override suspend fun extract(ocrText: String): String { llmThread = Thread.currentThread().name; return SAMPLE_LLM_JSON } }
        ExtractReceiptUseCase(llm, dispatchers, RecordingAnalyticsTracker(), RecordingCrashReporter()).extract(SAMPLE_OCR_TEXT)
        assertThat(llmThread).isNotEqualTo(caller)
        assertThat(llmThread).contains("DefaultDispatcher")
    }

    @Test
    fun `OCR recognition runs off the calling thread`() = runBlocking {
        var ocrThread = ""
        val recognizer = object : TextRecognizer { override suspend fun recognize(image: File): OcrResult { ocrThread = Thread.currentThread().name; return OcrResult(listOf(OcrLine("Chai 20.00", 0, 0))) } }
        val store = FakeReceiptImageStore()
        val useCase = ScanReceiptUseCase(recognizer, store, FakeReceiptDraftRepository(), fixedClock, dispatchers, RecordingAnalyticsTracker(), RecordingCrashReporter())
        useCase.recognize("g1", store.newCaptureFile().apply { writeBytes(byteArrayOf(1)) })
        assertThat(ocrThread).isNotEqualTo(caller)
        assertThat(ocrThread).contains("DefaultDispatcher")
    }

    @Test
    fun `split persistence goes through the repository's io dispatcher`() = runBlocking {
        var saveThread = ""
        val repo = object : FakeBillRepository() {
            override suspend fun saveBill(bill: com.darshan.splitsmart.domain.model.Bill) = kotlinx.coroutines.withContext(dispatchers.io) { saveThread = Thread.currentThread().name; super.saveBill(bill) }
        }
        SaveSplitUseCase(repo, RecordingAnalyticsTracker(), RecordingCrashReporter())(testBill(items = emptyList()), SplitConfig(SplitMode.EQUAL, listOf("a")), null)
        assertThat(saveThread).isNotEqualTo(caller)
        assertThat(saveThread).contains("DefaultDispatcher") // Dispatchers.IO shares the Default pool threads
    }
}
