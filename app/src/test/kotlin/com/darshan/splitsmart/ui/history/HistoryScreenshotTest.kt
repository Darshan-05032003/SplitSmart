package com.darshan.splitsmart.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.designsystem.captureNode
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.darshan.splitsmart.ui.designsystem.setDs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The history screen: insights, the timeline with a pinned month, the payment sheet, light and dark, at 1.3× text. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class HistoryScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, state: HistoryUiState, dark: Boolean = false, fontScale: Float = 1f, act: () -> Unit = {}) {
        rule.mainClock.autoAdvance = false
        rule.setDs(dark = dark, fontScale = fontScale, reduceMotion = true) {
            Box(Modifier.fillMaxSize().testTag(ROOT)) { HistoryScreen(state, SsSnackbarState(), HistoryFixtures.callbacks) }
        }
        rule.mainClock.advanceTimeBy(1_500)
        act()
        rule.onNodeWithTag(ROOT).captureNode("history_${name}_${if (dark) "dark" else "light"}${if (fontScale != 1f) "_font130" else ""}")
    }

    private fun scrollTo(key: String) {
        rule.onNodeWithTag(HistoryTags.LIST).performScrollToKey(key)
        rule.mainClock.advanceTimeBy(500)
    }

    @Test fun insights_light() = shot("insights", HistoryFixtures.state())
    @Test fun insights_dark() = shot("insights", HistoryFixtures.state(), dark = true)
    @Test fun insights_font130() = shot("insights", HistoryFixtures.state(), fontScale = 1.3f)
    @Test fun timeline_light() = shot("timeline", HistoryFixtures.state(), act = { scrollTo("b:b2") })
    @Test fun timeline_dark() = shot("timeline", HistoryFixtures.state(), dark = true, act = { scrollTo("b:b3") })
    @Test fun payment_sheet_light() = shot("payment_sheet", HistoryFixtures.state(selected = true))
    @Test fun empty_dark() = shot("empty", HistoryUiState(LoadState.Empty), dark = true)

    private companion object {
        const val ROOT = "root"
    }
}
