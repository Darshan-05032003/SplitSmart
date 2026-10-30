package com.darshan.splitsmart.domain.usecase

import com.darshan.splitsmart.core.DispatcherProvider
import com.darshan.splitsmart.data.llm.LlmException
import com.darshan.splitsmart.data.llm.LlmExtractor
import com.darshan.splitsmart.domain.model.ExtractionResult
import com.darshan.splitsmart.domain.model.ExtractionSource
import com.darshan.splitsmart.domain.model.LlmFailure
import com.darshan.splitsmart.domain.parser.JsonRepair
import com.darshan.splitsmart.domain.parser.ReceiptJsonParser
import com.darshan.splitsmart.domain.parser.ReceiptParseException
import com.darshan.splitsmart.domain.parser.ReceiptValidator
import com.darshan.splitsmart.domain.parser.RegexReceiptParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * OCR text → structured receipt. Tries the LLM (strict JSON → repair → parse → validate); on any failure the
 * on-device regex parser takes over and the reason is reported so the UI can explain it.
 */
class ExtractReceiptUseCase @Inject constructor(
    private val llm: LlmExtractor,
    private val dispatchers: DispatcherProvider,
) {
    private val parser = ReceiptJsonParser()

    suspend fun extract(ocrText: String): ExtractionResult = withContext(dispatchers.default) {
        val text = ocrText.trim()
        if (text.isEmpty()) return@withContext regexResult(text, null)
        try {
            val raw = llm.extract(text)
            val json = JsonRepair.repair(raw) ?: throw LlmException.Malformed("unrepairable json")
            val parsed = try { parser.parse(json) } catch (e: ReceiptParseException) { throw LlmException.Malformed(e.message ?: "parse") }
            val validated = ReceiptValidator.validate(parsed) ?: throw LlmException.Malformed("nothing usable")
            ExtractionResult(validated.receipt, ExtractionSource.LLM, validated.warnings, llmFailure = null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: LlmException) {
            regexResult(text, e.kind)
        } catch (e: Exception) {
            regexResult(text, LlmFailure.NETWORK)
        }
    }

    private fun regexResult(text: String, failure: LlmFailure?): ExtractionResult {
        val parsed = RegexReceiptParser.parse(text)
        val validated = ReceiptValidator.validate(parsed)
        return ExtractionResult(validated?.receipt ?: parsed, ExtractionSource.REGEX, validated?.warnings ?: emptySet(), failure)
    }
}
