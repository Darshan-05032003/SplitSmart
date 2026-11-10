package com.darshan.splitsmart.ui.settleup

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.domain.model.SettlementMethod
import com.darshan.splitsmart.ui.ErrorReason
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.designsystem.FakeHaptics
import com.darshan.splitsmart.ui.designsystem.assertInside
import com.darshan.splitsmart.ui.designsystem.sameAs
import com.darshan.splitsmart.ui.designsystem.components.ButtonStyle
import com.darshan.splitsmart.ui.designsystem.components.ChunkyButton
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.darshan.splitsmart.ui.designsystem.modifiers.filterTouchesWhenObscured
import com.darshan.splitsmart.ui.designsystem.setDs
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class SettleUpScreenTest {
    @get:Rule val rule = createComposeRule()

    private lateinit var view: View

    private fun show(
        state: SettleUpUiState,
        callbacks: SettleUpCallbacks = SettleUpFixtures.callbacks,
        fontScale: Float = 1f,
        reduceMotion: Boolean = true,
        haptics: FakeHaptics = FakeHaptics(),
    ) {
        rule.setDs(fontScale = fontScale, reduceMotion = reduceMotion, haptics = haptics) {
            view = LocalView.current
            Box(Modifier.fillMaxSize().testTag(ROOT)) { SettleUpScreen(state, SsSnackbarState(), callbacks) }
        }
    }

    /** A finger on [tag], delivered the way Android does when another app's window covers ours ([flags]) or not. */
    private fun tap(tag: String, flags: Int = 0) {
        val center = rule.onAllNodesWithTag(tag)[0].fetchSemanticsNode().boundsInRoot.center
        val now = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(event(now, now, MotionEvent.ACTION_DOWN, center, flags))
        view.dispatchTouchEvent(event(now, now + TAP_MS, MotionEvent.ACTION_UP, center, flags))
        rule.waitForIdle()
    }

    private fun event(down: Long, time: Long, action: Int, at: Offset, flags: Int): MotionEvent {
        val properties = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER })
        val coords = arrayOf(MotionEvent.PointerCoords().apply { x = at.x; y = at.y; pressure = 1f; size = 1f })
        return MotionEvent.obtain(down, time, action, 1, properties, coords, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, flags)
    }

    @Test
    fun `the hero says what is left, how many payments clear it and what pay really does`() {
        show(SettleUpFixtures.state())
        rule.onNodeWithTag(SettleUpTags.HERO).assertIsDisplayed()
        rule.onNodeWithText("To settle in Goa trip").assertIsDisplayed()
        rule.onNodeWithContentDescription("₹857.34").assertIsDisplayed()
        rule.onNodeWithText("3 payments clear every balance").assertIsDisplayed()
        rule.onNodeWithText("Pay opens your UPI app with the amount filled in. SplitSmart never moves money.").assertIsDisplayed()
    }

    @Test
    fun `each payment names who pays whom, where it goes, and its actions with labels`() {
        val paid = mutableListOf<SettleUpItem>()
        val qr = mutableListOf<SettleUpItem>()
        val marked = mutableListOf<SettleUpItem>()
        show(SettleUpFixtures.state(), SettleUpFixtures.callbacks.copy(onPay = { paid += it }, onShowQr = { qr += it }, onMarkPaid = { marked += it }))
        rule.onNodeWithText("Ravi owes Asha").assertIsDisplayed()
        rule.onNodeWithText("To asha@okfake").assertIsDisplayed()
        rule.onNode(hasContentDescription("₹420.84") and hasAnyAncestor(hasTestTag(SplitSmartTestTags.SETTLE_ITEM))).assertIsDisplayed()
        rule.onNodeWithText("Pay ₹420.84").performClick()
        assertThat(paid.single().key).isEqualTo("r>a")
        rule.onNodeWithContentDescription("Show QR to pay Asha").performClick()
        assertThat(qr.single().key).isEqualTo("r>a")
        rule.onAllNodesWithTag(SettleUpTags.MARK_PAID)[0].performClick()
        assertThat(marked.single().key).isEqualTo("r>a")
        // Meera's debt to Ravi: Ravi has no UPI ID, so the card says so and offers to add it.
        rule.onNodeWithTag(SettleUpTags.LIST).performScrollToKey("m>r")
        rule.onNodeWithText("Ravi has no UPI ID yet, so there is no payment link.").assertIsDisplayed()
        rule.onNodeWithText("Add Ravi's UPI ID").assertIsDisplayed()
    }

    @Test
    fun `a debt too large for one upi payment says so and offers the other ways`() {
        val huge = SettleUpFixtures.item(SettleUpFixtures.ravi, SettleUpFixtures.asha, 9_999_999_999_99L)
        show(SettleUpFixtures.state(items = listOf(huge)))
        assertThat(huge.tooLargeForUpi).isTrue()
        rule.onNodeWithText("More than one UPI payment can carry. Pay it in parts, or another way and mark it here.").assertIsDisplayed()
        assertThat(rule.onAllNodesWithTag(SplitSmartTestTags.SETTLE_PAY).fetchSemanticsNodes()).isEmpty()
        rule.onNodeWithTag(SettleUpTags.MARK_PAID).assertIsDisplayed()
        rule.onNode(hasContentDescription("₹99,99,99,999.99") and hasAnyAncestor(hasTestTag(SplitSmartTestTags.SETTLE_ITEM))).assertIsDisplayed()
        rule.onNode(hasContentDescription("₹99,99,99,999.99") and hasAnyAncestor(hasTestTag(SettleUpTags.HERO))).assertIsDisplayed()
    }

    @Test
    fun `the card being settled turns into the question, and yes is the only way to record`() {
        var yes = 0
        var notYet = 0
        show(SettleUpFixtures.state(check = SettleUpFixtures.check()), SettleUpFixtures.callbacks.copy(onConfirmPaid = { yes++ }, onNotYet = { notYet++ }))
        rule.onNodeWithText("Did it go through?").assertIsDisplayed()
        rule.onNodeWithText("Did ₹420.84 reach Asha? SplitSmart can't see your UPI app, so only you can say.").assertIsDisplayed()
        // The asked card replaces Ravi's payment card; the other two stay.
        assertThat(rule.onAllNodesWithTag(SplitSmartTestTags.SETTLE_ITEM).fetchSemanticsNodes()).hasSize(2)
        rule.onNodeWithTag(SettleUpTags.CHECK_YES).performClick()
        rule.onNodeWithTag(SettleUpTags.CHECK_NOT_YET).performClick()
        assertThat(yes).isEqualTo(1)
        assertThat(notYet).isEqualTo(1)
    }

    @Test
    fun `while away in the upi app the card waits and only offers cancel`() {
        show(SettleUpFixtures.state(check = SettleUpFixtures.check(asking = false)))
        rule.onNodeWithText("Paying in your UPI app").assertIsDisplayed()
        assertThat(rule.onAllNodesWithTag(SettleUpTags.CHECK_YES).fetchSemanticsNodes()).isEmpty()
        rule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun `paid another way asks in its own words`() {
        show(SettleUpFixtures.state(check = SettleUpFixtures.check(method = SettlementMethod.OTHER)))
        rule.onNodeWithText("Mark this as paid?").assertIsDisplayed()
        rule.onNodeWithText("Ravi paid Asha ₹420.84 in cash, by bank transfer or some other way.").assertIsDisplayed()
    }

    @Test
    fun `1000 taps on yes while it records give one confirmation`() {
        var yes = 0
        var recording by mutableStateOf(false)
        rule.setDs(reduceMotion = true) {
            view = LocalView.current
            SettleUpScreen(SettleUpFixtures.state(check = SettleUpFixtures.check(), recording = recording), SsSnackbarState(), SettleUpFixtures.callbacks.copy(onConfirmPaid = { yes++; recording = true }))
        }
        repeat(1_000) { rule.onNodeWithTag(SettleUpTags.CHECK_YES).performClick() }
        assertThat(yes).isEqualTo(1)
    }

    @Test
    fun `touches through another app's window never reach pay, paid another way or yes`() {
        var pays = 0
        var marks = 0
        var yes = 0
        val callbacks = SettleUpFixtures.callbacks.copy(onPay = { pays++ }, onMarkPaid = { marks++ }, onConfirmPaid = { yes++ })
        var state by mutableStateOf(SettleUpFixtures.state())
        rule.setDs(reduceMotion = true) {
            view = LocalView.current
            SettleUpScreen(state, SsSnackbarState(), callbacks)
        }
        assertThat(view.filterTouchesWhenObscured).isTrue()
        tap(firstTag(SplitSmartTestTags.SETTLE_PAY), MotionEvent.FLAG_WINDOW_IS_OBSCURED)
        tap(firstTag(SplitSmartTestTags.SETTLE_PAY), MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED)
        tap(firstTag(SettleUpTags.MARK_PAID), MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED)
        assertThat(pays).isEqualTo(0)
        assertThat(marks).isEqualTo(0)
        // The same finger with nothing on top works.
        tap(firstTag(SplitSmartTestTags.SETTLE_PAY))
        assertThat(pays).isEqualTo(1)
        state = SettleUpFixtures.state(check = SettleUpFixtures.check())
        rule.waitForIdle()
        tap(SettleUpTags.CHECK_YES, MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED)
        tap(SettleUpTags.CHECK_YES, MotionEvent.FLAG_WINDOW_IS_OBSCURED)
        assertThat(yes).isEqualTo(0)
        ShadowSystemClock.advanceBy(Duration.ofMillis(GUARD_MS))
        tap(SettleUpTags.CHECK_YES)
        assertThat(yes).isEqualTo(1)
    }

    @Test
    fun `the window filter is on only while settle-up is showing`() {
        var showing by mutableStateOf(true)
        rule.setDs(reduceMotion = true) {
            view = LocalView.current
            if (showing) SettleUpScreen(SettleUpFixtures.state(), SsSnackbarState(), SettleUpFixtures.callbacks)
        }
        assertThat(view.filterTouchesWhenObscured).isTrue()
        showing = false
        rule.waitForIdle()
        assertThat(view.filterTouchesWhenObscured).isFalse()
    }

    @Test
    fun `the action modifier alone drops a fully covered touch too`() {
        var clicks = 0
        rule.setDs(reduceMotion = true) {
            view = LocalView.current
            ChunkyButton("Pay", { clicks++ }, Modifier.filterTouchesWhenObscured().testTag("b"), style = ButtonStyle.Primary)
        }
        assertThat(view.filterTouchesWhenObscured).isFalse()
        tap("b", MotionEvent.FLAG_WINDOW_IS_OBSCURED)
        assertThat(clicks).isEqualTo(0)
        tap("b")
        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun `the payment just marked wears the stamp, and the stamp slams down only when it is fresh and motion is on`() {
        rule.mainClock.autoAdvance = false
        show(SettleUpFixtures.state(items = SettleUpFixtures.items.drop(1), settled = SettleUpFixtures.moment()), reduceMotion = false)
        rule.mainClock.advanceTimeBy(FRAME_MS)
        rule.onNodeWithTag(SettleUpTags.SETTLED_CARD).assertIsDisplayed()
        rule.onNodeWithText("Ravi paid Asha").assertIsDisplayed()
        rule.onNodeWithText("Marked just now · in your UPI app").assertIsDisplayed()
        val landing = rule.onNodeWithTag(SettleUpTags.STAMP).captureToImage()
        // Past the stamp's spring and the confetti, then once more to prove it rests.
        rule.mainClock.advanceTimeBy(SETTLE_MS * 2)
        val landed = rule.onNodeWithTag(SettleUpTags.STAMP).captureToImage()
        assertThat(landing.sameAs(landed)).isFalse()
        rule.mainClock.advanceTimeBy(SETTLE_MS)
        assertThat(rule.onNodeWithTag(SettleUpTags.STAMP).captureToImage().sameAs(landed)).isTrue()
        rule.onNodeWithContentDescription("Settled").assertExists()
    }

    @Test
    fun `under reduced motion the stamp is simply there from the first frame`() {
        rule.mainClock.autoAdvance = false
        show(SettleUpFixtures.state(items = SettleUpFixtures.items.drop(1), settled = SettleUpFixtures.moment()), reduceMotion = true)
        rule.mainClock.advanceTimeBy(FRAME_MS)
        val first = rule.onNodeWithTag(SettleUpTags.STAMP).captureToImage()
        rule.mainClock.advanceTimeBy(SETTLE_MS)
        assertThat(rule.onNodeWithTag(SettleUpTags.STAMP).captureToImage().sameAs(first)).isTrue()
    }

    @Test
    fun `all square swaps the hero for the bloom and the way to the history`() {
        var history = 0
        show(SettleUpFixtures.state(items = emptyList(), settled = SettleUpFixtures.moment(allSquare = true), history = 3), SettleUpFixtures.callbacks.copy(onHistory = { history++ }))
        rule.onNodeWithTag(SettleUpTags.ALL_SQUARE).assertIsDisplayed()
        rule.onNodeWithText("Nobody in Goa trip owes anybody.").assertIsDisplayed()
        rule.onNodeWithContentDescription("All square").assertExists()
        assertThat(rule.onAllNodesWithTag(SettleUpTags.HERO).fetchSemanticsNodes()).isEmpty()
        assertThat(rule.onAllNodesWithTag(SettleUpTags.SETTLED_CARD).fetchSemanticsNodes()).isEmpty()
        rule.onNodeWithTag(SettleUpTags.SEE_HISTORY).performClick()
        rule.onNodeWithTag(SettleUpTags.HISTORY).performClick()
        assertThat(history).isEqualTo(2)
    }

    @Test
    fun `bills left out are named with their reason, a long list shows five`() {
        val opened = mutableListOf<String>()
        val many = (1..12).map { SettleUpFixtures.toFix[it % 2].let { c -> c.copy(bill = c.bill.copy(id = "x$it", title = "Bill $it")) } }
        show(SettleUpFixtures.state(toFix = many), SettleUpFixtures.callbacks.copy(onOpenBill = { opened += it }))
        rule.onNodeWithTag(SettleUpTags.LIST).performScrollToKey("toFix")
        rule.onNodeWithText("12 bills aren't in these payments", substring = true).assertIsDisplayed()
        assertThat(rule.onAllNodesWithTag(SettleUpTags.TO_FIX_ROW).fetchSemanticsNodes()).hasSize(5)
        rule.onNodeWithText("And 7 more on the group screen.").assertExists()
        rule.onAllNodesWithTag(SettleUpTags.TO_FIX_ROW)[0].performClick()
        assertThat(opened).containsExactly("x1")
    }

    @Test
    fun `at 1_3x text every action stays inside its card`() {
        show(SettleUpFixtures.state(check = SettleUpFixtures.check(SettleUpFixtures.items[1])), fontScale = 1.3f)
        val card = rule.onAllNodesWithTag(SplitSmartTestTags.SETTLE_ITEM)[0]
        rule.onAllNodesWithTag(SplitSmartTestTags.SETTLE_PAY)[0].assertInside(card)
        rule.onAllNodesWithTag(SplitSmartTestTags.SETTLE_SHARE)[0].assertInside(card)
        rule.onAllNodesWithTag(SettleUpTags.MARK_PAID)[0].assertInside(card)
        rule.onNodeWithTag(SettleUpTags.LIST).performScrollToKey("d>m")
        rule.onNodeWithTag(SettleUpTags.CHECK_YES).assertInside(rule.onNodeWithTag(SettleUpTags.CHECK_CARD))
        rule.onNodeWithTag(SettleUpTags.CHECK_NOT_YET).assertInside(rule.onNodeWithTag(SettleUpTags.CHECK_CARD))
    }

    @Test
    fun `loading shows the skeleton, nothing to settle and errors are designed`() {
        var state by mutableStateOf(SettleUpUiState())
        rule.setDs(reduceMotion = true) { SettleUpScreen(state, SsSnackbarState(), SettleUpFixtures.callbacks) }
        rule.onNodeWithContentDescription("Working out the payments").assertExists()
        state = SettleUpUiState(LoadState.Empty)
        rule.onNodeWithText("Nothing to settle yet").assertIsDisplayed()
        state = SettleUpUiState(LoadState.Error(ErrorReason.NOT_FOUND))
        rule.onNodeWithText("Couldn't load settle up").assertIsDisplayed()
    }

    private fun firstTag(tag: String): String = tag.also { assertThat(rule.onAllNodesWithTag(it).fetchSemanticsNodes()).isNotEmpty() }

    private companion object {
        const val ROOT = "root"
        const val TAP_MS = 40L
        const val FRAME_MS = 16L
        const val SETTLE_MS = 1_200L
        const val GUARD_MS = 600L
    }
}
