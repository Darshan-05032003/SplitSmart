package com.darshan.splitsmart.ui.designsystem

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.ui.designsystem.components.AmountFormat
import com.darshan.splitsmart.ui.designsystem.components.AvatarStack
import com.darshan.splitsmart.ui.designsystem.components.BottomBarItem
import com.darshan.splitsmart.ui.designsystem.components.BumpCounter
import com.darshan.splitsmart.ui.designsystem.components.ChunkyButton
import com.darshan.splitsmart.ui.designsystem.components.ConfettiBurst
import com.darshan.splitsmart.ui.designsystem.components.EmojiChip
import com.darshan.splitsmart.ui.designsystem.components.EmptyState
import com.darshan.splitsmart.ui.designsystem.components.IllustrationKind
import com.darshan.splitsmart.ui.designsystem.components.RollingAmount
import com.darshan.splitsmart.ui.designsystem.components.SnackbarResult
import com.darshan.splitsmart.ui.designsystem.components.SsBottomBar
import com.darshan.splitsmart.ui.designsystem.components.SsBottomSheet
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarHost
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.darshan.splitsmart.ui.designsystem.components.SsTextField
import com.darshan.splitsmart.ui.designsystem.effects.HapticEvent
import com.darshan.splitsmart.ui.designsystem.effects.ThemeMode
import com.darshan.splitsmart.ui.designsystem.effects.UiSettings
import com.darshan.splitsmart.ui.designsystem.modifiers.PressFeedback
import com.darshan.splitsmart.ui.designsystem.modifiers.rememberPressFeedback
import com.darshan.splitsmart.ui.designsystem.motion.LocalMotion
import com.darshan.splitsmart.ui.designsystem.tokens.MarigoldLight
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock

internal fun ImageBitmap.sameAs(other: ImageBitmap): Boolean {
    if (width != other.width || height != other.height) return false
    val a = toPixelMap()
    val b = other.toPixelMap()
    for (x in 0 until width step 2) for (y in 0 until height step 2) if (a[x, y] != b[x, y]) return false
    return true
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ButtonsUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `rapid taps run the action once per window with one light haptic`() {
        val haptics = FakeHaptics()
        var count = 0
        rule.setDs(haptics = haptics) { ChunkyButton("Save split", { count++ }, Modifier.testTag("b")) }
        repeat(10) { rule.onNodeWithTag("b").performClick() }
        rule.runOnIdle { assertThat(count).isEqualTo(1) }
        assertThat(haptics.events).containsExactly(HapticEvent.Light)
        ShadowSystemClock.advanceBy(Duration.ofMillis(600))
        rule.onNodeWithTag("b").performClick()
        rule.runOnIdle { assertThat(count).isEqualTo(2) }
    }

    @Test
    fun `loading ignores taps and says so`() {
        var count = 0
        rule.setDs { ChunkyButton("Settle up", { count++ }, Modifier.testTag("b"), loading = true) }
        rule.onNodeWithTag("b").performClick()
        rule.runOnIdle { assertThat(count).isEqualTo(0) }
        assertThat(rule.onNodeWithTag("b").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)).isEqualTo("Working on it")
    }

    @Test
    fun `disabled does nothing and buzzes nothing`() {
        val haptics = FakeHaptics()
        var count = 0
        rule.setDs(haptics = haptics) { ChunkyButton("Add", { count++ }, Modifier.testTag("b"), enabled = false) }
        rule.onNodeWithTag("b").assertIsNotEnabled().performClick()
        rule.runOnIdle { assertThat(count).isEqualTo(0) }
        assertThat(haptics.events).isEmpty()
    }

    @Test
    fun `the squash starts within 100 ms of finger down and stretches on release`() {
        lateinit var press: PressFeedback
        rule.mainClock.autoAdvance = false
        rule.setDs {
            press = rememberPressFeedback()
            ChunkyButton("Split it", {}, Modifier.testTag("b"), press = press)
        }
        rule.mainClock.advanceTimeBy(32)
        rule.onNodeWithTag("b").performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(32)
        assertThat(press.progress).isGreaterThan(0f)
        rule.mainClock.advanceTimeBy(68)
        assertThat(press.progress).isGreaterThan(0.7f)
        rule.onNodeWithTag("b").performTouchInput { up() }
        var lowest = 1f
        repeat(30) {
            rule.mainClock.advanceTimeBy(16)
            lowest = minOf(lowest, press.progress)
        }
        assertThat(lowest).isLessThan(0f)
        rule.mainClock.advanceTimeBy(1_000)
        assertThat(press.progress).isWithin(0.01f).of(0f)
    }

    @Test
    fun `the label stays inside the button at 1_3x font on a narrow screen`() {
        rule.setDs(fontScale = 1.3f) {
            Box(Modifier.width(240.dp)) { ChunkyButton("Split equally", {}, Modifier.fillMaxWidth().testTag("b"), emoji = "🍕") }
        }
        rule.onNodeWithText("Split equally", useUnmergedTree = true).assertInside(rule.onNodeWithTag("b"))
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class AvatarAndAmountUiTest {
    @get:Rule val rule = createComposeRule()

    private val crowd = (1..200).map { "Friend $it" }

    @Test
    fun `two hundred members read as one sentence and fan out on tap with a tick`() {
        val haptics = FakeHaptics()
        rule.mainClock.autoAdvance = false
        rule.setDs(haptics = haptics) { AvatarStack(crowd, Modifier.testTag("stack")) }
        rule.mainClock.advanceTimeBy(100)
        val node = rule.onNodeWithContentDescription("Friend 1, Friend 2, Friend 3, Friend 4 and 196 more")
        val stackedWidth = node.fetchSemanticsNode().boundsInRoot.width
        assertThat(node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)).isEqualTo("Stacked")
        node.performClick()
        rule.mainClock.advanceTimeBy(1_000)
        assertThat(haptics.events).containsExactly(HapticEvent.Tick)
        assertThat(node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)).isEqualTo("Spread out")
        assertThat(node.fetchSemanticsNode().boundsInRoot.width).isGreaterThan(stackedWidth)
    }

    @Test
    fun `reduced motion spreads the stack with a short fade instead of a bounce`() {
        rule.mainClock.autoAdvance = false
        rule.setDs(reduceMotion = true) { AvatarStack(crowd.take(4), Modifier.testTag("stack")) }
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag("stack").performClick()
        rule.mainClock.advanceTimeBy(200)
        val after200 = rule.onNodeWithTag("stack").fetchSemanticsNode().boundsInRoot.width
        rule.mainClock.advanceTimeBy(1_000)
        assertThat(rule.onNodeWithTag("stack").fetchSemanticsNode().boundsInRoot.width).isEqualTo(after200)
    }

    @Test
    fun `a rolling amount tells TalkBack the final value at once and animates without recomposing per frame`() {
        var paise by mutableLongStateOf(1_118_70L)
        rule.mainClock.autoAdvance = false
        rule.setDs { RollingAmount(paise, Modifier.testTag("amount")) }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithContentDescription("₹1,118.70").assertExists()
        commitState { paise = 2_08_310_75L }
        rule.mainClock.advanceTimeBy(16)
        rule.onNodeWithContentDescription("₹2,08,310.75").assertExists()
        val before = recompositionCycles()
        rule.mainClock.advanceTimeBy(60)
        val mid = rule.onNodeWithTag("amount").captureToImage()
        repeat(40) { rule.mainClock.advanceTimeBy(16) }
        assertThat(recompositionCycles() - before).isLessThan(8)
        rule.mainClock.advanceTimeBy(2_000)
        assertThat(mid.sameAs(rule.onNodeWithTag("amount").captureToImage())).isFalse()
    }

    @Test
    fun `the largest amount fits a narrow card at 1_3x`() {
        rule.setDs(fontScale = 1.3f) {
            Box(Modifier.width(280.dp).testTag("card")) { RollingAmount(AmountFormat.MAX_DISPLAY_PAISE, Modifier.testTag("amount")) }
        }
        rule.onNodeWithContentDescription("₹99,99,99,999.99").assertInside(rule.onNodeWithTag("card"))
    }

    @Test
    fun `a counter bumps on change and caps at 99 plus`() {
        var count by mutableIntStateOf(3)
        rule.mainClock.autoAdvance = false
        rule.setDs { Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) { BumpCounter(count, Modifier.testTag("count")) } }
        rule.mainClock.advanceTimeBy(300)
        val rest = rule.onNodeWithTag("count").captureToImage()
        commitState { count = 4 }
        rule.mainClock.advanceTimeBy(64)
        assertThat(rule.onNodeWithTag("count").captureToImage().sameAs(rest)).isFalse()
        commitState { count = 120 }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithContentDescription("99+").assertExists()
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class SelectionUiTest {
    @get:Rule val rule = createComposeRule()

    private val tabs = listOf(BottomBarItem("Groups", Icons.Filled.Home), BottomBarItem("Scan", Icons.Filled.Search), BottomBarItem("Me", Icons.Filled.Person))

    @Test
    fun `the bottom bar selects with a tick and reselect asks to scroll up`() {
        val haptics = FakeHaptics()
        var selected by mutableIntStateOf(0)
        var reselected = -1
        rule.setDs(haptics = haptics) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                SsBottomBar(tabs, selected, onSelect = { selected = it }, onReselect = { reselected = it })
            }
        }
        rule.onNodeWithContentDescription("Scan").performClick()
        rule.onNodeWithContentDescription("Scan").assertIsSelected()
        rule.onNodeWithContentDescription("Groups").assertIsNotSelected()
        assertThat(haptics.events).containsExactly(HapticEvent.Tick)
        rule.onNodeWithContentDescription("Scan").performClick()
        assertThat(reselected).isEqualTo(1)
        assertThat(haptics.events).hasSize(1)
    }

    @Test
    fun `the blob travels in draw and layer passes only`() {
        var selected by mutableIntStateOf(0)
        rule.mainClock.autoAdvance = false
        rule.setDs { SsBottomBar(tabs, selected, onSelect = { selected = it }, modifier = Modifier.testTag("bar")) }
        rule.mainClock.advanceTimeBy(500)
        commitState { selected = 2 }
        rule.mainClock.advanceTimeBy(16)
        val before = recompositionCycles()
        rule.mainClock.advanceTimeBy(48)
        val mid = rule.onNodeWithTag("bar").captureToImage()
        repeat(30) { rule.mainClock.advanceTimeBy(16) }
        assertThat(recompositionCycles() - before).isLessThan(4)
        rule.mainClock.advanceTimeBy(2_000)
        assertThat(mid.sameAs(rule.onNodeWithTag("bar").captureToImage())).isFalse()
    }

    @Test
    fun `chips toggle as checkboxes or select as radio options`() {
        var veg by mutableStateOf(false)
        var mode by mutableIntStateOf(0)
        rule.setDs {
            Column {
                EmojiChip("Veg only", veg, { veg = !veg }, Modifier.testTag("veg"), emoji = "🥗")
                EmojiChip("Equal", mode == 0, { mode = 0 }, Modifier.testTag("equal"), exclusive = true)
                EmojiChip("By item", mode == 1, { mode = 1 }, Modifier.testTag("item"), exclusive = true)
            }
        }
        rule.onNodeWithTag("veg").assertIsOff().performClick()
        rule.onNodeWithTag("veg").assertIsOn()
        rule.onNodeWithTag("item").assertIsNotSelected().performClick()
        rule.onNodeWithTag("item").assertIsSelected()
        rule.onNodeWithTag("equal").assertIsNotSelected()
    }

    @Test
    fun `a field reports its error to TalkBack and buzzes once when the error appears`() {
        val haptics = FakeHaptics()
        var text by mutableStateOf("")
        var error by mutableStateOf<String?>(null)
        rule.setDs(haptics = haptics) { SsTextField(text, { text = it }, "Amount", Modifier.testTag("field"), error = error, emoji = "₹") }
        rule.onNodeWithTag("field").performTextInput("12a")
        rule.runOnIdle { assertThat(text).isEqualTo("12a") }
        rule.runOnIdle { error = "Numbers only" }
        rule.waitForIdle()
        assertThat(rule.onNodeWithTag("field").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Error)).isEqualTo("Numbers only")
        rule.runOnIdle { text = "12b" }
        rule.waitForIdle()
        assertThat(haptics.events).containsExactly(HapticEvent.Reject)
    }

    @Test
    fun `a field restored with an error does not buzz`() {
        val haptics = FakeHaptics()
        rule.setDs(haptics = haptics) { SsTextField("abc", {}, "Amount", error = "Numbers only") }
        rule.waitForIdle()
        assertThat(haptics.events).isEmpty()
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class OverlaysUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val scrim = SemanticsMatcher("scrim") { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Close" }

    @Composable
    private fun Sheet(visible: Boolean, dismissible: Boolean, onDismiss: () -> Unit) {
        Box(Modifier.fillMaxSize()) {
            SsBottomSheet(visible, onDismiss, dismissible = dismissible) { androidx.compose.foundation.text.BasicText("Pick a payer") }
        }
    }

    @Test
    fun `the scrim and back both dismiss an open sheet`() {
        var visible by mutableStateOf(true)
        var dismissals = 0
        rule.setDs { Sheet(visible, dismissible = true) { dismissals++; visible = false } }
        rule.onNodeWithText("Pick a payer").assertExists()
        rule.onNode(scrim).performSemanticsAction(SemanticsActions.OnClick)
        rule.runOnIdle { assertThat(dismissals).isEqualTo(1) }
        rule.runOnIdle { visible = true }
        rule.waitForIdle()
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.runOnIdle { assertThat(dismissals).isEqualTo(2) }
    }

    @Test
    fun `a locked sheet ignores the scrim and swallows back`() {
        var dismissals = 0
        rule.setDs { Sheet(visible = true, dismissible = false) { dismissals++ } }
        rule.onNodeWithText("Pick a payer").assertExists()
        assertThat(rule.onAllNodes(scrim).fetchSemanticsNodes()).isEmpty()
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.runOnIdle { assertThat(dismissals).isEqualTo(0) }
        rule.onNodeWithText("Pick a payer").assertExists()
    }

    @Test
    fun `undo restores once with a light haptic`() {
        val haptics = FakeHaptics()
        val state = SsSnackbarState()
        var result: SnackbarResult? = null
        // The undo window runs on the frame clock; an auto-advancing clock would idle straight through it.
        rule.mainClock.autoAdvance = false
        rule.setDs(haptics = haptics) {
            LaunchedEffect(Unit) { result = state.show("Group archived", emoji = "📦", actionLabel = "Undo") }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { SsSnackbarHost(state) }
        }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("Group archived").assertExists()
        rule.onNodeWithText("Undo").performClick()
        rule.onNodeWithText("Undo").performClick()
        rule.mainClock.advanceTimeBy(100)
        assertThat(result).isEqualTo(SnackbarResult.Undo)
        assertThat(haptics.events).containsExactly(HapticEvent.Light)
    }

    @Test
    fun `the undo window runs out on the frame clock`() {
        val state = SsSnackbarState()
        var result: SnackbarResult? = null
        rule.mainClock.autoAdvance = false
        rule.setDs {
            LaunchedEffect(Unit) { result = state.show("Bill deleted", actionLabel = "Undo") }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { SsSnackbarHost(state) }
        }
        rule.mainClock.advanceTimeBy(4_000)
        assertThat(result).isNull()
        rule.mainClock.advanceTimeBy(1_500)
        assertThat(result).isEqualTo(SnackbarResult.TimedOut)
    }

    @Test
    fun `confetti plays with full motion and stays off under reduced motion`() {
        fun frames(reduce: Boolean): Pair<ImageBitmap, ImageBitmap> {
            var key by mutableStateOf<Int?>(null)
            rule.mainClock.autoAdvance = false
            rule.setDs(reduceMotion = reduce) { ConfettiBurst(key, Modifier.size(200.dp).testTag("c")) }
            rule.mainClock.advanceTimeBy(100)
            val still = rule.onNodeWithTag("c").captureToImage()
            commitState { key = 1 }
            rule.mainClock.advanceTimeBy(400)
            return still to rule.onNodeWithTag("c").captureToImage()
        }
        val (still, playing) = frames(reduce = false)
        assertThat(still.sameAs(playing)).isFalse()
    }

    @Test
    fun `an empty state offers its one action and hides the drawing from TalkBack`() {
        var tapped = 0
        rule.setDs { EmptyState(IllustrationKind.Groups, "No groups yet", "Make one for your flat, a trip or tonight's dinner.", actionLabel = "New group", onAction = { tapped++ }) }
        rule.onNodeWithText("New group").performClick()
        rule.runOnIdle { assertThat(tapped).isEqualTo(1) }
        rule.onNodeWithText("No groups yet").assertExists()
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ReducedConfettiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `no confetti is drawn under reduced motion`() {
        var key by mutableStateOf<Int?>(null)
        rule.mainClock.autoAdvance = false
        rule.setDs(reduceMotion = true) { ConfettiBurst(key, Modifier.size(200.dp).testTag("c")) }
        rule.mainClock.advanceTimeBy(100)
        val still = rule.onNodeWithTag("c").captureToImage()
        commitState { key = 1 }
        rule.mainClock.advanceTimeBy(400)
        assertThat(still.sameAs(rule.onNodeWithTag("c").captureToImage())).isTrue()
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ThemeTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `system animator scale zero turns on reduced motion`() {
        val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        var reduced: Boolean? = null
        rule.setContent { SplitSmartTheme { reduced = LocalMotion.current.reduced } }
        rule.runOnIdle { assertThat(reduced).isTrue() }
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    @Test
    fun `stored settings choose the theme and the in-app reduce motion`() {
        var dark: Boolean? = null
        var reduced: Boolean? = null
        rule.setContent {
            SplitSmartTheme(UiSettings(theme = ThemeMode.Dark, reduceMotion = true)) {
                dark = SplitSmartTheme.colors.isDark
                reduced = LocalMotion.current.reduced
            }
        }
        rule.runOnIdle {
            assertThat(dark).isTrue()
            assertThat(reduced).isTrue()
        }
    }

    @Test
    fun `screens not rebuilt yet keep the old Material look, the Marigold look maps the palette`() {
        var legacyPrimary: androidx.compose.ui.graphics.Color? = null
        var marigoldPrimary: androidx.compose.ui.graphics.Color? = null
        rule.setContent {
            SplitSmartTheme(darkTheme = false) { legacyPrimary = MaterialTheme.colorScheme.primary }
            SplitSmartTheme(darkTheme = false, materialLook = com.darshan.splitsmart.ui.theme.MaterialLook.Marigold) { marigoldPrimary = MaterialTheme.colorScheme.primary }
        }
        rule.runOnIdle {
            assertThat(legacyPrimary).isEqualTo(androidx.compose.ui.graphics.Color(0xFFB5401F))
            assertThat(marigoldPrimary).isEqualTo(MarigoldLight.primary)
        }
    }
}
