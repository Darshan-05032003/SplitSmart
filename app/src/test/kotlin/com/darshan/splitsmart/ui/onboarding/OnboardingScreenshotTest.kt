package com.darshan.splitsmart.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.ui.designsystem.captureNode
import com.darshan.splitsmart.ui.designsystem.setDs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The intro's pages at rest in both themes, at 1.3× text, and one frame mid-swipe to show the parallax layers. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class OnboardingScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun page(page: Int, dark: Boolean, fontScale: Float = 1f, suffix: String = "") {
        rule.mainClock.autoAdvance = false
        rule.setDs(dark = dark, fontScale = fontScale) {
            Box(Modifier.fillMaxSize().testTag(ROOT)) { OnboardingScreen(onFinish = {}, initialPage = page) }
        }
        rule.mainClock.advanceTimeBy(1_500)
        rule.onNodeWithTag(ROOT).captureNode("shell_onboarding_${page + 1}_${if (dark) "dark" else "light"}$suffix")
    }

    @Test fun page1_light() = page(0, dark = false)
    @Test fun page1_dark() = page(0, dark = true)
    @Test fun page2_light() = page(1, dark = false)
    @Test fun page2_dark() = page(1, dark = true)
    @Test fun page3_light() = page(2, dark = false)
    @Test fun page3_dark() = page(2, dark = true)
    @Test fun page1_light_font130() = page(0, dark = false, fontScale = 1.3f, suffix = "_font130")
    @Test fun page3_dark_font130() = page(2, dark = true, fontScale = 1.3f, suffix = "_font130")

    /** A finger held 40 % of the way to page two: back layer lags, front layer leads, the indicator pill is stretched. */
    @Test
    fun parallax_mid_swipe() {
        rule.mainClock.autoAdvance = false
        rule.setDs {
            Box(Modifier.fillMaxSize().testTag(ROOT)) { OnboardingScreen(onFinish = {}) }
        }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag(OnboardingTags.PAGER).performTouchInput {
            down(center)
            moveBy(Offset(-width * 0.2f, 0f))
            moveBy(Offset(-width * 0.2f, 0f))
        }
        rule.mainClock.advanceTimeBy(32)
        rule.onNodeWithTag(ROOT).captureNode("shell_onboarding_parallax_light")
    }

    private companion object {
        const val ROOT = "root"
    }
}
