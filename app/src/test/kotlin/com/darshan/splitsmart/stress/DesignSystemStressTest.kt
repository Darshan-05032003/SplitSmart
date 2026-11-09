package com.darshan.splitsmart.stress

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.ui.designsystem.FakeHaptics
import com.darshan.splitsmart.ui.designsystem.assertInside
import com.darshan.splitsmart.ui.designsystem.commitState
import com.darshan.splitsmart.ui.designsystem.components.AmountFormat
import com.darshan.splitsmart.ui.designsystem.components.AmountPill
import com.darshan.splitsmart.ui.designsystem.components.AmountTone
import com.darshan.splitsmart.ui.designsystem.components.AvatarLogic
import com.darshan.splitsmart.ui.designsystem.components.AvatarStack
import com.darshan.splitsmart.ui.designsystem.components.BumpCounter
import com.darshan.splitsmart.ui.designsystem.components.ChunkyButton
import com.darshan.splitsmart.ui.designsystem.components.ClickGuard
import com.darshan.splitsmart.ui.designsystem.components.ConfettiPhysics
import com.darshan.splitsmart.ui.designsystem.components.MemberAvatar
import com.darshan.splitsmart.ui.designsystem.components.RollingAmount
import com.darshan.splitsmart.ui.designsystem.components.RollingAmountLogic
import com.darshan.splitsmart.ui.designsystem.components.SnackbarResult
import com.darshan.splitsmart.ui.designsystem.components.SsListRow
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.darshan.splitsmart.ui.designsystem.components.AmountDecimals
import com.darshan.splitsmart.ui.designsystem.components.AmountSign
import com.darshan.splitsmart.ui.designsystem.effects.HapticEvent
import com.darshan.splitsmart.ui.designsystem.motion.MotionLevel
import com.darshan.splitsmart.ui.designsystem.motion.resolveMotionLevel
import com.darshan.splitsmart.ui.designsystem.recompositionCycles
import com.darshan.splitsmart.ui.designsystem.setDs
import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Design-system load simulations. Run with `./gradlew stressTest`; each prints a `STRESS [...]` line for LOAD_TEST_REPORT. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class DesignSystemStressTest {
    @get:Rule val rule = createComposeRule()

    private fun usedMb() = Runtime.getRuntime().let { (it.totalMemory() - it.freeMemory()) / 1_048_576 }

    @Test
    fun `1000 rapid taps on a settle button run one settlement`() {
        val haptics = FakeHaptics()
        var settlements = 0
        var loading by mutableStateOf(false)
        rule.setDs(haptics = haptics) {
            ChunkyButton("Settle up", { settlements++; loading = true }, Modifier.testTag("settle"), loading = loading)
        }
        val ms = measureTimeMillis { repeat(1_000) { rule.onNodeWithTag("settle").performClick() } }
        rule.waitForIdle()
        println("STRESS [ds-taps-1000] 1000 injected taps → $settlements settlement(s), ${haptics.events.size} haptic(s), ${ms}ms")
        assertThat(settlements).isEqualTo(1)
        assertThat(haptics.events).containsExactly(HapticEvent.Light)
    }

    @Test
    fun `a million taps in one instant pass the guard once`() {
        val guard = ClickGuard()
        var accepted = 0
        val ns = measureNanoTime { repeat(1_000_000) { if (guard.tryAccept(1_000)) accepted++ } }
        println("STRESS [ds-guard-1m] 1,000,000 same-instant taps → $accepted accepted in ${ns / 1_000_000}ms")
        assertThat(accepted).isEqualTo(1)
    }

    @Test
    fun `a 200-member stack composes five faces and one sentence`() {
        val names = (1..200).map { "Member $it" }
        rule.setDs { AvatarStack(names, Modifier.testTag("stack")) }
        rule.onNodeWithContentDescription("Member 1, Member 2, Member 3, Member 4 and 196 more").assertExists()
        val layouts = measureTimeMillis {
            repeat(10_000) { group ->
                val count = 1 + group % 200
                AvatarLogic.layout(count, 4)
                AvatarLogic.description(names.take(count))
            }
        }
        println("STRESS [ds-avatars-200] 200 members → 5 faces; 10,000 group stacks laid out + described in ${layouts}ms")
        assertThat(layouts).isLessThan(5_000)
    }

    @Test
    fun `10,000 rows scroll with keys and content types`() {
        val rows = (0 until 10_000).map { it to "Bill $it · ${"Friend " + (it % 200)}" }
        lateinit var state: LazyListState
        rule.setDs {
            state = rememberLazyListState()
            LazyColumn(Modifier.fillMaxSize().testTag("list"), state = state) {
                items(rows, key = { it.first }, contentType = { "bill" }) { (id, title) ->
                    SsListRow(title, subtitle = "Paid by Friend ${id % 200}", leading = { MemberAvatar("Friend ${id % 200}") }, trailing = { AmountPill(id * 137L % 99_999_99L, AmountTone.Owed) })
                }
            }
        }
        val before = usedMb()
        val cycles = recompositionCycles()
        val ms = measureTimeMillis {
            repeat(300) { step -> rule.onNodeWithTag("list").performScrollToIndex(step * 33) }
            rule.waitForIdle()
        }
        val composedCycles = recompositionCycles() - cycles
        println("STRESS [ds-list-10k] 300 scroll steps over 10,000 rows in ${ms}ms, reached item ${state.firstVisibleItemIndex}, recomposer cycles $composedCycles, heap $before→${usedMb()} MB")
        assertThat(state.firstVisibleItemIndex).isAtLeast(9_000)
    }

    @Test
    fun `the largest amount rolls 200 times and still fits at 1_3x`() {
        var paise by mutableLongStateOf(0L)
        rule.mainClock.autoAdvance = false
        rule.setDs(fontScale = 1.3f) {
            Box(Modifier.width(300.dp).testTag("card")) { RollingAmount(paise, Modifier.testTag("amount")) }
        }
        val random = Random(9)
        val cycles = recompositionCycles()
        val ms = measureTimeMillis {
            repeat(200) {
                commitState { paise = random.nextLong(0, AmountFormat.MAX_DISPLAY_PAISE) }
                rule.mainClock.advanceTimeBy(32)
            }
            commitState { paise = AmountFormat.MAX_DISPLAY_PAISE }
            rule.mainClock.advanceTimeBy(2_000)
        }
        rule.onNodeWithContentDescription("₹99,99,99,999.99").assertInside(rule.onNodeWithTag("card"))
        val formatMs = measureTimeMillis {
            repeat(100_000) { RollingAmountLogic.glyphs(random.nextLong(-AmountFormat.MAX_DISPLAY_PAISE, AmountFormat.MAX_DISPLAY_PAISE), AmountSign.NegativeOnly, AmountDecimals.Always) }
        }
        println("STRESS [ds-amount-max] 200 interrupted rolls up to ₹99,99,99,999.99 in ${ms}ms (${recompositionCycles() - cycles} recomposer cycles); 100,000 amounts to glyphs in ${formatMs}ms")
    }

    @Test
    fun `1000 counter changes land on the right number`() {
        var count by mutableIntStateOf(0)
        rule.mainClock.autoAdvance = false
        rule.setDs { BumpCounter(count) }
        val ms = measureTimeMillis {
            repeat(1_000) {
                commitState { count++ }
                rule.mainClock.advanceTimeBy(16)
            }
            rule.mainClock.advanceTimeBy(1_000)
        }
        rule.onNodeWithContentDescription("99+").assertExists()
        commitState { count = 7 }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithContentDescription("7").assertExists()
        println("STRESS [ds-counter-1000] 1000 bumps, one per frame, in ${ms}ms; settles on the last value")
    }

    @Test
    fun `1000 undo snackbars leave nothing behind`() = runTest {
        val state = SsSnackbarState()
        var undone = 0
        val before = usedMb()
        val ms = measureTimeMillis {
            repeat(1_000) {
                val result = async { state.show("Bill $it deleted", actionLabel = "Undo") }
                runCurrent()
                state.current!!.performAction()
                state.current?.performAction()
                if (result.await() == SnackbarResult.Undo) undone++
            }
        }
        println("STRESS [ds-undo-1000] 1000 deletes undone (double-tapped Undo) → $undone restores in ${ms}ms, heap $before→${usedMb()} MB, on screen after: ${state.current}")
        assertThat(undone).isEqualTo(1_000)
        assertThat(state.current).isNull()
    }

    @Test
    fun `confetti physics stay cheap`() {
        val out = FloatArray(4)
        var placed = 0
        val ms = measureTimeMillis {
            repeat(1_000) { burst ->
                val particles = ConfettiPhysics.burst(burst.toLong(), 48, 5, 3)
                for (frame in 0 until 96) for (p in particles) {
                    ConfettiPhysics.place(p, frame / 60f, 1.6f, out)
                    placed++
                }
            }
        }
        println("STRESS [ds-confetti] 1000 bursts × 96 frames × 48 pieces = $placed placements in ${ms}ms (${ms * 1_000_000 / placed} ns each)")
        assertThat(ms).isLessThan(10_000)
    }

    @Test
    fun `animator speeds only reduce motion at zero`() {
        val levels = listOf(0f, 0.5f, 1f, 3f, 10f).associateWith { resolveMotionLevel(false, it) }
        println("STRESS [ds-animator-scale] $levels")
        assertThat(levels.filterValues { it == MotionLevel.Reduced }.keys).containsExactly(0f)
    }
}
