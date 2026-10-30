package com.darshan.splitsmart.data.ocr

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * Regression for the emulator run on 2026-10-30: ML Kit returned the item-name column and the amount column as separate
 * blocks, and sorting by `top` produced "320.00 320.00" above "Paneer Tikka" and swapped rows further down.
 */
class OcrLayoutTest {
    // Boxes as ML Kit reported them for the Spice Garden receipt (heights ~34 px, numbers start 1-3 px earlier than names).
    private val columns = listOf(
        OcrLine("SPICE GARDEN RESTAURANT", top = 100, left = 180, width = 400, height = 38),
        OcrLine("Item", top = 300, left = 40, width = 70, height = 34),
        OcrLine("Qty Rate Amount", top = 298, left = 330, width = 360, height = 34),
        OcrLine("Paneer Tikka", top = 352, left = 40, width = 230, height = 34),
        OcrLine("Butter Naan", top = 394, left = 40, width = 210, height = 34),
        OcrLine("1 320.00 320.00", top = 350, left = 370, width = 330, height = 34),
        OcrLine("4 60.00 240.00", top = 392, left = 370, width = 330, height = 34),
        OcrLine("Sub Total", top = 470, left = 40, width = 170, height = 34),
        OcrLine("1,910.00", top = 469, left = 560, width = 140, height = 34),
        OcrLine("GRAND TOTAL", top = 520, left = 40, width = 210, height = 38),
        OcrLine("Rs 2,101.00", top = 521, left = 500, width = 200, height = 38),
    )

    @Test
    fun `columns of one printed row are joined left to right`() {
        assertThat(OcrResult(columns).text.lines()).containsExactly(
            "SPICE GARDEN RESTAURANT",
            "Item Qty Rate Amount",
            "Paneer Tikka 1 320.00 320.00",
            "Butter Naan 4 60.00 240.00",
            "Sub Total 1,910.00",
            "GRAND TOTAL Rs 2,101.00",
        ).inOrder()
    }

    @Test
    fun `a tilted photo is deskewed by the median line angle before grouping rows`() {
        val angle = -4.0 // photo rotated 4° counter-clockwise: the right column sits higher than its row's name
        val t = Math.toRadians(angle)
        fun rotated(line: OcrLine): OcrLine {
            val cx = line.left + line.width / 2.0
            val cy = line.top + line.height / 2.0
            val nx = cx * cos(t) - cy * sin(t)
            val ny = cx * sin(t) + cy * cos(t)
            return line.copy(left = (nx - line.width / 2).toInt(), top = (ny - line.height / 2).toInt(), angle = angle.toFloat())
        }
        val tilted = columns.map(::rotated)
        // sanity: without deskew the amount of a row is closer to the previous row's name than to its own
        assertThat(OcrLayout.assemble(tilted.map { it.copy(angle = 0f) }).lines()).doesNotContain("Paneer Tikka 1 320.00 320.00")
        assertThat(OcrLayout.assemble(tilted).lines()).containsAtLeast("Paneer Tikka 1 320.00 320.00", "Butter Naan 4 60.00 240.00", "GRAND TOTAL Rs 2,101.00").inOrder()
    }

    @Test
    fun `without box sizes the old top-then-left order is kept`() {
        val lines = listOf(OcrLine("b", 10, 5), OcrLine("a", 10, 0), OcrLine("c", 0, 0))
        assertThat(OcrResult(lines).text).isEqualTo("c\na\nb")
    }

    @Test
    fun `blank lines are dropped and empty input is empty`() {
        assertThat(OcrLayout.assemble(emptyList())).isEmpty()
        assertThat(OcrLayout.assemble(listOf(OcrLine("  ", 0, 0, 10, 10), OcrLine("Chai 20.00", 20, 0, 100, 10)))).isEqualTo("Chai 20.00")
    }

    @Test
    fun `separate rows stay separate even when tightly spaced`() {
        val lines = (0 until 5).map { OcrLine("row $it", top = it * 36, left = 0, width = 100, height = 34) }
        assertThat(OcrLayout.assemble(lines).lines()).hasSize(5)
    }
}
