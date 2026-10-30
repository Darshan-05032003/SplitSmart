package com.darshan.splitsmart.domain.parser

import com.darshan.splitsmart.core.Money
import com.darshan.splitsmart.domain.model.ExtractedItem
import com.darshan.splitsmart.domain.model.ExtractedReceipt
import com.darshan.splitsmart.domain.model.ExtractionWarning
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReceiptValidatorTest {
    private fun item(name: String = "Item", qty: Int = 1, unit: Long = 100, total: Long = unit * qty) = ExtractedItem(name, qty, unit, total)

    @Test
    fun `clean receipt passes without warnings`() {
        val r = ExtractedReceipt(listOf(item(total = 100), item("B", 2, 50, 100)), taxPaise = 10, tipPaise = 0, totalPaise = 210)
        val v = ReceiptValidator.validate(r)!!
        assertThat(v.receipt).isEqualTo(r)
        assertThat(v.warnings).isEmpty()
    }

    @Test
    fun `negative and absurd amounts are dropped`() {
        val r = ExtractedReceipt(listOf(item(total = -5, unit = -5), item("Huge", 1, Money.MAX_PAISE + 1, Money.MAX_PAISE + 1), item("Ok", 1, 100)), taxPaise = -1, tipPaise = 0, totalPaise = 100)
        val v = ReceiptValidator.validate(r)!!
        assertThat(v.receipt.items.map { it.name }).containsExactly("Ok")
        assertThat(v.receipt.taxPaise).isEqualTo(0)
        assertThat(v.warnings).containsAtLeast(ExtractionWarning.NEGATIVE_DROPPED, ExtractionWarning.ABSURD_DROPPED)
    }

    @Test
    fun `item math is repaired towards the line total`() {
        val v = ReceiptValidator.validate(ExtractedReceipt(listOf(item("A", 2, 100, 250), item("B", 3, 0, 300), item("C", 2, 100, 0)), 0, 0, 550))!!
        assertThat(v.receipt.items[0]).isEqualTo(ExtractedItem("A", 2, 0, 250))     // unit cannot explain total → unit unknown
        assertThat(v.receipt.items[1]).isEqualTo(ExtractedItem("B", 3, 100, 300))   // unit derived from divisible total
        assertThat(v.receipt.items[2]).isEqualTo(ExtractedItem("C", 2, 100, 200))   // total derived from unit × qty
        assertThat(v.warnings).contains(ExtractionWarning.ITEM_MATH_FIXED)
    }

    @Test
    fun `blank names and bad quantities are fixed`() {
        val v = ReceiptValidator.validate(ExtractedReceipt(listOf(item("  ", 0, 100), item("B", 5_000, 1, 5_000)), 0, 0, 5_100))!!
        assertThat(v.receipt.items[0].name).isEqualTo("Item 1")
        assertThat(v.receipt.items[0].qty).isEqualTo(1)
        assertThat(v.receipt.items[1].qty).isEqualTo(999)
        assertThat(v.warnings).containsAtLeast(ExtractionWarning.NAME_FILLED, ExtractionWarning.QTY_FIXED)
    }

    @Test
    fun `items are capped at 200`() {
        val items = (1..250).map { item("I$it", 1, 100) }
        val v = ReceiptValidator.validate(ExtractedReceipt(items, 0, 0, 25_000))!!
        assertThat(v.receipt.items).hasSize(200)
        assertThat(v.warnings).contains(ExtractionWarning.ITEMS_CAPPED)
    }

    @Test
    fun `missing total is derived, mismatching total is flagged but kept`() {
        val derived = ReceiptValidator.validate(ExtractedReceipt(listOf(item(total = 100)), 10, 5, 0))!!
        assertThat(derived.receipt.totalPaise).isEqualTo(115)
        assertThat(derived.warnings).contains(ExtractionWarning.TOTAL_DERIVED)

        val mismatch = ReceiptValidator.validate(ExtractedReceipt(listOf(item(total = 100)), 10, 5, 200))!!
        assertThat(mismatch.receipt.totalPaise).isEqualTo(200)
        assertThat(mismatch.warnings).contains(ExtractionWarning.SUM_MISMATCH)
    }

    @Test
    fun `receipt with nothing usable is rejected`() {
        assertThat(ReceiptValidator.validate(ExtractedReceipt(emptyList(), 0, 0, 0))).isNull()
        assertThat(ReceiptValidator.validate(ExtractedReceipt(listOf(item(total = -1, unit = -1)), 0, 0, -1))).isNull()
        assertThat(ReceiptValidator.validate(ExtractedReceipt(emptyList(), 0, 0, Money.MAX_PAISE + 1))).isNull()
    }

    @Test
    fun `total only receipt is accepted`() {
        val v = ReceiptValidator.validate(ExtractedReceipt(emptyList(), 0, 0, 5_000))!!
        assertThat(v.receipt.totalPaise).isEqualTo(5_000)
    }
}
