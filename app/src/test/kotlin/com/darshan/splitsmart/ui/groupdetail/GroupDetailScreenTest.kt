package com.darshan.splitsmart.ui.groupdetail

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.domain.model.Debt
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testGroup
import com.darshan.splitsmart.testutil.testMember
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.designsystem.FakeHaptics
import com.darshan.splitsmart.ui.designsystem.assertInside
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.darshan.splitsmart.ui.designsystem.effects.HapticEvent
import com.darshan.splitsmart.ui.designsystem.setDs
import com.google.common.truth.Truth.assertThat
import androidx.compose.ui.test.performScrollToKey
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The rebuilt group screen: hero and primary action, members, add-bill tiles, sheets, guarded delete, archive, text size. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class GroupDetailScreenTest {
    @get:Rule val rule = createComposeRule()

    private val goa = testGroup("goa", "Goa trip")
    private val asha = testMember("m1", "goa", "Asha", vpa = "asha@upi")
    private val ravi = testMember("m2", "goa", "Ravi")
    private val bills = listOf(testBill(id = "a", groupId = "goa", title = "Dinner", totalPaise = 30_000), testBill(id = "b", groupId = "goa", title = "Cab", totalPaise = 20_000))
    private val owing = GroupDetail(goa, listOf(asha, ravi), bills, debts = listOf(Debt("m2", "m1", 15_000)))
    private val square = GroupDetail(goa, listOf(asha, ravi), bills)

    private class Calls {
        var settle = 0
        var scan = 0
        var addMember = 0
        var deletes = 0
        val removed = mutableListOf<String>()
        val archived = mutableListOf<Boolean>()
        val payers = mutableListOf<String?>()
    }

    private fun callbacks(calls: Calls) = GroupDetailCallbacks(
        onBack = {}, onRetry = {}, onOpenBill = {}, onScan = { calls.scan++ }, onSettleUp = { calls.settle++ }, onDeleteGroup = { calls.deletes++ },
        onAddMember = { calls.addMember++ }, onDismissMember = {}, onMemberName = {}, onMemberVpa = {}, onSubmitMember = {},
        onRemoveMember = { calls.removed += it }, onAddBill = {}, onDismissBill = {}, onBillTitle = {}, onBillAmount = {},
        onBillPayer = { calls.payers += it }, onSubmitBill = {}, onArchive = { calls.archived += it },
    )

    private fun show(state: GroupDetailUiState, calls: Calls = Calls(), fontScale: Float = 1f, haptics: FakeHaptics = FakeHaptics()) =
        rule.setDs(reduceMotion = true, fontScale = fontScale, haptics = haptics) { GroupDetailScreen(state, SsSnackbarState(), callbacks(calls)) }

    @Test
    fun `the hero shows what was spent and what is to move, and Settle up is the one main action`() {
        val calls = Calls()
        show(GroupDetailUiState(LoadState.Content(owing)), calls)
        // The benchmark waits for the group's name as an exact text.
        rule.onNodeWithText("Goa trip").assertIsDisplayed()
        rule.onNodeWithTag(GroupDetailTags.SPENT, useUnmergedTree = true).assertContentDescriptionEquals("₹500.00")
        rule.onNodeWithText("₹150.00 to settle in 1 payment").assertIsDisplayed()
        rule.onNodeWithTag(SplitSmartTestTags.SETTLE_UP_BUTTON).performClick()
        assertThat(calls.settle).isEqualTo(1)
    }

    @Test
    fun `with nobody owing anybody there is no settle button and the group reads as square`() {
        show(GroupDetailUiState(LoadState.Content(square)))
        rule.onNodeWithTag(SplitSmartTestTags.SETTLE_UP_BUTTON).assertDoesNotExist()
        rule.onNodeWithTag(GroupDetailTags.SETTLED).assertExists()
    }

    @Test
    fun `members are faces with names, and a face opens a sheet with their UPI ID and a way to remove them`() {
        val calls = Calls()
        show(GroupDetailUiState(LoadState.Content(owing)), calls)
        assertThat(rule.onAllNodesWithTag(SplitSmartTestTags.MEMBER_ITEM).fetchSemanticsNodes()).hasSize(2)
        rule.onNodeWithTag(SplitSmartTestTags.ADD_MEMBER_BUTTON).assertExists()
        rule.onNodeWithContentDescription("Asha, has a UPI ID").performClick()
        rule.onNodeWithTag(GroupDetailTags.MEMBER_SHEET).assertIsDisplayed()
        rule.onNodeWithText("asha@upi").assertIsDisplayed()
        rule.onNodeWithTag(GroupDetailTags.MEMBER_REMOVE).performClick()
        assertThat(calls.removed).containsExactly("m1")
        rule.onNodeWithTag(GroupDetailTags.MEMBER_SHEET).assertDoesNotExist()
        rule.onNodeWithContentDescription("Ravi").performClick()
        rule.onNodeWithText("No UPI ID yet. You can add one when you settle up.").assertIsDisplayed()
    }

    @Test
    fun `add member is a sheet whose errors say what to fix`() {
        show(GroupDetailUiState(LoadState.Content(square), memberDraft = MemberDraft(name = "Asha", duplicate = true)))
        rule.onNodeWithText("Someone with this name is already in the group").assertIsDisplayed()
        rule.onNodeWithTag(SplitSmartTestTags.MEMBER_VPA_FIELD).assertExists()
        rule.onNodeWithTag(SplitSmartTestTags.DIALOG_CONFIRM).assertIsDisplayed()
    }

    @Test
    fun `the bill sheet picks one payer and picking them again clears it`() {
        val calls = Calls()
        show(GroupDetailUiState(LoadState.Content(square), billDraft = BillDraft(title = "Lunch", amount = "300", payerId = "m1")), calls)
        rule.onNodeWithText("Ravi").performClick()
        rule.onNodeWithText("Asha").performClick()
        assertThat(calls.payers).containsExactly("m2", null).inOrder()
    }

    @Test
    fun `delete asks first, keep it changes nothing, and one confirmation deletes once with a heavy haptic`() {
        val calls = Calls()
        val haptics = FakeHaptics()
        show(GroupDetailUiState(LoadState.Content(square)), calls, haptics = haptics)
        rule.onNodeWithTag(GroupDetailTags.OPTIONS).performClick()
        rule.onNodeWithTag(SplitSmartTestTags.DELETE_GROUP_BUTTON).performClick()
        rule.onNodeWithText("Delete Goa trip?").assertIsDisplayed()
        rule.onNodeWithText("This removes 2 bills and 2 members. You'll have a few seconds to undo it.").assertIsDisplayed()
        rule.onNodeWithTag(GroupDetailTags.DELETE_KEEP).performClick()
        rule.onNodeWithTag(GroupDetailTags.DELETE_SHEET).assertDoesNotExist()
        assertThat(calls.deletes).isEqualTo(0)

        // Past the options button's one-tap-per-half-second guard.
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(600))
        rule.onNodeWithTag(GroupDetailTags.OPTIONS).performClick()
        rule.onNodeWithTag(SplitSmartTestTags.DELETE_GROUP_BUTTON).performClick()
        repeat(5) { rule.onNodeWithTag(GroupDetailTags.DELETE_CONFIRM).performClick() }
        // The button's own guard takes one tap per half second; the view model takes one confirmation in total (tested there).
        assertThat(calls.deletes).isEqualTo(1)
        assertThat(haptics.events).contains(HapticEvent.Heavy)
    }

    @Test
    fun `while the delete runs the confirmation is locked and says it is working`() {
        val calls = Calls()
        show(GroupDetailUiState(LoadState.Content(square), deleting = true), calls)
        rule.onNode(hasTestTag(GroupDetailTags.DELETE_CONFIRM) and hasStateDescription("Working on it")).performClick()
        assertThat(calls.deletes).isEqualTo(0)
    }

    @Test
    fun `archive lives in the options and an archived group says so and offers to come back`() {
        val calls = Calls()
        show(GroupDetailUiState(LoadState.Content(square.copy(group = goa.copy(archivedAt = java.time.Instant.EPOCH)))), calls)
        rule.onNodeWithText("Archived").assertIsDisplayed()
        rule.onNodeWithTag(GroupDetailTags.OPTIONS).performClick()
        rule.onNodeWithText("Unarchive group").assertIsDisplayed()
        rule.onNodeWithTag(GroupDetailTags.ARCHIVE).performClick()
        assertThat(calls.archived).containsExactly(false)
    }

    @Test
    fun `a new group with no bills shows the scan tile as the way in`() {
        val calls = Calls()
        show(GroupDetailUiState(LoadState.Content(GroupDetail(goa, emptyList(), emptyList()))), calls)
        rule.onNodeWithTag(GroupDetailTags.NO_BILLS).assertExists()
        rule.onNodeWithText("Add the people who share bills in this group.").assertIsDisplayed()
        rule.onNodeWithTag(SplitSmartTestTags.SCAN_BUTTON).performClick()
        assertThat(calls.scan).isEqualTo(1)
    }

    @Test
    fun `at 130 percent text the hero and the three add tiles stay on screen`() {
        show(GroupDetailUiState(LoadState.Content(owing)), fontScale = 1.3f)
        rule.onNodeWithTag(GroupDetailTags.HERO).assertInside(rule.onRoot())
        for (tag in listOf(SplitSmartTestTags.SCAN_BUTTON, SplitSmartTestTags.GALLERY_ADD_BUTTON, SplitSmartTestTags.ADD_BILL_BUTTON)) {
            rule.onNodeWithTag(tag).assertInside(rule.onRoot())
        }
    }

    @Test
    fun `bills left out of the balances say why in plain words`() {
        show(GroupDetailUiState(LoadState.Content(square.copy(unsettledBills = 1))))
        rule.onNodeWithTag(GroupDetailTags.LIST).performScrollToKey("unsettled")
        rule.onNodeWithText("1 bill isn't split or has no payer yet, so it isn't counted here.").assertExists()
    }
}
