package com.darshan.splitsmart.domain.model

import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testItem
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BillTest {
    @Test
    fun `item subtotal is the sum of item totals`() {
        val bill = testBill(items = listOf(testItem(id = "a", unitPaise = 100, qty = 2), testItem(id = "b", unitPaise = 50)))
        assertThat(bill.itemsSubtotalPaise).isEqualTo(250)
    }

    @Test
    fun `bill rejects negative amounts`() {
        assertThat(runCatching { testBill(totalPaise = -1) }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(runCatching { testBill(taxPaise = -1) }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(runCatching { testBill(tipPaise = -1) }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `bill rejects totals above the cap and blank titles`() {
        assertThat(runCatching { testBill(totalPaise = Long.MAX_VALUE) }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(runCatching { testBill(title = " ") }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `item rejects non positive quantity, negative price and inconsistent total`() {
        assertThat(runCatching { testItem(qty = 0) }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(runCatching { testItem(unitPaise = -5) }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(
            runCatching { BillItem("i", "b", "x", qty = 2, unitPaise = 100, totalPaise = 150, sortOrder = 0) }.exceptionOrNull(),
        ).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `item total may differ from qty times unit only when unit is unknown`() {
        val item = BillItem("i", "b", "x", qty = 3, unitPaise = 0, totalPaise = 299, sortOrder = 0)
        assertThat(item.totalPaise).isEqualTo(299)
    }

    @Test
    fun `tooManyItems is rejected`() {
        val items = (0 until Bill.MAX_ITEMS + 1).map { testItem(id = "i$it", sortOrder = it) }
        assertThat(runCatching { testBill(items = items) }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }
}
