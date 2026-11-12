package com.darshan.splitsmart.ui.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.ui.ErrorReason
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.designsystem.assertInside
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.darshan.splitsmart.ui.designsystem.sameAs
import com.darshan.splitsmart.ui.designsystem.setDs
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class HistoryScreenTest {
    @get:Rule val rule = createComposeRule()

    private fun show(state: HistoryUiState = HistoryFixtures.state(), callbacks: HistoryCallbacks = HistoryFixtures.callbacks, fontScale: Float = 1f, reduceMotion: Boolean = true) =
        rule.setDs(fontScale = fontScale, reduceMotion = reduceMotion) { HistoryScreen(state, SsSnackbarState(), callbacks) }

    @Test
    fun `insights say what was spent and settled, who paid most and the top categories, one sentence per bar`() {
        show()
        rule.onNodeWithText("Goa trip so far").assertIsDisplayed()
        rule.onNodeWithText("6 bills").assertIsDisplayed()
        rule.onNodeWithText("2 payments").assertIsDisplayed()
        assertThat(rule.onAllNodesWithTag(HistoryTags.PAYER_BAR).fetchSemanticsNodes()).hasSize(4)
        rule.onNodeWithContentDescription("Asha paid ₹13,262.50").assertExists()
        rule.onNodeWithText("Guessed from the words in each bill's name").assertExists()
        rule.onNodeWithContentDescription("Stay, ₹12,000.00").assertExists()
    }

    @Test
    fun `the timeline runs newest first in months with sticky headers and their totals`() {
        show()
        rule.onNodeWithTag(HistoryTags.LIST).performScrollToKey("m:2026-11")
        rule.onNodeWithText("November 2026").assertIsDisplayed()
        rule.onNodeWithText("₹2,162.50 spent · ₹420.84 settled").assertIsDisplayed()
        rule.onNodeWithText("Ravi paid Asha").assertIsDisplayed()
        rule.onNodeWithText("Dinner at Marigold Cafe").assertIsDisplayed()
        rule.onNodeWithTag(HistoryTags.LIST).performScrollToKey("b:b4")
        rule.onNodeWithText("26 Oct · no payer yet").assertExists()
        // Scrolled past October's header, the header stays pinned above its rows.
        rule.onNodeWithText("October 2026").assertIsDisplayed()
    }

    @Test
    fun `a bill opens, a payment opens its sheet`() {
        val bills = mutableListOf<String>()
        var selected: String? = null
        show(callbacks = HistoryCallbacks({}, onOpenBill = { bills += it }, onSelect = { selected = it.id }))
        rule.onNodeWithTag(HistoryTags.LIST).performScrollToKey("s:s1")
        rule.onNodeWithText("Ravi paid Asha").performClick()
        rule.onNodeWithText("Dinner at Marigold Cafe").performClick()
        assertThat(selected).isEqualTo("s1")
        assertThat(bills).containsExactly("b1")
    }

    @Test
    fun `the payment sheet says what SplitSmart knows and removes once`() {
        var removes = 0
        var removing by mutableStateOf(false)
        rule.setDs(reduceMotion = true) {
            HistoryScreen(HistoryFixtures.state(selected = true).copy(removing = removing), SsSnackbarState(), HistoryCallbacks({}, onRemove = { removes++; removing = true }))
        }
        rule.onNodeWithText("SplitSmart only knows what was marked here. It never sees the money.").assertIsDisplayed()
        rule.onNodeWithText("Marked on ${com.darshan.splitsmart.ui.components.formatDate(HistoryFixtures.settlements.first().createdAt)} · paid in a UPI app").assertExists()
        repeat(1_000) { rule.onNodeWithTag(HistoryTags.REMOVE).performClick() }
        assertThat(removes).isEqualTo(1)
    }

    @Test
    fun `bars grow from nothing when motion is on and are simply there under reduced motion`() {
        rule.mainClock.autoAdvance = false
        show(reduceMotion = false)
        rule.mainClock.advanceTimeBy(16)
        val growing = rule.onAllNodesWithTag(HistoryTags.PAYER_BAR)[0].captureToImage()
        rule.mainClock.advanceTimeBy(2_000)
        val grown = rule.onAllNodesWithTag(HistoryTags.PAYER_BAR)[0].captureToImage()
        assertThat(growing.sameAs(grown)).isFalse()
        rule.mainClock.advanceTimeBy(1_000)
        assertThat(rule.onAllNodesWithTag(HistoryTags.PAYER_BAR)[0].captureToImage().sameAs(grown)).isTrue()
    }

    @Test
    fun `at 1_3x text the bars and figures stay inside the card`() {
        show(fontScale = 1.3f)
        val card = rule.onNodeWithTag(HistoryTags.INSIGHTS)
        rule.onAllNodesWithTag(HistoryTags.PAYER_BAR)[0].assertInside(card)
        rule.onAllNodesWithTag(HistoryTags.PAYER_BAR)[3].assertInside(card)
    }

    @Test
    fun `loading, empty and error are designed`() {
        var state by mutableStateOf(HistoryUiState())
        rule.setDs(reduceMotion = true) { HistoryScreen(state, SsSnackbarState(), HistoryFixtures.callbacks) }
        rule.onNodeWithContentDescription("Loading the history").assertExists()
        state = HistoryUiState(LoadState.Empty)
        rule.onNodeWithText("No history yet").assertIsDisplayed()
        state = HistoryUiState(LoadState.Error(ErrorReason.STORAGE))
        rule.onNodeWithText("Couldn't load the history").assertIsDisplayed()
    }

    @Test
    fun `a read that failed can be tried again, a group that is gone goes back`() {
        var retries = 0
        var backs = 0
        var state by mutableStateOf(HistoryUiState(LoadState.Error(ErrorReason.STORAGE)))
        rule.setDs(reduceMotion = true) { HistoryScreen(state, SsSnackbarState(), HistoryFixtures.callbacks.copy(onBack = { backs++ }, onRetry = { retries++ })) }
        rule.onNodeWithText("Try again").performClick()
        assertThat(retries).isEqualTo(1)
        state = HistoryUiState(LoadState.Error(ErrorReason.NOT_FOUND))
        rule.onNodeWithText("Try again").assertDoesNotExist()
        ShadowSystemClock.advanceBy(Duration.ofMillis(600)) // past the button's one-tap-per-500-ms guard
        rule.onNodeWithText("Back").performClick()
        assertThat(backs).isEqualTo(1)
        assertThat(retries).isEqualTo(1)
    }
}
