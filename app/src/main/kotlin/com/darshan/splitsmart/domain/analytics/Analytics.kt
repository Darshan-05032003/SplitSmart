package com.darshan.splitsmart.domain.analytics

import com.darshan.splitsmart.domain.model.LlmFailure
import com.darshan.splitsmart.domain.model.ScanError
import com.darshan.splitsmart.domain.model.SplitMode

enum class ScanSource { CAMERA, GALLERY }
enum class SettleUpMethod { UPI, SHARE, QR }

/**
 * The success metrics the app reports. Every event is typed: parameters are enums, counts and booleans only,
 * never names, UPI IDs, amounts or receipt text ([AnalyticsPolicy] enforces this a second time at the sink).
 */
sealed class AnalyticsEvent(val name: String, val params: Map<String, Any>) {
    class ScanSuccess(source: ScanSource, lineCount: Int) : AnalyticsEvent("scan_success", mapOf("source" to source, "line_count" to lineCount))
    class ScanFailure(source: ScanSource, error: ScanError) : AnalyticsEvent("scan_failure", mapOf("source" to source, "error" to error))
    class LlmParseSuccess(itemCount: Int, warningCount: Int, textLength: Int) :
        AnalyticsEvent("llm_parse_success", mapOf("item_count" to itemCount, "warning_count" to warningCount, "text_length" to textLength))
    class LlmParseFailure(failure: LlmFailure, fallbackUsed: Boolean, fallbackItemCount: Int) :
        AnalyticsEvent("llm_parse_failure", mapOf("failure" to failure, "fallback_used" to fallbackUsed, "fallback_item_count" to fallbackItemCount))
    class SplitCreated(mode: SplitMode, memberCount: Int, itemCount: Int) :
        AnalyticsEvent("split_created", mapOf("mode" to mode, "member_count" to memberCount, "item_count" to itemCount))
    class SettleUpTap(method: SettleUpMethod, payeeHasVpa: Boolean) : AnalyticsEvent("settle_up_tap", mapOf("method" to method, "payee_has_id" to payeeHasVpa))
}

interface AnalyticsTracker {
    fun track(event: AnalyticsEvent)
}

interface CrashReporter {
    fun log(message: String)
    fun setKey(key: String, value: Int)
    fun setKey(key: String, value: String)
    fun recordNonFatal(throwable: Throwable, keys: Map<String, Any> = emptyMap())
}

object NoOpAnalyticsTracker : AnalyticsTracker {
    override fun track(event: AnalyticsEvent) = Unit
}

object NoOpCrashReporter : CrashReporter {
    override fun log(message: String) = Unit
    override fun setKey(key: String, value: Int) = Unit
    override fun setKey(key: String, value: String) = Unit
    override fun recordNonFatal(throwable: Throwable, keys: Map<String, Any>) = Unit
}

/**
 * Privacy gate for everything that leaves the device as telemetry: snake_case keys without PII-ish words,
 * values that are numbers, booleans (→ 0/1), enums (→ lowercase names) or short strings without `@` or phone-like
 * digit runs. Violations throw, so a mistake fails a unit test instead of shipping a name to a dashboard.
 */
object AnalyticsPolicy {
    private val KEY = Regex("[a-z][a-z0-9_]{0,39}")
    private val FORBIDDEN_KEY_WORDS = setOf("name", "vpa", "email", "phone", "mobile", "amount", "total", "paise", "address", "upi")
    private const val MAX_VALUE_LENGTH = 100

    fun checkKey(key: String) {
        require(KEY.matches(key)) { "analytics key must be snake_case: $key" }
        require(key.split('_').none { it in FORBIDDEN_KEY_WORDS }) { "analytics key may carry PII: $key" }
    }

    fun checkValue(key: String, value: Any): Any = when (value) {
        is Boolean -> if (value) 1 else 0
        is Int, is Long -> value
        is Enum<*> -> value.name.lowercase()
        is String -> {
            require(value.length <= MAX_VALUE_LENGTH) { "analytics value too long for $key" }
            require(!value.contains('@')) { "analytics value for $key looks like an address" }
            require(value.count { it.isDigit() } < 10) { "analytics value for $key looks like a phone number" }
            value
        }
        else -> throw IllegalArgumentException("unsupported analytics value type for $key: ${value::class.simpleName}")
    }

    fun sanitize(event: AnalyticsEvent): Map<String, Any> = sanitizeKeys(event.params)

    fun sanitizeKeys(keys: Map<String, Any>): Map<String, Any> = keys.entries.associate { (k, v) -> checkKey(k); k to checkValue(k, v) }
}
