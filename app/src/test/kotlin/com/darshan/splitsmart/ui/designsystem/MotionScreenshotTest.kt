package com.darshan.splitsmart.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.ui.designsystem.components.AvatarStack
import com.darshan.splitsmart.ui.designsystem.components.BottomBarItem
import com.darshan.splitsmart.ui.designsystem.components.BumpCounter
import com.darshan.splitsmart.ui.designsystem.components.ChunkyButton
import com.darshan.splitsmart.ui.designsystem.components.ConfettiBurst
import com.darshan.splitsmart.ui.designsystem.components.EmojiChip
import com.darshan.splitsmart.ui.designsystem.components.RollingAmount
import com.darshan.splitsmart.ui.designsystem.components.SkeletonList
import com.darshan.splitsmart.ui.designsystem.components.SsBottomBar
import com.darshan.splitsmart.ui.designsystem.components.SsBottomSheet
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarHost
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.darshan.splitsmart.ui.designsystem.tokens.Sizes
import com.darshan.splitsmart.ui.designsystem.tokens.Spacing
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Frozen frames from the middle of the key motions: what no resting baseline can show. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class MotionScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val tabs = listOf(BottomBarItem("Groups", Icons.Filled.Home), BottomBarItem("Scan", Icons.Filled.Search), BottomBarItem("Me", Icons.Filled.Person))

    private fun frame(dark: Boolean, reduced: Boolean = false, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setDs(dark = dark, reduceMotion = reduced) {
            Box(Modifier.fillMaxWidth().testTag(ROOT).padding(Spacing.screen)) { content() }
        }
    }

    private fun shot(base: String, dark: Boolean, reduced: Boolean = false) =
        rule.onNodeWithTag(ROOT).captureNode("ds_motion_${base}_${if (dark) "dark" else "light"}${if (reduced) "_reduced" else ""}")

    private fun pressed(dark: Boolean) {
        frame(dark) { ChunkyButton("Save split", {}, Modifier.fillMaxWidth().testTag("b"), emoji = "✅") }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag("b").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(64)
        shot("button_squash", dark)
    }

    @Test fun button_squash_light() = pressed(false)
    @Test fun button_squash_dark() = pressed(true)

    @Test
    fun button_stretch_light() {
        frame(false) { ChunkyButton("Save split", {}, Modifier.fillMaxWidth().testTag("b"), emoji = "✅") }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag("b").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(160)
        rule.onNodeWithTag("b").performTouchInput { up() }
        rule.mainClock.advanceTimeBy(80)
        shot("button_stretch", false)
    }

    private fun fanning(dark: Boolean) {
        var expanded by mutableStateOf(false)
        frame(dark) { AvatarStack(listOf("Asha Rao", "Ravi Kumar", "Meera Iyer", "Kabir Shah", "Isha", "Neel"), expanded, { expanded = it }) }
        rule.mainClock.advanceTimeBy(200)
        commitState { expanded = true }
        rule.mainClock.advanceTimeBy(112)
        shot("avatars_fanning", dark)
    }

    @Test fun avatars_fanning_light() = fanning(false)
    @Test fun avatars_fanning_dark() = fanning(true)

    @Test
    fun counter_bump_light() {
        var count by mutableIntStateOf(2)
        frame(false) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                BasicText("Items for Asha", style = SplitSmartTheme.type.bodyStrong.copy(color = SplitSmartTheme.colors.text))
                BumpCounter(count, style = SplitSmartTheme.type.title)
            }
        }
        rule.mainClock.advanceTimeBy(200)
        commitState { count = 3 }
        rule.mainClock.advanceTimeBy(80)
        shot("counter_bump", false)
    }

    private fun rolling(dark: Boolean, reduced: Boolean = false) {
        var paise by mutableLongStateOf(1_118_70L)
        frame(dark, reduced) { RollingAmount(paise) }
        rule.mainClock.advanceTimeBy(500)
        commitState { paise = 2_08_310_75L }
        rule.mainClock.advanceTimeBy(96)
        shot("amount_rolling", dark, reduced)
    }

    @Test fun amount_rolling_light() = rolling(false)
    @Test fun amount_rolling_dark() = rolling(true)
    @Test fun amount_rolling_light_reduced() = rolling(false, reduced = true)

    private fun blob(dark: Boolean, reduced: Boolean = false) {
        var selected by mutableIntStateOf(0)
        frame(dark, reduced) { SsBottomBar(tabs, selected, { selected = it }) }
        rule.mainClock.advanceTimeBy(500)
        commitState { selected = 2 }
        rule.mainClock.advanceTimeBy(80)
        shot("blob_travelling", dark, reduced)
    }

    @Test fun blob_travelling_light() = blob(false)
    @Test fun blob_travelling_dark() = blob(true)
    @Test fun blob_travelling_light_reduced() = blob(false, reduced = true)

    private fun sheet(dark: Boolean, reduced: Boolean = false) {
        frame(dark, reduced) {
            Box(Modifier.fillMaxWidth().height(Sizes.illustration * 2)) {
                SsBottomSheet(visible = true, onDismissRequest = {}) {
                    BasicText("Who paid?", Modifier.padding(Spacing.screen).height(Sizes.illustrationSmall), style = SplitSmartTheme.type.title.copy(color = SplitSmartTheme.colors.text))
                }
            }
        }
        rule.mainClock.advanceTimeBy(96)
        shot("sheet_arriving", dark, reduced)
    }

    @Test fun sheet_arriving_light() = sheet(false)
    @Test fun sheet_arriving_dark() = sheet(true)
    @Test fun sheet_arriving_dark_reduced() = sheet(true, reduced = true)

    @Test
    fun snackbar_arriving_light() {
        val state = SsSnackbarState()
        frame(false) {
            LaunchedEffect(Unit) { state.show("Group archived", emoji = "📦", actionLabel = "Undo") }
            Box(Modifier.fillMaxWidth().height(Sizes.illustration)) { SsSnackbarHost(state, Modifier.align(Alignment.BottomCenter)) }
        }
        rule.mainClock.advanceTimeBy(112)
        shot("snackbar_arriving", false)
    }

    private fun confetti(dark: Boolean) {
        var key by mutableStateOf<Int?>(null)
        frame(dark) {
            Box(Modifier.fillMaxWidth().height(Sizes.illustration * 2), contentAlignment = Alignment.Center) {
                BasicText("All square! 🎉", style = SplitSmartTheme.type.display.copy(color = SplitSmartTheme.colors.text))
                ConfettiBurst(key, Modifier.matchParentSize())
            }
        }
        rule.mainClock.advanceTimeBy(100)
        commitState { key = 7 }
        rule.mainClock.advanceTimeBy(420)
        shot("confetti_burst", dark)
    }

    @Test fun confetti_burst_light() = confetti(false)
    @Test fun confetti_burst_dark() = confetti(true)

    @Test
    fun chip_pop_light() {
        var picked by mutableStateOf(false)
        frame(false) { Column { EmojiChip("Food", picked, { picked = !picked }, emoji = "🍛") } }
        rule.mainClock.advanceTimeBy(200)
        commitState { picked = true }
        rule.mainClock.advanceTimeBy(64)
        shot("chip_pop", false)
    }

    @Test
    fun skeleton_sweep_light() {
        frame(false) { SkeletonList(2) }
        rule.mainClock.advanceTimeBy(500)
        shot("skeleton_sweep", false)
    }

    private companion object {
        const val ROOT = "motion-root"
    }
}
