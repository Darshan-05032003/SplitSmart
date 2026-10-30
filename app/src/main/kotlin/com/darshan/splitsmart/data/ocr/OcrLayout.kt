package com.darshan.splitsmart.data.ocr

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Re-assembles ML Kit lines into printed rows. ML Kit returns a receipt's columns as separate blocks (all item names,
 * then all amounts), and sorting by `top` alone interleaves "Paneer Tikka" and "320.00" in whatever order their boxes
 * happen to start. This groups lines whose vertical centres lie within half a line height of each other into one row
 * and orders each row left to right, so "Paneer Tikka 1 320.00 320.00" comes out as one line again.
 *
 * Photos are rarely straight: centres are first rotated by the median line angle (deskew) so a 4° tilted receipt does
 * not merge neighbouring rows. Without size information it falls back to top → left order.
 */
object OcrLayout {
    /** Two lines are on the same printed row when their deskewed centres differ by at most this × the median height. */
    const val ROW_TOLERANCE = 0.5

    fun assemble(lines: List<OcrLine>): String {
        val usable = lines.filter { it.text.isNotBlank() }
        if (usable.isEmpty()) return ""
        if (usable.any { it.height <= 0 }) {
            return usable.sortedWith(compareBy({ it.top }, { it.left })).joinToString("\n") { it.text }
        }
        val theta = Math.toRadians(median(usable.map { it.angle.toDouble() }))
        val (s, c) = sin(theta) to cos(theta)
        val placed = usable.map { line ->
            val cx = line.left + line.width / 2.0
            val cy = line.top + line.height / 2.0
            Placed(line.text, x = cx * c + cy * s, y = cy * c - cx * s)
        }.sortedBy { it.y }
        val tolerance = ROW_TOLERANCE * median(usable.map { it.height.toDouble() })
        val rows = mutableListOf<MutableList<Placed>>()
        for (p in placed) {
            val row = rows.lastOrNull()
            if (row != null && abs(p.y - row.sumOf { it.y } / row.size) <= tolerance) row += p else rows += mutableListOf(p)
        }
        return rows.joinToString("\n") { row -> row.sortedBy { it.x }.joinToString(" ") { it.text.trim() } }
    }

    private data class Placed(val text: String, val x: Double, val y: Double)

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }
}
