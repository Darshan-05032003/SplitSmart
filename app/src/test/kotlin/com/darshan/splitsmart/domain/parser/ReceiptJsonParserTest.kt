package com.darshan.splitsmart.domain.parser

import com.darshan.splitsmart.domain.model.ExtractedItem
import com.darshan.splitsmart.testutil.SAMPLE_LLM_JSON
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReceiptJsonParserTest {
    private val parser = ReceiptJsonParser()

    @Test
    fun `parses the canonical schema`() {
        val r = parser.parse(SAMPLE_LLM_JSON)
        assertThat(r.items).containsExactly(
            ExtractedItem("Paneer Tikka", 1, 25_000, 25_000),
            ExtractedItem("Butter Naan", 2, 4_500, 9_000),
        ).inOrder()
        assertThat(r.taxPaise).isEqualTo(1_700)
        assertThat(r.tipPaise).isEqualTo(0)
        assertThat(r.totalPaise).isEqualTo(35_700)
    }

    @Test
    fun `accepts numbers as strings, decimals as rupees and ignores unknown fields`() {
        val r = parser.parse("""{"items":[{"name":"Dosa","qty":"2","unitPaise":"6000","totalPaise":120.00,"note":"x"}],"taxPaise":"8.50","tipPaise":null,"totalPaise":128.5,"currency":"INR"}""")
        assertThat(r.items.single()).isEqualTo(ExtractedItem("Dosa", 2, 6_000, 12_000))
        assertThat(r.taxPaise).isEqualTo(850)
        assertThat(r.tipPaise).isEqualTo(0)
        assertThat(r.totalPaise).isEqualTo(12_850)
    }

    @Test
    fun `missing fields default to zero and empty`() {
        val r = parser.parse("""{"totalPaise": 500}""")
        assertThat(r.items).isEmpty()
        assertThat(r.totalPaise).isEqualTo(500)
        assertThat(r.taxPaise).isEqualTo(0)
    }

    @Test
    fun `rejects non object, non array items and absurd numbers`() {
        listOf("""[]""", """{"items":"nope"}""", """{"items":[{"name":"x","totalPaise":"abc"}]}""", """{"totalPaise":1e40}""", "not json").forEach {
            assertThat(runCatching { parser.parse(it) }.exceptionOrNull()).isInstanceOf(ReceiptParseException::class.java)
        }
    }

    @Test
    fun `item names are trimmed and capped`() {
        val r = parser.parse("""{"items":[{"name":"  Very long ${"x".repeat(200)}  ","qty":1,"unitPaise":1,"totalPaise":1}],"totalPaise":1}""")
        assertThat(r.items.single().name.length).isAtMost(60)
        assertThat(r.items.single().name).startsWith("Very long")
    }
}
