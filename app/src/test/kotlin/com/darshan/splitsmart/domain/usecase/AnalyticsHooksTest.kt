package com.darshan.splitsmart.domain.usecase

import com.darshan.splitsmart.data.llm.LlmException
import com.darshan.splitsmart.domain.analytics.ScanSource
import com.darshan.splitsmart.domain.model.SplitConfig
import com.darshan.splitsmart.domain.model.SplitMode
import com.darshan.splitsmart.testutil.FakeBillRepository
import com.darshan.splitsmart.testutil.FakeLlmExtractor
import com.darshan.splitsmart.testutil.FakeReceiptDraftRepository
import com.darshan.splitsmart.testutil.FakeReceiptImageStore
import com.darshan.splitsmart.testutil.FakeTextRecognizer
import com.darshan.splitsmart.testutil.RecordingAnalyticsTracker
import com.darshan.splitsmart.testutil.RecordingCrashReporter
import com.darshan.splitsmart.testutil.SAMPLE_OCR_TEXT
import com.darshan.splitsmart.testutil.TestDispatcherProvider
import com.darshan.splitsmart.testutil.fixedClock
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testItem
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Success metrics: scan_success/failure, llm_parse_success/failure (+ fallback), split_created; non-fatals with custom keys. */
class AnalyticsHooksTest {
    private val analytics = RecordingAnalyticsTracker()
    private val crashes = RecordingCrashReporter()
    private val dispatchers = TestDispatcherProvider(Dispatchers.Unconfined)

    @Test
    fun `scan use case reports success and failure without any receipt text`() = runTest {
        val recognizer = FakeTextRecognizer(listOf("Paneer Tikka 250.00", "Total 250.00"))
        val store = FakeReceiptImageStore()
        val useCase = ScanReceiptUseCase(recognizer, store, FakeReceiptDraftRepository(), fixedClock, dispatchers, analytics, crashes)
        useCase.recognize("g1", store.newCaptureFile().apply { writeBytes(byteArrayOf(1)) })
        assertThat(analytics.last("scan_success")).containsExactly("source", "camera", "line_count", 2)
        useCase.importAndRecognize("g1", "content://media/1")
        assertThat(analytics.last("scan_success")["source"]).isEqualTo("gallery")

        recognizer.lines = emptyList()
        useCase.importAndRecognize("g1", "content://media/2")
        assertThat(analytics.last("scan_failure")).containsExactly("source", "gallery", "error", "no_text")
        assertThat(crashes.nonFatals).isEmpty() // expected user-facing outcome, not a defect

        recognizer.failWith = IllegalStateException("ml kit died")
        useCase.recognize("g1", store.newCaptureFile().apply { writeBytes(byteArrayOf(1)) })
        assertThat(analytics.last("scan_failure")["error"]).isEqualTo("recognition_failed")
        assertThat(crashes.nonFatals).hasSize(1)
        assertThat(crashes.nonFatals.single().second).containsEntry("scan_error", "recognition_failed")
        analytics.events.forEach { (_, params) -> params.values.forEach { assertThat(it.toString()).doesNotContain("Paneer") } }
    }

    @Test
    fun `extraction reports llm success, fallback with reason, and non-fatals only for unexpected failures`() = runTest {
        val llm = FakeLlmExtractor()
        val useCase = ExtractReceiptUseCase(llm, dispatchers, analytics, crashes)
        useCase.extract(SAMPLE_OCR_TEXT)
        assertThat(analytics.last("llm_parse_success")).containsExactly("item_count", 2, "warning_count", 0, "text_length", SAMPLE_OCR_TEXT.length)
        assertThat(crashes.keys).containsEntry("item_count", 2)

        llm.failWith = LlmException.KeyMissing()
        useCase.extract(SAMPLE_OCR_TEXT)
        assertThat(analytics.last("llm_parse_failure")).containsExactly("failure", "key_missing", "fallback_used", 1, "fallback_item_count", 2)
        assertThat(crashes.nonFatals).isEmpty() // no key is configuration, not a defect

        llm.failWith = LlmException.ServerError(503)
        useCase.extract(SAMPLE_OCR_TEXT)
        assertThat(analytics.last("llm_parse_failure")["failure"]).isEqualTo("server_error")
        assertThat(crashes.nonFatals).hasSize(1)
        assertThat(crashes.nonFatals.single().second).containsExactly("llm_failure", "server_error", "text_length", SAMPLE_OCR_TEXT.length)

        llm.failWith = null
        llm.response = "not json at all"
        useCase.extract(SAMPLE_OCR_TEXT)
        assertThat(analytics.last("llm_parse_failure")["failure"]).isEqualTo("malformed")
        assertThat(crashes.nonFatals).hasSize(2)
        assertThat(crashes.logs.any { it.contains("Paneer") }).isFalse()
    }

    @Test
    fun `saving a split reports split_created with sizes and sets custom keys`() = runTest {
        val repo = FakeBillRepository()
        val useCase = SaveSplitUseCase(repo, analytics, crashes)
        val bill = testBill(totalPaise = 300, items = listOf(testItem(unitPaise = 300)))
        useCase(bill, SplitConfig(SplitMode.BY_SHARE, listOf("a", "b", "c"), shares = mapOf("a" to 1, "b" to 1, "c" to 1)), payerId = "a")
        assertThat(analytics.last("split_created")).containsExactly("mode", "by_share", "member_count", 3, "item_count", 1)
        assertThat(crashes.keys).containsEntry("group_size", 3)
        useCase(bill, SplitConfig(SplitMode.EQUAL, emptyList()), null)
        assertThat(analytics.names().count { it == "split_created" }).isEqualTo(1) // invalid split is not a created split
    }
}
