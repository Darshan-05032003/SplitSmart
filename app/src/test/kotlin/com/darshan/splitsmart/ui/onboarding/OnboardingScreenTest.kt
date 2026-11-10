package com.darshan.splitsmart.ui.onboarding

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.ui.designsystem.FakeHaptics
import com.darshan.splitsmart.ui.designsystem.assertInside
import com.darshan.splitsmart.ui.designsystem.commitState
import com.darshan.splitsmart.ui.designsystem.effects.HapticEvent
import com.darshan.splitsmart.ui.designsystem.setDs
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class OnboardingScreenTest {
    @get:Rule val rule = createComposeRule()

    private var finishes = 0
    private lateinit var back: OnBackPressedDispatcher
    private val haptics = FakeHaptics()

    /** The art bobs forever, so the clock is paused before anything is composed. */
    private fun show(initialPage: Int = 0, fontScale: Float = 1f, reduceMotion: Boolean = false) {
        rule.mainClock.autoAdvance = false
        rule.setDs(fontScale = fontScale, reduceMotion = reduceMotion, haptics = haptics) {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            Box(Modifier.fillMaxSize().testTag(ROOT)) {
                OnboardingScreen(onFinish = { finishes++ }, initialPage = initialPage)
            }
        }
        settle()
    }

    private fun settle(ms: Long = SETTLE_MS) = rule.mainClock.advanceTimeBy(ms)

    private fun tapNext() {
        ShadowSystemClock.advanceBy(Duration.ofMillis(600))
        rule.onNodeWithTag(OnboardingTags.NEXT).performClick()
    }

    private fun indicatorSays(page: Int) =
        rule.onNodeWithTag(OnboardingTags.INDICATOR).assertContentDescriptionEquals("Page $page of ${OnboardingPages.COUNT}")

    private fun top(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top

    @Test
    fun `the intro opens on page one with skip and next, and no call to action yet`() {
        show()
        indicatorSays(1)
        rule.onNodeWithText("Snap the bill").assertExists()
        rule.onNodeWithTag(OnboardingTags.SKIP).assertExists()
        rule.onNodeWithContentDescription("Next page").assertExists()
        rule.onNodeWithTag(OnboardingTags.CTA).assertDoesNotExist()
    }

    @Test
    fun `the scan page is honest that AI needs a key and a built-in parser covers without one`() {
        show()
        rule.onNodeWithText("Gemini API key", substring = true).assertExists()
        rule.onNodeWithText("built-in parser", substring = true).assertExists()
    }

    @Test
    fun `the settle page says the UPI app does the paying and nothing is marked paid by itself`() {
        show(initialPage = 2)
        rule.onNodeWithText("your UPI app opens", substring = true).assertExists()
        rule.onNodeWithText("never marks a debt paid by itself", substring = true).assertExists()
    }

    @Test
    fun `next walks the pages with a tick each, and the call to action rises in on a spring on the last`() {
        show()
        tapNext()
        settle()
        indicatorSays(2)
        rule.onNodeWithText("Split it your way").assertExists()
        tapNext()
        // The pager is heading for the last page: the button is there, still low and climbing.
        settle(FRAME_MS * 2)
        val early = top(OnboardingTags.CTA)
        settle()
        val rest = top(OnboardingTags.CTA)
        assertThat(early).isGreaterThan(rest + 1f)
        indicatorSays(3)
        rule.onNodeWithTag(OnboardingTags.SKIP).assertDoesNotExist()
        rule.onNodeWithTag(OnboardingTags.NEXT).assertDoesNotExist()
        assertThat(haptics.events.count { it == HapticEvent.Tick }).isEqualTo(2)
    }

    @Test
    fun `reduced motion fades the call to action in where it rests, without the climb`() {
        show(initialPage = 1, reduceMotion = true)
        val restTop = run {
            tapNext()
            settle(FRAME_MS * 2)
            top(OnboardingTags.CTA)
        }
        settle()
        assertThat(top(OnboardingTags.CTA)).isWithin(1f).of(restTop)
        indicatorSays(3)
    }

    @Test
    fun `skip finishes once however fast it is tapped`() {
        show()
        repeat(10) { rule.onNodeWithTag(OnboardingTags.SKIP).performClick() }
        settle()
        assertThat(finishes).isEqualTo(1)
    }

    @Test
    fun `the call to action finishes once`() {
        show(initialPage = 2)
        repeat(10) {
            ShadowSystemClock.advanceBy(Duration.ofMillis(600))
            rule.onNodeWithTag(OnboardingTags.CTA).performClick()
        }
        settle()
        assertThat(finishes).isEqualTo(1)
    }

    @Test
    fun `back walks to the previous page, and on the first page it is left to the system`() {
        show(initialPage = 2)
        rule.runOnIdle { back.onBackPressed() }
        settle()
        indicatorSays(2)
        rule.runOnIdle { back.onBackPressed() }
        settle()
        indicatorSays(1)
        rule.runOnIdle { assertThat(back.hasEnabledCallbacks()).isFalse() }
        assertThat(finishes).isEqualTo(0)
    }

    @Test
    fun `a predictive back gesture lets the previous page peek in, and cancelling it stays put`() {
        show(initialPage = 1)
        // Bounds are clipped to the screen: the page off to the left shows only as its visible right edge.
        val restRight = rule.onNodeWithTag(OnboardingTags.page(0)).fetchSemanticsNode().boundsInRoot.right
        rule.runOnIdle {
            back.dispatchOnBackStarted(BackEventCompat(0f, 400f, 0f, BackEventCompat.EDGE_LEFT))
            back.dispatchOnBackProgressed(BackEventCompat(120f, 400f, 0.8f, BackEventCompat.EDGE_LEFT))
        }
        settle(FRAME_MS * 3)
        commitState { }
        settle(FRAME_MS)
        val peekRight = rule.onNodeWithTag(OnboardingTags.page(0)).fetchSemanticsNode().boundsInRoot.right
        assertThat(peekRight).isGreaterThan(restRight + 10f)
        rule.runOnIdle { back.dispatchOnBackCancelled() }
        settle()
        indicatorSays(2)
        rule.onNodeWithText("Split it your way").assertExists()
    }

    @Test
    fun `at 1_3x text every page's words and the call to action stay on screen`() {
        show(initialPage = 2, fontScale = 1.3f)
        val root = rule.onNodeWithTag(ROOT)
        rule.onNodeWithText("Settle up over UPI").assertInside(root)
        rule.onNodeWithText("never marks a debt paid by itself", substring = true).assertInside(root)
        rule.onNodeWithTag(OnboardingTags.CTA).assertInside(root)
    }

    @Test
    fun `the first page fits at 1_3x too, with skip and next on screen`() {
        show(fontScale = 1.3f)
        val root = rule.onNodeWithTag(ROOT)
        rule.onNodeWithText("Gemini API key", substring = true).assertInside(root)
        rule.onNodeWithTag(OnboardingTags.SKIP).assertInside(root)
        rule.onNodeWithTag(OnboardingTags.NEXT).assertInside(root)
    }

    @Test
    fun `talkback hears the page position, the titles as headings and a named next button`() {
        show()
        indicatorSays(1)
        val heading = rule.onNodeWithText("Snap the bill", useUnmergedTree = false).fetchSemanticsNode()
        assertThat(heading.config.contains(SemanticsProperties.Heading)).isTrue()
        rule.onNodeWithContentDescription("Next page").assertExists()
    }

    private companion object {
        const val ROOT = "root"
        const val FRAME_MS = 16L
        const val SETTLE_MS = 1_500L
    }
}
