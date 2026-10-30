package com.darshan.splitsmart.stress

import com.darshan.splitsmart.domain.parser.JsonRepair
import com.darshan.splitsmart.domain.parser.OcrTextNormalizer
import com.darshan.splitsmart.domain.parser.ReceiptJsonParser
import com.darshan.splitsmart.domain.parser.ReceiptParseException
import com.darshan.splitsmart.domain.parser.ReceiptValidator
import com.darshan.splitsmart.domain.parser.RegexReceiptParser
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/** Run with `./gradlew stressTest`. */
class ParserStressTest {
    private fun usedMb() = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1_048_576

    @Test
    fun `10000 synthetic OCR receipts through normalizer + regex parser`() {
        val texts = (0 until 10_000).map { SyntheticReceipts.ocrText(Random(it)) }
        var items = 0
        var withTotal = 0
        var validated = 0
        val before = usedMb()
        val ms = measureTimeMillis {
            texts.forEach { raw ->
                val receipt = RegexReceiptParser.parse(OcrTextNormalizer.normalize(raw))
                items += receipt.items.size
                if (receipt.totalPaise > 0) withTotal++
                if (ReceiptValidator.validate(receipt) != null) validated++
            }
        }
        println("STRESS [regex-parser] 10000 receipts in ${ms}ms (${ms * 1000 / 10_000} µs/receipt): items=$items (${items / 10_000.0}/receipt) withTotal=$withTotal validated=$validated heap ${before}→${usedMb()} MB")
        assertThat(withTotal).isAtLeast(9_900)
        assertThat(validated).isAtLeast(9_900)
        assertThat(ms).isLessThan(20_000)
    }

    @Test
    fun `10000 malformed json blobs never escape as unexpected exceptions`() {
        val parser = ReceiptJsonParser()
        val random = Random(99)
        val alphabet = "{}[]\",:0123456789.-abcxyz \n\t“”NaN"
        var repaired = 0
        var parsed = 0
        val ms = measureTimeMillis {
            repeat(10_000) { i ->
                val blob = when (i % 4) {
                    0 -> (0 until random.nextInt(0, 300)).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
                    1 -> SyntheticReceipts.llmJson(random).let { it.take(random.nextInt(1, it.length + 1)) }
                    2 -> "```json\n" + SyntheticReceipts.llmJson(random).replace("}", "},") + "\n```"
                    else -> SyntheticReceipts.llmJson(random)
                }
                val json = JsonRepair.repair(blob)
                if (json != null) {
                    repaired++
                    try {
                        parser.parse(json); parsed++
                    } catch (e: ReceiptParseException) {
                        // expected for garbage
                    }
                }
            }
        }
        println("STRESS [json-repair] 10000 blobs in ${ms}ms: repaired=$repaired parsed=$parsed")
        assertThat(parsed).isAtLeast(2_500) // every well-formed quarter must parse
        assertThat(ms).isLessThan(20_000)
    }
}
