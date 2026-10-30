package com.darshan.splitsmart.domain.model

/** One line item as extracted from a receipt, before validation. Amounts in paise. */
data class ExtractedItem(val name: String, val qty: Int, val unitPaise: Long, val totalPaise: Long)

data class ExtractedReceipt(val items: List<ExtractedItem>, val taxPaise: Long, val tipPaise: Long, val totalPaise: Long) {
    val itemsSubtotalPaise: Long get() = items.sumOf { it.totalPaise }
}

enum class ExtractionSource { LLM, REGEX }

/** Repairs the validator applied; shown to the user so they know what to double-check. */
enum class ExtractionWarning { NEGATIVE_DROPPED, ABSURD_DROPPED, ITEM_MATH_FIXED, NAME_FILLED, QTY_FIXED, ITEMS_CAPPED, TOTAL_DERIVED, SUM_MISMATCH }

/** Why the LLM path was not used (the regex parser took over). */
enum class LlmFailure { KEY_MISSING, NETWORK, TIMEOUT, UNAUTHORIZED, RATE_LIMITED, CLIENT_ERROR, SERVER_ERROR, MALFORMED }

data class ExtractionResult(
    val receipt: ExtractedReceipt,
    val source: ExtractionSource,
    val warnings: Set<ExtractionWarning>,
    val llmFailure: LlmFailure?,
)
