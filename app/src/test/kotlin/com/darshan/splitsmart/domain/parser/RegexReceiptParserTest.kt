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

    /** OCR text exactly as ML Kit + OcrLayout produced it on the emulator (2026-10-31); the "2" of the first row was lost. */
    private val mainlandChina = """MAINLAND CHINA
Phoenix Market city, Viman Nagar
GSTIN 27AABCM4455Q1Z9
KOT 118 Table 22 Covers 4
31/10/2026 20:47
Item Qty Rate Amount
Veg Manchow Sou 160.00 320.00
Chilli Paneer 1 380.00 380.00
Hakka Noodles 2 290.00 580.00
Veg Fried Rice 1 270.00 270.00
Crispy Corn 1 260.00 260.00
Spring Rolls 1 240.00 240.00
Fresh Lime Soda 3 110.00 330.00
Brownie Sizzler 1 320.00 320.00
Sub Total 2700.00
Discount 10% -270.00
CGST 2.5% 60.75
SGST 2.5% 60.75
Service Charge 5% 121.50
Round Off 0.00
NET PAYABLE 2673.00
Thank you for dining with us"""

    @Test
    fun `emulator regression - a dropped qty column is recovered from rate and amount`() {
        val r = RegexReceiptParser.parse(mainlandChina)
        assertThat(r.items).hasSize(8)
        assertThat(r.items[0]).isEqualTo(ExtractedItem("Veg Manchow Sou", 2, 16_000, 32_000))
        assertThat(r.items[1]).isEqualTo(ExtractedItem("Chilli Paneer", 1, 38_000, 38_000))
        assertThat(r.items[6]).isEqualTo(ExtractedItem("Fresh Lime Soda", 3, 11_000, 33_000))
        assertThat(r.itemsSubtotalPaise).isEqualTo(270_000)
        assertThat(r.taxPaise).isEqualTo(24_300)
        assertThat(r.tipPaise).isEqualTo(0)
        assertThat(r.totalPaise).isEqualTo(267_300) // net payable after the discount line (which is skipped)
    }

    @Test
    fun `a trailing number that does not divide the amount stays part of the line`() {
        val r = RegexReceiptParser.parse("Combo Meal 333.33 500.00")
        assertThat(r.items.single()).isEqualTo(ExtractedItem("Combo Meal 333.33", 1, 50_000, 50_000))
    }
}
