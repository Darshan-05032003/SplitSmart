package com.darshan.splitsmart.ui.simplify

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
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.designsystem.captureNode
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.darshan.splitsmart.ui.designsystem.setDs
import com.darshan.splitsmart.ui.groupdetail.GroupDetailCallbacks
import com.darshan.splitsmart.ui.groupdetail.GroupDetailScreen
import com.darshan.splitsmart.ui.groupdetail.GroupDetailTags
import com.darshan.splitsmart.ui.groupdetail.GroupDetailUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The simplify-debts card on the group screen and its sheet: the tangle mid-collapse and the result, light and dark. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class SimplifyScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val noop = GroupDetailCallbacks(
        onBack = {}, onRetry = {}, onOpenBill = {}, onScan = {}, onSettleUp = {}, onDeleteGroup = {}, onAddMember = {}, onDismissMember = {},
        onMemberName = {}, onMemberVpa = {}, onSubmitMember = {}, onRemoveMember = {}, onAddBill = {}, onDismissBill = {}, onBillTitle = {},
        onBillAmount = {}, onBillPayer = {}, onSubmitBill = {},
    )

    private fun shot(name: String, dark: Boolean = false, reduceMotion: Boolean = true, act: () -> Unit = {}, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setDs(dark = dark, reduceMotion = reduceMotion) { Box(Modifier.fillMaxSize().testTag(ROOT)) { content() } }
        rule.mainClock.advanceTimeBy(1_500)
        act()
        rule.onNodeWithTag(ROOT).captureNode("simplify_${name}_${if (dark) "dark" else "light"}")
    }

    private val screen = @Composable { GroupDetailScreen(GroupDetailUiState(LoadState.Content(SimplifyFixtures.detail)), SsSnackbarState(), noop) }

    private fun openSheet(wait: Long) {
        rule.onNodeWithTag(GroupDetailTags.LIST).performScrollToKey("simplify")
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag(SimplifyTags.CARD).performClick()
        rule.mainClock.advanceTimeBy(wait)
    }

    @Test fun card_light() = shot("card", act = { rule.onNodeWithTag(GroupDetailTags.LIST).performScrollToKey("balances"); rule.mainClock.advanceTimeBy(500) }, content = screen)

    @Test fun sheet_light() = shot("sheet", act = { openSheet(3_000) }, content = screen)

    @Test fun sheet_dark() = shot("sheet", dark = true, act = { openSheet(3_000) }, content = screen)

    /** Full motion, before the collapse: every IOU bill by bill. */
    @Test fun tangle_light() = shot("tangle", reduceMotion = false, act = { openSheet(150) }, content = screen)

    /** Full motion, a moment into the collapse: the IOU arrows shrinking into their middles. */
    @Test fun collapsing_dark() = shot("collapsing", dark = true, reduceMotion = false, act = { openSheet(420) }, content = screen)

    private companion object {
        const val ROOT = "root"
    }
}
