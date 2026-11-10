package com.darshan.splitsmart.ui.split

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.darshan.splitsmart.domain.model.SplitConfig
import com.darshan.splitsmart.domain.model.SplitMode
import com.darshan.splitsmart.testutil.testItem
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

class SplitLogicTest {
    private val items = listOf(testItem("i1", unitPaise = 200), testItem("i2", unitPaise = 100, sortOrder = 1), testItem("i3", unitPaise = 50, sortOrder = 2))
    private val everyone = SplitConfig(SplitMode.BY_ITEM, listOf("a", "b", "c"))

    @Test
    fun `mode options map onto the stored mode and back, percent being shares entered as percentages`() {
        assertThat(SplitModeOption.entries.map { it.mode }).containsExactly(SplitMode.EQUAL, SplitMode.BY_ITEM, SplitMode.BY_SHARE, SplitMode.BY_SHARE).inOrder()
        assertThat(SplitModeOption.of(SplitConfig(SplitMode.EQUAL, emptyList()))).isEqualTo(SplitModeOption.Equal)
        assertThat(SplitModeOption.of(SplitConfig(SplitMode.BY_ITEM, emptyList()))).isEqualTo(SplitModeOption.ByItem)
        assertThat(SplitModeOption.of(SplitConfig(SplitMode.BY_SHARE, emptyList()))).isEqualTo(SplitModeOption.ByShare)
        assertThat(SplitModeOption.of(SplitConfig(SplitMode.BY_SHARE, emptyList(), percent = true))).isEqualTo(SplitModeOption.Percent)
        // A percent flag left on another mode means nothing.
        assertThat(SplitModeOption.of(SplitConfig(SplitMode.EQUAL, emptyList(), percent = true))).isEqualTo(SplitModeOption.Equal)
        assertThat(SplitModeOption.Percent.apply(SplitConfig(SplitMode.EQUAL, listOf("a")))).isEqualTo(SplitConfig(SplitMode.BY_SHARE, listOf("a"), percent = true))
        assertThat(SplitModeOption.ByShare.apply(SplitConfig(SplitMode.BY_SHARE, listOf("a"), percent = true)).percent).isFalse()
    }

    @Test
    fun `percent left counts only participants and is null outside percent mode`() {
        val config = SplitConfig(SplitMode.BY_SHARE, listOf("a", "b"), shares = mapOf("a" to 60, "b" to 30, "gone" to 10), percent = true)
        assertThat(SplitLogic.percentLeft(config)).isEqualTo(10)
        assertThat(SplitLogic.percentLeft(config.copy(shares = mapOf("a" to 80, "b" to 30)))).isEqualTo(-10)
        assertThat(SplitLogic.percentLeft(config.copy(shares = mapOf("a" to 70, "b" to 30)))).isEqualTo(0)
        assertThat(SplitLogic.percentLeft(config.copy(percent = false))).isNull()
        assertThat(SplitLogic.percentLeft(config.copy(mode = SplitMode.EQUAL))).isNull()
    }

    @Test
    fun `even percentages always add up to 100, odd counts included`() {
        assertThat(SplitLogic.evenPercents(listOf("a", "b", "c"))).containsExactly("a", "34", "b", "33", "c", "33").inOrder()
        for (n in 1..40) {
            val ids = (1..n).map { "m$it" }
            assertThat(SplitLogic.evenPercents(ids).values.sumOf { it.toInt() }).isEqualTo(100)
        }
        assertThat(SplitLogic.evenPercents(emptyList())).isEmpty()
    }

    @Test
    fun `assignment counts, unassigned items and the everything-assigned moment`() {
        assertThat(SplitLogic.unassignedCount(items, everyone)).isEqualTo(3)
        assertThat(SplitLogic.allAssigned(items, everyone)).isFalse()
        var config = SplitLogic.assign(everyone, listOf("i1", "i2"), "a")
        config = SplitLogic.assign(config, listOf("i2"), "b")
        assertThat(SplitLogic.assignmentCounts(items, config)).containsExactly("a", 2, "b", 1, "c", 0)
        assertThat(SplitLogic.unassignedCount(items, config)).isEqualTo(1)
        config = SplitLogic.assign(config, listOf("i3"), "c")
        assertThat(SplitLogic.allAssigned(items, config)).isTrue()
        // An assignee who left the split no longer owns the item.
        val withoutC = config.copy(participantIds = listOf("a", "b"))
        assertThat(SplitLogic.unassignedCount(items, withoutC)).isEqualTo(1)
        assertThat(SplitLogic.allAssigned(emptyList(), everyone)).isFalse()
    }

    @Test
    fun `assigning adds, never toggles off, and ignores people outside the split`() {
        val once = SplitLogic.assign(everyone, listOf("i1"), "a")
        assertThat(SplitLogic.assign(once, listOf("i1"), "a")).isEqualTo(once)
        assertThat(SplitLogic.assign(once, listOf("i1"), "b").itemAssignments["i1"]).containsExactly("a", "b")
        assertThat(SplitLogic.assign(everyone, listOf("i1"), "stranger")).isEqualTo(everyone)
        assertThat(SplitLogic.assign(everyone, emptyList(), "a")).isEqualTo(everyone)
    }

    @Test
    fun `share steps stay between 0 and the field's six digits`() {
        assertThat(SplitLogic.stepShare("2", 1)).isEqualTo("3")
        assertThat(SplitLogic.stepShare("0", -1)).isEqualTo("0")
        assertThat(SplitLogic.stepShare("abc", 1)).isEqualTo("1")
        assertThat(SplitLogic.stepShare("999999", 1)).isEqualTo("999999")
        assertThat(SplitLogic.stepShare(" 7 ", -2)).isEqualTo("5")
    }

    @Test
    fun `donut keeps the people in order and folds the smallest into one slice past the cap`() {
        val slices = DonutLogic.slices(listOf("a" to 300L, "b" to 100L, "c" to 0L))
        assertThat(slices.map { it.key }).containsExactly("a", "b", "c").inOrder()
        assertThat(slices.map { it.fraction }).containsExactly(0.75f, 0.25f, 0f).inOrder()
        val many = (1..200).map { "m$it" to it.toLong() }
        val capped = DonutLogic.slices(many)
        assertThat(capped).hasSize(DonutLogic.MAX_SLICES)
        assertThat(capped.last().key).isEqualTo(DonutLogic.OTHERS)
        assertThat(capped.dropLast(1).map { it.key }).containsExactly("m194", "m195", "m196", "m197", "m198", "m199", "m200").inOrder()
        assertThat(capped.sumOf { it.fraction.toDouble() }).isWithin(1e-4).of(1.0)
        assertThat(DonutLogic.slices(listOf("a" to 0L))).isEmpty()
        assertThat(DonutLogic.slices(emptyList())).isEmpty()
    }

    @Test
    fun `donut morph aligns both sets of slices and lays arcs out without gaps when one slice is left`() {
        val from = DonutLogic.slices(listOf("a" to 1L, "b" to 1L))
        val to = DonutLogic.slices(listOf("b" to 1L, "c" to 3L))
        val aligned = DonutLogic.align(from, to)
        assertThat(aligned.keys).containsExactly("b", "c", "a").inOrder()
        assertThat(aligned.from.toList()).containsExactly(0.5f, 0f, 0.5f).inOrder()
        assertThat(aligned.to.toList()).containsExactly(0.25f, 0.75f, 0f).inOrder()

        val out = FloatArray(aligned.keys.size * 2)
        DonutLogic.layout(aligned.from, aligned.to, 1f, gapDegrees = 4f, out = out)
        assertThat(out[0]).isWithin(1e-3f).of(-90f + 2f)
        assertThat(out[1]).isWithin(1e-3f).of(90f - 4f)
        assertThat(out[3]).isWithin(1e-3f).of(270f - 4f)
        assertThat(out[5]).isEqualTo(0f)
        DonutLogic.layout(aligned.from, aligned.to, 0.5f, gapDegrees = 4f, out = out)
        assertThat(out[1] + out[3] + out[5]).isWithin(1e-2f).of(360f - 3 * 4f)

        val single = DonutLogic.align(emptyList(), DonutLogic.slices(listOf("a" to 5L)))
        val one = FloatArray(2)
        DonutLogic.layout(single.from, single.to, 1f, gapDegrees = 4f, out = one)
        assertThat(one[1]).isEqualTo(360f)
    }

    @Test
    fun `donut layout never allocates a negative sweep, whatever the fractions`() {
        val random = Random(7)
        repeat(1_000) {
            val n = random.nextInt(1, 9)
            val a = FloatArray(n) { random.nextFloat() }.let { arr -> val s = arr.sum(); FloatArray(n) { arr[it] / s } }
            val b = FloatArray(n) { random.nextFloat() }.let { arr -> val s = arr.sum(); FloatArray(n) { arr[it] / s } }
            val out = FloatArray(n * 2)
            DonutLogic.layout(a, b, random.nextFloat(), gapDegrees = 3f, out = out)
            for (i in 0 until n) assertThat(out[i * 2 + 1]).isAtLeast(0f)
        }
    }

    @Test
    fun `a drop lands on the face under the finger, with a little slop, nearest centre first`() {
        val targets = mapOf("a" to Rect(0f, 0f, 100f, 100f), "b" to Rect(110f, 0f, 210f, 100f))
        assertThat(DropTargets.hit(Offset(50f, 50f), targets)).isEqualTo("a")
        assertThat(DropTargets.hit(Offset(160f, 90f), targets)).isEqualTo("b")
        assertThat(DropTargets.hit(Offset(105f, 50f), targets)).isNull()
        assertThat(DropTargets.hit(Offset(104f, 50f), targets, slop = 8f)).isEqualTo("a")
        assertThat(DropTargets.hit(Offset(106f, 50f), targets, slop = 8f)).isEqualTo("b")
        assertThat(DropTargets.hit(Offset(500f, 500f), targets, slop = 8f)).isNull()
        assertThat(DropTargets.hit(Offset.Unspecified, targets)).isNull()
        assertThat(DropTargets.hit(Offset(1f, 1f), emptyMap())).isNull()
    }
}
