package com.darshan.splitsmart.ui.bill

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.BillSource
import com.darshan.splitsmart.domain.model.BillSplit
import com.darshan.splitsmart.domain.model.BillStatus
import com.darshan.splitsmart.domain.model.SplitConfig
import com.darshan.splitsmart.domain.model.SplitMode
import com.darshan.splitsmart.domain.split.SplitCalculator
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testItem
import com.darshan.splitsmart.testutil.testMember
import com.darshan.splitsmart.ui.ErrorReason
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.designsystem.FakeHaptics
import com.darshan.splitsmart.ui.designsystem.assertInside
import com.darshan.splitsmart.ui.designsystem.commitState
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.darshan.splitsmart.ui.designsystem.effects.HapticEvent
import com.darshan.splitsmart.ui.designsystem.setDs
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/** A fictional dinner, split equally between three with Asha paying, shared by the bill's UI and screenshot tests. */
internal object BillFixtures {
    val members = listOf(testMember("a", name = "Asha"), testMember("r", name = "Ravi"), testMember("m", name = "Meera"))
    private val unsplit: Bill = testBill(
        title = "Beach shack dinner",
        totalPaise = 1_262_50,
        taxPaise = 62_50,
        tipPaise = 100_00,
        source = BillSource.SCAN_LLM,
        items = listOf(
            testItem("i1", name = "Paneer tikka", qty = 2, unitPaise = 320_00),
            testItem("i2", name = "Masala dosa", unitPaise = 180_00, sortOrder = 1),
            testItem("i3", name = "Lime soda", qty = 3, unitPaise = 60_00, sortOrder = 2),
            testItem("i4", name = "Kulfi", qty = 2, unitPaise = 65_00, sortOrder = 3),
        ),
    )
    val draft: Bill = unsplit
    val split: Bill = run {
        val config = SplitConfig(SplitMode.EQUAL, members.map { it.id })
        unsplit.copy(paidByMemberId = "a", status = BillStatus.SPLIT, split = BillSplit(config, SplitCalculator.split(unsplit, config).shares))
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class BillDetailScreenTest {
    @get:Rule val rule = createComposeRule()

    private class Calls {
        var deletes = 0
        var splits = 0
        var retries = 0
        var backs = 0
    }

    private fun show(bill: Bill, calls: Calls = Calls(), fontScale: Float = 1f, deleting: Boolean = false, haptics: FakeHaptics = FakeHaptics()): Calls {
        rule.setDs(fontScale = fontScale, reduceMotion = true, haptics = haptics) {
            BillDetailScreen(
                BillDetailUiState(LoadState.Content(BillDetail(bill, BillFixtures.members)), deleting = deleting),
                SsSnackbarState(),
                onBack = {}, onDelete = { calls.deletes++ }, onRetry = { calls.retries++ }, onSplit = { calls.splits++ },
            )
        }
        return calls
    }

    private fun scrollTo(key: String) = rule.onNodeWithTag(BillDetailTags.LIST).performScrollToKey(key)

    @Test
    fun `a split bill shows the total, the payer, every share with who owes whom, and Edit split`() {
        val calls = show(BillFixtures.split)
        rule.onNodeWithContentDescription("₹1,262.50").assertIsDisplayed()
        rule.onNodeWithText("Paid by Asha").assertIsDisplayed()
        rule.onNodeWithText("Edit split").performClick()
        assertThat(calls.splits).isEqualTo(1)
        scrollTo("share:m")
        rule.onAllNodesWithTag(SplitSmartTestTags.SHARE_ROW).assertCountEquals(3)
        rule.onNodeWithContentDescription("Ravi owes Asha ₹420.83").assertExists()
        rule.onNodeWithContentDescription("Asha paid, gets back ₹841.66").assertExists()
    }

    @Test
    fun `the receipt lists every line, tax, tip and the discount`() {
        show(BillFixtures.split)
        scrollTo("items")
        rule.onAllNodesWithTag(SplitSmartTestTags.BILL_ITEM_ROW).assertCountEquals(4)
        rule.onNodeWithText("2 × ₹320.00").assertExists()
        scrollTo("total")
        rule.onNodeWithContentDescription("Discount, minus ₹30.00").assertExists()
        rule.onNodeWithContentDescription("Tax, ₹62.50").assertExists()
        rule.onNodeWithContentDescription("Tip, ₹100.00").assertExists()
    }

    @Test
    fun `a bill not split yet offers the split as the one main action`() {
        val calls = show(BillFixtures.draft)
        rule.onNodeWithText("Paid by nobody yet").assertExists()
        rule.onNodeWithTag(SplitSmartTestTags.SPLIT_BUTTON).performClick()
        assertThat(calls.splits).isEqualTo(1)
        rule.onNodeWithTag(BillDetailTags.NOT_SPLIT).assertExists()
        rule.onAllNodesWithTag(SplitSmartTestTags.SHARE_ROW).assertCountEquals(0)
    }

    @Test
    fun `delete asks first, Keep it keeps it, confirm deletes once with a heavy haptic`() {
        val haptics = FakeHaptics()
        val calls = show(BillFixtures.split, haptics = haptics)
        rule.onNodeWithTag(SplitSmartTestTags.DELETE_BILL_BUTTON).performClick()
        rule.waitForIdle()
        assertThat(calls.deletes).isEqualTo(0)
        rule.onNodeWithTag(BillDetailTags.DELETE_SHEET).assertIsDisplayed()
        rule.onNodeWithText("Delete Beach shack dinner?").assertExists()
        rule.onNodeWithTag(BillDetailTags.DELETE_KEEP).performClick()
        rule.waitForIdle()
        assertThat(calls.deletes).isEqualTo(0)
        ShadowSystemClock.advanceBy(Duration.ofMillis(600)) // past the button's tap guard
        rule.onNodeWithTag(SplitSmartTestTags.DELETE_BILL_BUTTON).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(BillDetailTags.DELETE_CONFIRM).performClick()
        assertThat(calls.deletes).isEqualTo(1)
        assertThat(haptics.events).contains(HapticEvent.Heavy)
    }

    @Test
    fun `a thousand taps on confirm delete once`() {
        val calls = Calls()
        val state = mutableStateOf(BillDetailUiState(LoadState.Content(BillDetail(BillFixtures.split, BillFixtures.members))))
        rule.mainClock.autoAdvance = false
        rule.setDs(reduceMotion = true) {
            BillDetailScreen(state.value, SsSnackbarState(), onBack = {}, onDelete = { calls.deletes++; state.value = state.value.copy(deleting = true) }, onRetry = {}, onSplit = {})
        }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag(SplitSmartTestTags.DELETE_BILL_BUTTON).performClick()
        rule.mainClock.advanceTimeBy(1_000)
        repeat(1_000) { rule.onNodeWithTag(BillDetailTags.DELETE_CONFIRM).performClick() }
        assertThat(calls.deletes).isEqualTo(1)
    }

    @Test
    fun `loading and error are designed, and error retries`() {
        val calls = Calls()
        val state = mutableStateOf(BillDetailUiState())
        rule.mainClock.autoAdvance = false
        rule.setDs(reduceMotion = true) { BillDetailScreen(state.value, SsSnackbarState(), onBack = { calls.backs++ }, onDelete = {}, onRetry = { calls.retries++ }, onSplit = {}) }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag(SplitSmartTestTags.LOADING).assertExists()
        commitState { state.value = BillDetailUiState(LoadState.Error(ErrorReason.STORAGE)) }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag(SplitSmartTestTags.ERROR).assertExists()
        rule.onNodeWithText("Couldn't load this bill").assertExists()
        rule.onNodeWithText("Try again").performClick()
        assertThat(calls.retries).isEqualTo(1)
    }

    @Test
    fun `a bill that is gone offers the way back, not a retry that cannot work`() {
        val calls = Calls()
        rule.mainClock.autoAdvance = false
        rule.setDs(reduceMotion = true) {
            BillDetailScreen(BillDetailUiState(LoadState.Error(ErrorReason.NOT_FOUND)), SsSnackbarState(), onBack = { calls.backs++ }, onDelete = {}, onRetry = { calls.retries++ }, onSplit = {})
        }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("Try again").assertDoesNotExist()
        rule.onNodeWithText("Back").performClick()
        assertThat(calls.backs).isEqualTo(1)
        assertThat(calls.retries).isEqualTo(0)
    }

    @Test
    fun `at 1·3x text the hero and its button stay on screen`() {
        show(BillFixtures.split, fontScale = 1.3f)
        rule.onNodeWithTag(BillDetailTags.HERO).assertInside(rule.onRoot())
        rule.onNodeWithTag(SplitSmartTestTags.SPLIT_BUTTON).assertInside(rule.onRoot())
    }
}
