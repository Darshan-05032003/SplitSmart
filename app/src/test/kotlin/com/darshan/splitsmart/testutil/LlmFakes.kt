package com.darshan.splitsmart.testutil

import com.darshan.splitsmart.data.llm.LlmException
import com.darshan.splitsmart.data.llm.LlmExtractor
import java.util.concurrent.atomic.AtomicInteger

class FakeLlmExtractor(var response: String = SAMPLE_LLM_JSON) : LlmExtractor {
    var failWith: LlmException? = null
    val calls = AtomicInteger()
    var lastText: String? = null
    /** Simulated model latency (virtual time under runTest). */
    var delayMs: Long = 0
    override suspend fun extract(ocrText: String): String {
        calls.incrementAndGet()
        lastText = ocrText
        if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
        failWith?.let { throw it }
        return response
    }
}

const val SAMPLE_LLM_JSON = """{"items":[{"name":"Paneer Tikka","qty":1,"unitPaise":25000,"totalPaise":25000},{"name":"Butter Naan","qty":2,"unitPaise":4500,"totalPaise":9000}],"taxPaise":1700,"tipPaise":0,"totalPaise":35700}"""

const val SAMPLE_OCR_TEXT = """Hotel Saravana Bhavan
Paneer Tikka 250.00
Butter Naan x2 90.00
Sub Total 340.00
CGST 2.5% 8.50
SGST 2.5% 8.50
TOTAL 357.00"""
