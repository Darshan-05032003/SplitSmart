package com.darshan.splitsmart.ui.split

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.core.Money
import com.darshan.splitsmart.domain.model.SplitConfig
import com.darshan.splitsmart.domain.model.SplitMode
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testItem
import com.darshan.splitsmart.testutil.testMember
import com.darshan.splitsmart.ui.ErrorReason
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.designsystem.captureNode
import com.darshan.splitsmart.ui.designsystem.commitState
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.darshan.splitsmart.ui.designsystem.setDs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The rebuilt split screen in its main states, light and dark, at 1.3× text, mid-drag and mid-morph. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class SplitScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val noop = SplitCallbacks({}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, {})
    private val all = listOf("a", "r", "m")
    private val partly = SplitConfig(SplitMode.BY_ITEM, all, itemAssignments = mapOf("i1" to setOf("a", "r"), "i3" to setOf("m")))
    private val owned = SplitConfig(SplitMode.BY_ITEM, all, itemAssignments = mapOf("i1" to setOf("a", "r"), "i2" to setOf("m"), "i3" to setOf("m"), "i4" to setOf("r")))

    private fun shot(name: String, dark: Boolean = false, fontScale: Float = 1f, reduceMotion: Boolean = true, act: () -> Unit = {}, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setDs(dark = dark, fontScale = fontScale, reduceMotion = reduceMotion) { Box(Modifier.fillMaxSize().testTag(ROOT)) { content() } }
        rule.mainClock.advanceTimeBy(1_500)
        act()
        rule.onNodeWithTag(ROOT).captureNode("split_${name}_${if (dark) "dark" else "light"}${if (fontScale != 1f) "_font130" else ""}")
    }

    private fun screen(editor: SplitEditor, saving: Boolean = false) = @Composable {
        SplitScreen(SplitUiState(LoadState.Content(editor), saving = saving), SsSnackbarState(), noop)
    }

    private fun scrollTo(key: String) {
        rule.onNodeWithTag(SplitTags.LIST).performScrollToKey(key)
        rule.mainClock.advanceTimeBy(500)
    }

    @Test fun equal_light() = shot("equal", content = screen(SplitFixtures.editor()))
    @Test fun equal_dark() = shot("equal", dark = true, content = screen(SplitFixtures.editor()))
    @Test fun equal_font130() = shot("equal", fontScale = 1.3f, content = screen(SplitFixtures.editor()))

    @Test
    fun by_item_light() = shot("by_item", act = { scrollTo("itemsHeader") }, content = screen(SplitFixtures.editor(config = partly, selected = setOf("i2", "i4"))))

    @Test
    fun by_item_dark() = shot("by_item", dark = true, act = { scrollTo("itemsHeader") }, content = screen(SplitFixtures.editor(config = owned)))

    @Test
    fun people_light() = shot("people", act = { scrollTo("person:a") }, content = screen(SplitFixtures.editor(config = partly)))

    @Test
    fun percent_light() = shot("percent", act = { scrollTo("person:r") }, content = screen(
        SplitFixtures.editor(config = SplitConfig(SplitMode.BY_SHARE, all, percent = true), shareTexts = mapOf("a" to "50", "r" to "20", "m" to "10")),
    ))

    @Test
    fun by_share_dark() = shot("by_share", dark = true, act = { scrollTo("person:a") }, content = screen(
        SplitFixtures.editor(config = SplitConfig(SplitMode.BY_SHARE, all), shareTexts = mapOf("a" to "2", "r" to "1", "m" to "1")),
    ))

    @Test
    fun loading_light() = shot("loading") { SplitScreen(SplitUiState(), SsSnackbarState(), noop) }

    @Test
    fun error_dark() = shot("error", dark = true) { SplitScreen(SplitUiState(LoadState.Error(ErrorReason.NOT_FOUND)), SsSnackbarState(), noop) }

    /** 200 people: the donut folds the smallest into one slice, the rows of faces scroll sideways. A ₹1 crore bill, the cap. */
    @Test
    fun crowd_light() {
        val people = (1..200).map { testMember("p$it", name = "Guest $it") }
        val bill = testBill(title = "Wedding caterer", totalPaise = Money.MAX_PAISE, items = listOf(testItem("i1", name = "Thali for everyone", unitPaise = Money.MAX_PAISE)))
        val editor = SplitFixtures.editor(
            config = SplitConfig(SplitMode.BY_SHARE, people.map { it.id }),
            shareTexts = people.associate { it.id to (1 + it.id.drop(1).toInt() % 9).toString() },
            payer = "p1",
            bill = bill,
            members = people,
        )
        shot("crowd", content = screen(editor))
    }

    /** Mid-drag: the line's pill under the finger and the face it hovers over grown, ready to gulp. */
    @Test
    fun dragging_light() = shot("dragging", reduceMotion = false, act = {
        scrollTo("itemsHeader")
        val handle = rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, SplitTags.handle("i2")))
        val from = handle.fetchSemanticsNode().boundsInRoot.center
        val to = rule.onNode(hasContentDescription("Meera, 1 line")).fetchSemanticsNode().boundsInRoot.center
        // One event per frame: the gesture detector only sees events while the (paused) clock lets it resume.
        handle.performTouchInput { down(center) }
        rule.mainClock.advanceTimeByFrame()
        repeat(10) {
            handle.performTouchInput { moveBy((to - from) / 10f) }
            rule.mainClock.advanceTimeByFrame()
        }
        rule.mainClock.advanceTimeBy(600)
    }, content = screen(SplitFixtures.editor(config = partly)))

    /** Equal → by item, 160 ms into the morph: the arcs on their way. */
    @Test
    fun morph_light() {
        val state = mutableStateOf(SplitUiState(LoadState.Content(SplitFixtures.editor())))
        shot("morph", reduceMotion = false, act = {
            commitState { state.value = SplitUiState(LoadState.Content(SplitFixtures.editor(config = owned))) }
            rule.mainClock.advanceTimeBy(160)
        }) { SplitScreen(state.value, SsSnackbarState(), noop) }
    }

    private companion object {
        const val ROOT = "root"
    }
}
