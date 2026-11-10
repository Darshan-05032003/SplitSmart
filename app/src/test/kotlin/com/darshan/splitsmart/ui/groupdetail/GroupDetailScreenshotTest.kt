package com.darshan.splitsmart.ui.groupdetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.domain.model.BillSource
import com.darshan.splitsmart.domain.model.BillStatus
import com.darshan.splitsmart.domain.model.Debt
import com.darshan.splitsmart.domain.model.NameError
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testGroup
import com.darshan.splitsmart.testutil.testMember
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

/** The rebuilt group screen and its sheets, light and dark, at 1.3× text. Replaces the shell's `legacy_group_detail` baselines. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class GroupDetailScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val goa = testGroup("goa", "Goa trip")
    private val members = listOf(
        testMember("m1", "goa", "Asha", vpa = "asha@upi"),
        testMember("m2", "goa", "Ravi"),
        testMember("m3", "goa", "Darshan", vpa = "darshan@upi"),
        testMember("m4", "goa", "Meera"),
    )
    private val bills = listOf(
        testBill(id = "ai", groupId = "goa", title = "Beach shack dinner", totalPaise = 286_050, source = BillSource.SCAN_LLM, paidByMemberId = "m1", status = BillStatus.SPLIT),
        testBill(id = "cab", groupId = "goa", title = "Airport cab", totalPaise = 120_000, paidByMemberId = "m3", status = BillStatus.SPLIT),
        testBill(id = "manual", groupId = "goa", title = "Scooter rent", totalPaise = 90_000, source = BillSource.MANUAL),
    )
    private val detail = GroupDetail(goa, members, bills, debts = listOf(Debt("m2", "m1", 111_870), Debt("m4", "m3", 60_000)), unsettledBills = 1)
    private val noop = GroupDetailCallbacks(
        onBack = {}, onRetry = {}, onOpenBill = {}, onScan = {}, onSettleUp = {}, onDeleteGroup = {}, onAddMember = {}, onDismissMember = {},
        onMemberName = {}, onMemberVpa = {}, onSubmitMember = {}, onRemoveMember = {}, onAddBill = {}, onDismissBill = {}, onBillTitle = {},
        onBillAmount = {}, onBillPayer = {}, onSubmitBill = {},
    )

    private fun shot(name: String, dark: Boolean = false, fontScale: Float = 1f, act: () -> Unit = {}, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setDs(dark = dark, fontScale = fontScale) { Box(Modifier.fillMaxSize().testTag(ROOT)) { content() } }
        rule.mainClock.advanceTimeBy(1_500)
        act()
        rule.onNodeWithTag(ROOT).captureNode("group_${name}_${if (dark) "dark" else "light"}${if (fontScale != 1f) "_font130" else ""}")
    }

    private fun screen(state: GroupDetailUiState) = @Composable { GroupDetailScreen(state, SsSnackbarState(), noop) }

    @Test fun detail_light() = shot("detail", content = screen(GroupDetailUiState(LoadState.Content(detail))))
    @Test fun detail_dark() = shot("detail", dark = true, content = screen(GroupDetailUiState(LoadState.Content(detail))))
    @Test fun detail_font130() = shot("detail", fontScale = 1.3f, content = screen(GroupDetailUiState(LoadState.Content(detail))))
    @Test fun new_group_light() = shot("new_group", content = screen(GroupDetailUiState(LoadState.Content(GroupDetail(goa, emptyList(), emptyList())))))
    @Test fun loading_light() = shot("loading", content = screen(GroupDetailUiState()))

    @Test
    fun member_sheet_light() = shot("member_sheet", act = {
        rule.onNodeWithContentDescription("Asha, has a UPI ID").performClick()
        rule.mainClock.advanceTimeBy(1_000)
    }, content = screen(GroupDetailUiState(LoadState.Content(detail))))

    @Test
    fun delete_sheet_dark() = shot("delete_sheet", dark = true, act = {
        rule.onNodeWithTag(GroupDetailTags.OPTIONS).performClick()
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithTag(SplitSmartTestTags.DELETE_GROUP_BUTTON).performClick()
        rule.mainClock.advanceTimeBy(1_000)
    }, content = screen(GroupDetailUiState(LoadState.Content(detail))))

    @Test
    fun options_sheet_light() = shot("options_sheet", act = {
        rule.onNodeWithTag(GroupDetailTags.OPTIONS).performClick()
        rule.mainClock.advanceTimeBy(1_000)
    }, content = screen(GroupDetailUiState(LoadState.Content(detail))))

    @Test
    fun add_bill_sheet_light() = shot("add_bill_sheet", content = screen(GroupDetailUiState(LoadState.Content(detail), billDraft = BillDraft("Ice cream", "", "m1", amountError = true))))

    @Test
    fun add_member_sheet_dark() = shot("add_member_sheet", dark = true, content = screen(GroupDetailUiState(LoadState.Content(detail), memberDraft = MemberDraft("", "", nameError = NameError.BLANK))))

    private companion object {
        const val ROOT = "root"
    }
}
