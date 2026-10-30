package com.darshan.splitsmart.stress

import com.darshan.splitsmart.data.upi.UpiLinkBuilder
import com.darshan.splitsmart.data.upi.ZxingQrCodeGenerator
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/** Run with `./gradlew stressTest`. */
class UpiStressTest {
    @Test
    fun `10000 links built and parsed back`() {
        val random = Random(42)
        var ok = 0
        val ms = measureTimeMillis {
            repeat(10_000) { i ->
                val link = UpiLinkBuilder.build("user$i@okbank", "Member $i & co ₹", random.nextLong(1, 100_000_000), "Bill $i via SplitSmart")
                val parsed = UpiLinkBuilder.parse(link.uri)
                if (parsed == link) ok++
            }
        }
        println("STRESS [upi-links] 10000 build+parse round trips in ${ms}ms, exact=$ok")
        assertThat(ok).isEqualTo(10_000)
    }

    @Test
    fun `10000 hostile payee names and vpas never leak reserved characters or throw unexpected exceptions`() {
        val random = Random(99)
        val hostile = "abcXYZ019 @.&=?#%/\\\"'<>" + "\u0000\u202E\u20B9\n\t" + "\u00E9\uD83D\uDE00"
        val vpaUser = "abcxyz019._-"
        val vpaHandle = "abcxyz"
        var built = 0
        var rejected = 0
        val reasons = linkedMapOf<String, Int>()
        fun randomString(alphabet: String, min: Int, max: Int) = (0 until random.nextInt(min, max)).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        val ms = measureTimeMillis {
            repeat(10_000) { i ->
                // Half the VPAs are syntactically valid (so the encoding path runs), half are random garbage.
                val vpa = if (i % 2 == 0) randomString(vpaUser, 1, 20) + "@" + randomString(vpaHandle, 2, 8) else randomString(hostile, 0, 30)
                val name = randomString(hostile, 1, 60)
                try {
                    val link = UpiLinkBuilder.build(vpa, name, random.nextLong(-10, 200_000), name)
                    built++
                    val query = link.uri.substringAfter('?')
                    // every value is percent-encoded: no raw reserved characters survive
                    query.split('&').forEach { kv -> assertThat(kv.substringAfter('=')).doesNotContainMatch("[ &=?#\"'<>\\u0000-\\u001F]") }
                    assertThat(UpiLinkBuilder.parse(link.uri)).isEqualTo(link)
                } catch (e: IllegalArgumentException) {
                    rejected++
                    val key = e.message?.substringBefore(':') ?: "?"
                    reasons[key] = (reasons[key] ?: 0) + 1
                }
            }
        }
        println("STRESS [upi-fuzz] 10000 hostile inputs in ${ms}ms: built=$built rejected=$rejected reasons=$reasons")
        assertThat(built + rejected).isEqualTo(10_000)
        assertThat(built).isGreaterThan(1_000) // the valid-VPA half must mostly build (only blank names / bad amounts are rejected)
    }

    @Test
    fun `1000 qr renders`() {
        val generator = ZxingQrCodeGenerator()
        val uris = (0 until 1_000).map { UpiLinkBuilder.build("user$it@okbank", "Member $it", 1_000L + it, "Trip").uri }
        val before = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1_048_576
        var modules = 0L
        val ms = measureTimeMillis { uris.forEach { modules += generator.generate(it).modules.size } }
        val after = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1_048_576
        println("STRESS [qr-1000] 1000 QR codes in ${ms}ms (${ms * 1000 / 1_000} µs each), ${modules / 1_000} modules avg, heap ${before}→${after} MB")
        assertThat(ms).isLessThan(20_000)
    }
}
