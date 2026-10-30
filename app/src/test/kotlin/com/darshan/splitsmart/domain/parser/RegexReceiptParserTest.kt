package com.darshan.splitsmart.domain.parser

import com.darshan.splitsmart.domain.model.ExtractedItem
import com.darshan.splitsmart.testutil.SAMPLE_OCR_TEXT
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RegexReceiptParserTest {
    @Test
    fun `parses a typical indian restaurant receipt`() {
        val r = RegexReceiptParser.parse(SAMPLE_OCR_TEXT)
        assertThat(r.items).containsExactly(
            ExtractedItem("Paneer Tikka", 1, 25_000, 25_000),
            ExtractedItem("Butter Naan", 2, 4_500, 9_000),
        ).inOrder()
        assertThat(r.taxPaise).isEqualTo(1_700)
        assertThat(r.tipPaise).isEqualTo(0)
        assertThat(r.totalPaise).isEqualTo(35_700)
    }

    @Test
    fun `understands quantity formats`() {
        val r = RegexReceiptParser.parse("2 x Masala Dosa 240.00\nFilter Coffee x 3 120.00\nLassi 2 @ 60.00 120.00\nIdli (2 pcs) 60.00\nTotal 540.00")
        assertThat(r.items).containsExactly(
            ExtractedItem("Masala Dosa", 2, 12_000, 24_000),
            ExtractedItem("Filter Coffee", 3, 4_000, 12_000),
            ExtractedItem("Lassi", 2, 6_000, 12_000),
            ExtractedItem("Idli (2 pcs)", 1, 6_000, 6_000),
        ).inOrder()
        assertThat(r.totalPaise).isEqualTo(54_000)
    }

    @Test
    fun `grand total beats sub total and tax lines are summed`() {
        val r = RegexReceiptParser.parse("Thali 200.00\nSub Total 200.00\nGST 5% 10.00\nService Charge 10.00\nGrand Total 220.00\nAmount Paid 220.00")
        assertThat(r.items.map { it.name }).containsExactly("Thali")
        assertThat(r.taxPaise).isEqualTo(2_000)
        assertThat(r.totalPaise).isEqualTo(22_000)
    }

    @Test
    fun `tip and gratuity lines are tips`() {
        val r = RegexReceiptParser.parse("Burger 300.00\nTip 30.00\nTotal 330.00")
        assertThat(r.tipPaise).isEqualTo(3_000)
        assertThat(r.totalPaise).isEqualTo(33_000)
    }

    @Test
    fun `skips headers, ids, dates, phone numbers and discount lines`() {
        val r = RegexReceiptParser.parse(
            "Cafe Coffee Day\nGSTIN 27AAAAA0000A1Z5\nBill No 4521 Date 12/03/2026\nPh 9876543210\nItem Qty Rate Amount\nCappuccino 180.00\nDiscount -20.00\nRound off 0.40\nTotal 160.40\nThank you visit again",
        )
        assertThat(r.items).containsExactly(ExtractedItem("Cappuccino", 1, 18_000, 18_000))
        assertThat(r.totalPaise).isEqualTo(16_040)
    }

    @Test
    fun `without a total line the total is derived from items`() {
        val r = RegexReceiptParser.parse("Chai 20.00\nSamosa 15.00")
        assertThat(r.totalPaise).isEqualTo(3_500)
    }

    @Test
    fun `empty or garbage text yields an empty receipt`() {
        assertThat(RegexReceiptParser.parse("")).isEqualTo(RegexReceiptParser.EMPTY)
        assertThat(RegexReceiptParser.parse("lorem ipsum dolor\n----\n****")).isEqualTo(RegexReceiptParser.EMPTY)
    }

    @Test
    fun `huge and hostile amounts are ignored`() {
        val r = RegexReceiptParser.parse("Gold Bar 99999999999.00\nChai 20.00\nTotal 99999999999.00")
        assertThat(r.items.map { it.name }).containsExactly("Chai")
        assertThat(r.totalPaise).isEqualTo(2_000)
    }

    @Test
    fun `handles unicode item names and amounts without decimals`() {
        val r = RegexReceiptParser.parse("पनीर टिक्का 250\nCafé Latte 180\nTotal 430")
        assertThat(r.items.map { it.name }).containsExactly("पनीर टिक्का", "Café Latte").inOrder()
        assertThat(r.totalPaise).isEqualTo(43_000)
    }
}
