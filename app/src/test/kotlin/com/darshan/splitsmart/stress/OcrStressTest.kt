package com.darshan.splitsmart.stress

import com.darshan.splitsmart.domain.parser.OcrTextNormalizer
import com.darshan.splitsmart.testutil.FakeReceiptImageStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/** Run with `./gradlew stressTest`. */
class OcrStressTest {
    private fun usedMb() = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1_048_576

    @Test
    fun `10000 synthetic OCR receipts through the normalizer`() {
        val texts = (0 until 10_000).map { SyntheticReceipts.ocrText(Random(it)) }
        val before = usedMb()
        var chars = 0L
        val ms = measureTimeMillis { texts.forEach { chars += OcrTextNormalizer.normalize(it).length } }
        println("STRESS [ocr-normalize] 10000 receipts (${texts.sumOf { it.length } / 1000} k chars in) → ${chars / 1000} k chars out in ${ms}ms (${ms * 1000 / 10_000} µs/receipt), heap ${before}→${usedMb()} MB")
        assertThat(ms).isLessThan(10_000)
    }

    @Test
    fun `pathological inputs do not blow up`() {
        val cases = listOf(
            "O".repeat(50_000), "1,".repeat(20_000), " .".repeat(20_000), "​".repeat(50_000), "₹".repeat(30_000),
            (0 until 5_000).joinToString("\n") { "line $it 1O.OO" },
        )
        val ms = measureTimeMillis { cases.forEach { assertThat(OcrTextNormalizer.normalize(it).length).isAtMost(OcrTextNormalizer.MAX_CHARS) } }
        println("STRESS [ocr-pathological] ${cases.size} adversarial inputs normalised in ${ms}ms")
        assertThat(ms).isLessThan(5_000)
    }

    @Test
    fun `1000 concurrent image imports into the store`() = runBlocking {
        val store = FakeReceiptImageStore()
        val ms = measureTimeMillis {
            (0 until 1_000).map { i -> async(Dispatchers.IO) { store.import("content://media/$i") } }.awaitAll()
        }
        val files = store.newCaptureFile().parentFile!!.listFiles().orEmpty()
        assertThat(files).hasLength(1_000)
        println("STRESS [image-store] 1000 concurrent imports in ${ms}ms")
        files.forEach { it.delete() }
    }
}

/** Deterministic fake receipts with OCR-style noise, shared by the parser stress tests. */
object SyntheticReceipts {
    private val items = listOf("Paneer Tikka", "Dal Tadka", "Butter Naan", "Jeera Rice", "Masala Dosa", "Filter Coffee", "Gulab Jamun", "Veg Biryani", "Chole Bhature", "Lassi")
    private val noise = listOf("", "  ", "----", "* * *", "GSTIN 27AAAAA0000A1Z5", "Thank you! Visit again", "Table 7   Bill #4521")

    fun ocrText(random: Random): String {
        val sb = StringBuilder()
        sb.append("  Hotel Saravana Bhavan \n")
        repeat(random.nextInt(2, 15)) {
            val qty = random.nextInt(1, 5)
            val unit = random.nextInt(2_000, 60_000)
            val total = qty * unit
            val price = "${total / 100}.${(total % 100).toString().padStart(2, '0')}"
            val noisy = if (random.nextBoolean()) price.replace('0', 'O') else price
            sb.append(items.random(random)).append(if (random.nextBoolean()) "  x$qty  " else "   ").append(if (random.nextBoolean()) "₹ " else "").append(noisy).append('\n')
            if (random.nextInt(4) == 0) sb.append(noise.random(random)).append('\n')
        }
        sb.append("Sub Total ").append(random.nextInt(100, 5_000)).append(".00\n")
        sb.append("CGST 2.5% ").append(random.nextInt(1, 200)).append(".50\n")
        sb.append("SGST 2.5% ").append(random.nextInt(1, 200)).append(".50\n")
        sb.append("TOTAL Rs. ").append(random.nextInt(100, 6_000)).append(".OO\n")
        return sb.toString()
    }

    /** A well-formed Gemini-style JSON receipt. */
    fun llmJson(random: Random): String {
        val items = (0 until random.nextInt(1, 12)).map {
            val qty = random.nextInt(1, 4)
            val unit = random.nextInt(1_000, 50_000)
            """{"name":"${this.items.random(random)}","qty":$qty,"unitPaise":$unit,"totalPaise":${qty * unit}}"""
        }
        val subtotal = random.nextInt(1_000, 500_000)
        return """{"items":[${items.joinToString(",")}],"taxPaise":${random.nextInt(0, 5_000)},"tipPaise":0,"totalPaise":$subtotal}"""
    }
}
