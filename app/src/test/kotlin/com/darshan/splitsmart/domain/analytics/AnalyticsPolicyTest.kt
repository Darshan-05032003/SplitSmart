package com.darshan.splitsmart.domain.analytics

import com.darshan.splitsmart.domain.model.LlmFailure
import com.darshan.splitsmart.domain.model.ScanError
import com.darshan.splitsmart.domain.model.SplitMode
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class AnalyticsPolicyTest {
    @Test
    fun `events have stable snake_case names and typed parameters only`() {
        val events = listOf(
            AnalyticsEvent.ScanSuccess(ScanSource.CAMERA, lineCount = 12) to "scan_success",
            AnalyticsEvent.ScanFailure(ScanSource.GALLERY, ScanError.NO_TEXT) to "scan_failure",
            AnalyticsEvent.LlmParseSuccess(itemCount = 5, warningCount = 1, textLength = 900) to "llm_parse_success",
            AnalyticsEvent.LlmParseFailure(LlmFailure.RATE_LIMITED, fallbackUsed = true, fallbackItemCount = 3) to "llm_parse_failure",
            AnalyticsEvent.SplitCreated(SplitMode.BY_ITEM, memberCount = 4, itemCount = 9) to "split_created",
            AnalyticsEvent.SettleUpTap(SettleUpMethod.UPI, payeeHasVpa = true) to "settle_up_tap",
        )
        events.forEach { (event, name) ->
            assertThat(event.name).isEqualTo(name)
            val params = AnalyticsPolicy.sanitize(event)
            params.forEach { (k, v) ->
                assertWithMessage(k).that(k).matches("[a-z][a-z0-9_]{0,39}")
                assertWithMessage(k).that(v is Int || v is Long || v is String).isTrue()
            }
        }
        assertThat(AnalyticsPolicy.sanitize(AnalyticsEvent.ScanFailure(ScanSource.GALLERY, ScanError.NO_TEXT)))
            .containsExactly("source", "gallery", "error", "no_text")
        assertThat(AnalyticsPolicy.sanitize(AnalyticsEvent.LlmParseFailure(LlmFailure.RATE_LIMITED, true, 3)))
            .containsExactly("failure", "rate_limited", "fallback_used", 1, "fallback_item_count", 3)
        assertThat(AnalyticsPolicy.sanitize(AnalyticsEvent.SplitCreated(SplitMode.BY_ITEM, 4, 9)))
            .containsExactly("mode", "by_item", "member_count", 4, "item_count", 9)
    }

    @Test
    fun `pii-looking values and forbidden keys are rejected`() {
        listOf("asha@okicici", "someone@example.com", "9876543210", "+91 98765 43210", "x".repeat(101)).forEach {
            assertWithMessage(it).that(runCatching { AnalyticsPolicy.checkValue("note", it) }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        }
        listOf("name", "member_name", "vpa", "payee_vpa", "email", "phone", "amount", "amount_paise", "total").forEach {
            assertWithMessage(it).that(runCatching { AnalyticsPolicy.checkKey(it) }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        }
        assertThat(runCatching { AnalyticsPolicy.checkKey("Bad Key") }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(runCatching { AnalyticsPolicy.checkValue("source", "camera") }.exceptionOrNull()).isNull()
        assertThat(runCatching { AnalyticsPolicy.checkKey("item_count") }.exceptionOrNull()).isNull()
    }

    @Test
    fun `custom crash keys follow the same policy`() {
        assertThat(AnalyticsPolicy.sanitizeKeys(mapOf("group_size" to 4, "item_count" to 12L, "llm_failure" to "timeout")))
            .containsExactly("group_size", 4, "item_count", 12L, "llm_failure", "timeout")
        assertThat(runCatching { AnalyticsPolicy.sanitizeKeys(mapOf("payee_vpa" to "a@b")) }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(runCatching { AnalyticsPolicy.sanitizeKeys(mapOf("note" to "asha@okicici")) }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `no-op implementations are safe to call`() {
        NoOpAnalyticsTracker.track(AnalyticsEvent.SettleUpTap(SettleUpMethod.QR, false))
        NoOpCrashReporter.log("x"); NoOpCrashReporter.setKey("k", 1); NoOpCrashReporter.setKey("k", "v"); NoOpCrashReporter.recordNonFatal(RuntimeException("x"))
    }
}
