package com.darshan.splitsmart.ui.bill

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.ui.ErrorReason
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.designsystem.captureNode
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.darshan.splitsmart.ui.designsystem.setDs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The rebuilt bill screen, light and dark, at 1.3× text, with its delete sheet. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class BillDetailScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, dark: Boolean = false, fontScale: Float = 1f, act: () -> Unit = {}, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setDs(dark = dark, fontScale = fontScale, reduceMotion = true) { Box(Modifier.fillMaxSize().testTag(ROOT)) { content() } }
        rule.mainClock.advanceTimeBy(1_500)
        act()
        rule.onNodeWithTag(ROOT).captureNode("bill_${name}_${if (dark) "dark" else "light"}${if (fontScale != 1f) "_font130" else ""}")
    }

    private fun screen(bill: Bill, deleting: Boolean = false) = @Composable {
        BillDetailScreen(BillDetailUiState(LoadState.Content(BillDetail(bill, BillFixtures.members)), deleting = deleting), SsSnackbarState(), {}, {}, {}, {})
    }

    private fun scrollTo(key: String) {
        rule.onNodeWithTag(BillDetailTags.LIST).performScrollToKey(key)
        rule.mainClock.advanceTimeBy(500)
    }

    @Test fun split_light() = shot("split", content = screen(BillFixtures.split))
    @Test fun split_dark() = shot("split", dark = true, content = screen(BillFixtures.split))
    @Test fun split_font130() = shot("split", fontScale = 1.3f, content = screen(BillFixtures.split))
    @Test fun receipt_light() = shot("receipt", act = { scrollTo("items") }, content = screen(BillFixtures.split))
    @Test fun receipt_dark() = shot("receipt", dark = true, act = { scrollTo("items") }, content = screen(BillFixtures.split))
    @Test fun draft_light() = shot("draft", content = screen(BillFixtures.draft))

    @Test
    fun delete_sheet_dark() = shot("delete_sheet", dark = true, act = {
        rule.onNodeWithTag(SplitSmartTestTags.DELETE_BILL_BUTTON).performClick()
        rule.mainClock.advanceTimeBy(1_000)
    }, content = screen(BillFixtures.split))

    @Test fun loading_light() = shot("loading") { BillDetailScreen(BillDetailUiState(), SsSnackbarState(), {}, {}, {}, {}) }

    @Test
    fun error_dark() = shot("error", dark = true) { BillDetailScreen(BillDetailUiState(LoadState.Error(ErrorReason.NOT_FOUND)), SsSnackbarState(), {}, {}, {}, {}) }

    private companion object {
        const val ROOT = "root"
    }
}
