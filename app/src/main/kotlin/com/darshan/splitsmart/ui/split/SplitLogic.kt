package com.darshan.splitsmart.ui.split

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import com.darshan.splitsmart.domain.model.BillItem
import com.darshan.splitsmart.domain.model.SplitConfig
import com.darshan.splitsmart.domain.model.SplitMode
import com.darshan.splitsmart.domain.split.LargestRemainder

/**
 * The four ways the screen offers to split. Percent is shares entered as percentages: the same maths as [ByShare], saved only
 * once the numbers total 100, and remembered on the config so the bill reopens in the mode it was split in.
 */
enum class SplitModeOption(val mode: SplitMode, val percent: Boolean, val emoji: String) {
    Equal(SplitMode.EQUAL, false, "⚖️"),
    ByItem(SplitMode.BY_ITEM, false, "🍽️"),
    ByShare(SplitMode.BY_SHARE, false, "🧮"),
    Percent(SplitMode.BY_SHARE, true, "💯"),
    ;

    fun apply(config: SplitConfig): SplitConfig = config.copy(mode = mode, percent = percent)

    companion object {
        fun of(config: SplitConfig): SplitModeOption = when (config.mode) {
            SplitMode.EQUAL -> Equal
            SplitMode.BY_ITEM -> ByItem
            SplitMode.BY_SHARE -> if (config.percent) Percent else ByShare
        }
    }
}

/** Pure rules behind the split screen, kept out of Compose so they are tested on the JVM. */
object SplitLogic {
    const val PERCENT_TOTAL = 100

    /** Largest number a share field holds (six digits). */
    const val MAX_SHARE = 999_999

    /** Percentage points still to give in percent mode (negative when over 100); null in every other mode. */
    fun percentLeft(config: SplitConfig): Int? {
        if (config.mode != SplitMode.BY_SHARE || !config.percent) return null
        return PERCENT_TOTAL - config.participantIds.sumOf { config.shares[it] ?: 0 }
    }

    /** 100 spread as evenly as whole percentages allow (34, 33, 33), so percent mode opens already adding up. */
    fun evenPercents(participantIds: List<String>): Map<String, String> {
        val parts = LargestRemainder.distribute(PERCENT_TOTAL.toLong(), List(participantIds.size) { 1L })
        return participantIds.zip(parts).associate { (id, part) -> id to part.toString() }
    }

    /** How many items each participant has been given by name (an item nobody was given is shared by everyone, not counted). */
    fun assignmentCounts(items: List<BillItem>, config: SplitConfig): Map<String, Int> {
        val counts = LinkedHashMap<String, Int>()
        config.participantIds.forEach { counts[it] = 0 }
        for (item in items) {
            for (id in config.itemAssignments[item.id].orEmpty()) {
                if (id in counts) counts[id] = counts.getValue(id) + 1
            }
        }
        return counts
    }

    /** Items with nobody in the split given them, which are shared by everyone. */
    fun unassignedCount(items: List<BillItem>, config: SplitConfig): Int {
        val inSplit = config.participantIds.toSet()
        return items.count { item -> config.itemAssignments[item.id].orEmpty().none { it in inSplit } }
    }

    /** Every item has an owner: the moment the screen celebrates. A bill without items never gets there. */
    fun allAssigned(items: List<BillItem>, config: SplitConfig): Boolean = items.isNotEmpty() && unassignedCount(items, config) == 0

    /** Gives [itemIds] to [memberId] as well as anyone who already has them. Never takes an item away; people outside the split are ignored. */
    fun assign(config: SplitConfig, itemIds: Collection<String>, memberId: String): SplitConfig {
        if (memberId !in config.participantIds || itemIds.isEmpty()) return config
        val next = config.itemAssignments.toMutableMap()
        var changed = false
        for (id in itemIds) {
            val current = next[id].orEmpty()
            if (memberId !in current) {
                next[id] = current + memberId
                changed = true
            }
        }
        return if (changed) config.copy(itemAssignments = next) else config
    }

    /** The share text one stepper press away: unreadable text counts as 0, and the result stays in 0..[MAX_SHARE]. */
    fun stepShare(text: String, delta: Int): String =
        ((text.trim().toIntOrNull() ?: 0).toLong() + delta).coerceIn(0L, MAX_SHARE.toLong()).toString()
}

/** One slice of the share donut: a person (or everyone past the cap) and their part of the bill. */
@Immutable
data class DonutSlice(val key: String, val fraction: Float)

/** Two sets of slices on the same keys, so one can morph into the other. */
class AlignedSlices(val keys: List<String>, val from: FloatArray, val to: FloatArray)

/**
 * The donut's geometry. Slices keep the people's order so a mode change morphs each person's arc in place; past [MAX_SLICES]
 * the smallest fold into one [OTHERS] slice so 200 people still read as a picture. [layout] writes into a reused array: it runs
 * every frame of a morph.
 */
object DonutLogic {
    const val MAX_SLICES = 8
    const val OTHERS = "+others"
    private const val FULL_TURN = 360f
    private const val TOP = -90f

    fun slices(amounts: List<Pair<String, Long>>, max: Int = MAX_SLICES): List<DonutSlice> {
        val total = amounts.sumOf { it.second.coerceAtLeast(0L) }
        if (total <= 0L) return emptyList()
        val kept: List<Pair<String, Long>>
        val folded: Long
        if (amounts.size > max) {
            val keep = amounts.indices.sortedWith(compareByDescending<Int> { amounts[it].second }.thenBy { it }).take(max - 1).toSet()
            kept = amounts.filterIndexed { i, _ -> i in keep }
            folded = amounts.filterIndexed { i, _ -> i !in keep }.sumOf { it.second.coerceAtLeast(0L) }
        } else {
            kept = amounts
            folded = -1L
        }
        val result = kept.map { (key, paise) -> DonutSlice(key, (paise.coerceAtLeast(0L).toDouble() / total).toFloat()) }
        return if (folded >= 0L) result + DonutSlice(OTHERS, (folded.toDouble() / total).toFloat()) else result
    }

    /** Keys of [to] in order, then keys only [from] had (they shrink away); fractions on those keys, 0 where a side lacks one. */
    fun align(from: List<DonutSlice>, to: List<DonutSlice>): AlignedSlices {
        val keys = to.map { it.key } + from.map { it.key }.filter { key -> to.none { it.key == key } }
        val fromMap = from.associate { it.key to it.fraction }
        val toMap = to.associate { it.key to it.fraction }
        return AlignedSlices(keys, FloatArray(keys.size) { fromMap[keys[it]] ?: 0f }, FloatArray(keys.size) { toMap[keys[it]] ?: 0f })
    }

    /**
     * Arcs at morph progress [t]: `out[2i]` is slice i's start angle (degrees, 0 = three o'clock, starting at twelve), `out[2i + 1]`
     * its sweep. Slices are [gapDegrees] apart unless only one is left, which closes into a full ring. No sweep is negative.
     */
    fun layout(from: FloatArray, to: FloatArray, t: Float, gapDegrees: Float, out: FloatArray) {
        val n = minOf(from.size, to.size)
        var nonZero = 0
        for (i in 0 until n) if (lerp(from[i], to[i], t) > 0f) nonZero++
        val gap = if (nonZero > 1) gapDegrees else 0f
        var total = 0f
        for (i in 0 until n) total += lerp(from[i], to[i], t).coerceAtLeast(0f)
        var cursor = 0f
        for (i in 0 until n) {
            val f = if (total > 0f) lerp(from[i], to[i], t).coerceAtLeast(0f) / total else 0f
            val sweep = f * FULL_TURN
            out[i * 2] = TOP + cursor + gap / 2f
            out[i * 2 + 1] = if (f > 0f) (sweep - gap).coerceAtLeast(0f) else 0f
            cursor += sweep
        }
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}

/** Where a dragged item lands: the face under the finger, its box grown by [slop]; when boxes overlap the nearest centre wins. */
object DropTargets {
    fun hit(point: Offset, targets: Map<String, Rect>, slop: Float = 0f): String? {
        if (!point.isSpecified) return null
        var best: String? = null
        var bestDistance = Float.MAX_VALUE
        for ((id, rect) in targets) {
            if (!rect.inflate(slop).contains(point)) continue
            val distance = (rect.center - point).getDistanceSquared()
            if (distance < bestDistance) {
                bestDistance = distance
                best = id
            }
        }
        return best
    }
}
