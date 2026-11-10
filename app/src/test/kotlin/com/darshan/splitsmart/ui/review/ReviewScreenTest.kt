package com.darshan.splitsmart.ui.review

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.domain.model.ExtractionSource
import com.darshan.splitsmart.domain.model.ExtractionWarning
import com.darshan.splitsmart.domain.model.LlmFailure
import com.darshan.splitsmart.testutil.testMember
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.designsystem.assertInside
import com.darshan.splitsmart.ui.designsystem.commitState
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.darshan.splitsmart.ui.designsystem.recompositionCycles
import com.darshan.splitsmart.ui.designsystem.setDs
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The rebuilt review: reading state, honest source, the line-by-line reveal, compact rows that open in place, totals, saving. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ReviewScreenTest {
    @get:Rule val rule = createComposeRule()

    private class Calls {
        var saves = 0
        var reruns = 0
        var applied = 0
        var dismissed = 0
        var computed = 0
        var added = 0
        val removed = mutableListOf<String>()
        val payers = mutableListOf<String?>()
        val shown = mutableListOf<Long>()
    }

    private fun callbacks(calls: Calls) = ReviewCallbacks(
        onBack = {}, onRetry = {}, onRerun = { calls.reruns++ }, onSave = { calls.saves++ }, onTitle = {}, onPayer = { calls.payers += it },
        onItemName = { _, _ -> }, onItemQty = { _, _ -> }, onItemAmount = { _, _ -> }, onAddItem = { calls.added++ },
        onRemoveItem = { calls.removed += it }, onTax = {}, onTip = {}, onTotal = {}, onUseComputedTotal = { calls.computed++ },
        onApplyAi = { calls.applied++ }, onDismissAi = { calls.dismissed++ }, onRevealShown = { calls.shown += it },
    )

    private val rows = listOf(
        ItemRow("a", "Paneer Tikka", "1", "250.00"),
        ItemRow("b", "Butter Naan", "2", "90.00"),
        ItemRow("c", "Masala Chaas", "1", "60.00"),
    )
    private val aiForm = ReviewForm(items = rows, tax = "20.00", tip = "", total = "420.00", source = ExtractionSource.LLM)
    private fun editing(form: ReviewForm = aiForm, mismatch: Long? = 0) = ReviewUiState(
        phase = ReviewPhase.EDITING, form = form, subtotalPaise = 40_000, mismatchPaise = mismatch,
    )

    private val state = mutableStateOf(ReviewUiState())

    private fun show(calls: Calls = Calls(), reduceMotion: Boolean = false, fontScale: Float = 1f) {
        rule.mainClock.autoAdvance = false
        rule.setDs(reduceMotion = reduceMotion, fontScale = fontScale) {
            Box(Modifier.fillMaxSize().testTag(ROOT)) { ReviewScreen(state.value, SsSnackbarState(), callbacks(calls)) }
        }
        rule.mainClock.advanceTimeBy(FRAME * 2)
    }

    @Test
    fun `while the bill is read, skeleton lines stand in for the rows and the note is honest about who reads it`() {
        state.value = ReviewUiState(phase = ReviewPhase.EXTRACTING)
        show()
        rule.onNodeWithTag(ReviewTags.READING).assertExists()
        rule.onNodeWithContentDescription("Reading the items")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate))
        rule.onNodeWithText("Asking the AI first. If it can't answer, the built-in parser reads it on this phone.").assertIsDisplayed()
        rule.onNodeWithTag(SplitSmartTestTags.REVIEW_SAVE).assertDoesNotExist()
        // No row is invented while reading: placeholders only.
        rule.onNodeWithTag(SplitSmartTestTags.REVIEW_ITEM).assertDoesNotExist()
    }

    @Test
    fun `an AI reading says so, and the rows read like the receipt`() {
        state.value = editing()
        show()
        rule.onNodeWithContentDescription("Read by AI").assertIsDisplayed()
        rule.onNodeWithContentDescription("Paneer Tikka, quantity 1, ₹250.00").assertIsDisplayed()
        rule.onNodeWithContentDescription("Butter Naan, quantity 2, ₹90.00").assertIsDisplayed()
        rule.onNodeWithText("× 2", substring = true, useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(SplitSmartTestTags.FALLBACK_BANNER).assertDoesNotExist()
    }

    @Test
    fun `a parser reading says who read it and why the AI did not`() {
        state.value = editing(aiForm.copy(source = ExtractionSource.REGEX, llmFailure = LlmFailure.KEY_MISSING))
        show()
        rule.onNodeWithContentDescription("Read by the built-in parser").assertIsDisplayed()
        rule.onNodeWithTag(SplitSmartTestTags.FALLBACK_BANNER).assertIsDisplayed()
        // No key is how this build ships: said in a user's words, with no setup instructions (those live in Me and the docs).
        rule.onNodeWithText("AI reading is off in this build", substring = true).assertIsDisplayed()
        rule.onNodeWithText("LLM_API_KEY", substring = true).assertDoesNotExist()
        rule.onNodeWithText("local.properties", substring = true).assertDoesNotExist()
        // No key: trying the AI again cannot help, so it is not offered.
        rule.onNodeWithTag(SplitSmartTestTags.REVIEW_RERUN).assertDoesNotExist()
    }

    @Test
    fun `a transient AI failure offers one retry with the server's wait`() {
        val calls = Calls()
        state.value = editing(aiForm.copy(source = ExtractionSource.REGEX, llmFailure = LlmFailure.RATE_LIMITED, retryAfterSeconds = 20))
        show(calls)
        rule.onNodeWithText("Try again in 20 s", substring = true).assertIsDisplayed()
        repeat(5) { rule.onNodeWithTag(SplitSmartTestTags.REVIEW_RERUN).performClick() }
        assertThat(calls.reruns).isEqualTo(1)
    }

    @Test
    fun `while the AI refines, the draft is labelled as the parser's and stays editable`() {
        state.value = editing(aiForm.copy(source = ExtractionSource.REGEX)).copy(refining = true)
        show(reduceMotion = true)
        rule.onNodeWithTag(SplitSmartTestTags.REFINING_BANNER).assertIsDisplayed()
        rule.onNodeWithContentDescription("Built-in parser draft").assertIsDisplayed()
        rule.onNodeWithTag(SplitSmartTestTags.FALLBACK_BANNER).assertDoesNotExist()
        rule.onAllNodesWithTag(ReviewTags.ROW_SUMMARY).onFirst().performClick()
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag(SplitSmartTestTags.REVIEW_ITEM_NAME).assertIsDisplayed()
    }

    @Test
    fun `a late AI answer is offered, never forced`() {
        val calls = Calls()
        state.value = editing(aiForm.copy(source = ExtractionSource.REGEX)).copy(pendingAi = aiForm.copy(items = rows.take(2), total = "357.00"))
        show(calls)
        rule.onNodeWithText("AI result is ready: 2 items, total ₹357.00.").assertIsDisplayed()
        rule.onNodeWithTag(SplitSmartTestTags.AI_APPLY).performClick()
        rule.onNodeWithText("Keep mine").performClick()
        assertThat(calls.applied).isEqualTo(1)
        assertThat(calls.dismissed).isEqualTo(1)
    }

    @Test
    fun `repairs are listed in plain words`() {
        state.value = editing(aiForm.copy(warnings = setOf(ExtractionWarning.ITEMS_CAPPED, ExtractionWarning.TOTAL_DERIVED)))
        show()
        rule.onNodeWithTag(ReviewTags.WARNINGS).assertIsDisplayed()
        rule.onNodeWithText("• Only the first 200 lines are here: a bill holds at most 200.").assertIsDisplayed()
        rule.onNodeWithText("• No total was printed, so it was added up from the lines.").assertIsDisplayed()
    }

    @Test
    fun `a row opens in place, one at a time, and closes with Done`() {
        state.value = editing()
        show()
        rule.onNodeWithTag(ReviewTags.EDITOR).assertDoesNotExist()
        rule.onNodeWithContentDescription("Paneer Tikka, quantity 1, ₹250.00").performClick()
        rule.mainClock.advanceTimeBy(600)
        rule.onAllNodesWithTag(ReviewTags.EDITOR).assertCount(1)
        rule.onNodeWithContentDescription("Butter Naan, quantity 2, ₹90.00").performClick()
        // The first editor folds away on the Settle spring while the second opens.
        rule.mainClock.advanceTimeBy(1_500)
        rule.onAllNodesWithTag(ReviewTags.EDITOR).assertCount(1)
        rule.onNodeWithTag(SplitSmartTestTags.REVIEW_ITEM_NAME).assertExists()
        rule.onNodeWithText("Done").performClick()
        rule.mainClock.advanceTimeBy(1_500)
        rule.onNodeWithTag(ReviewTags.EDITOR).assertDoesNotExist()
    }

    @Test
    fun `remove deletes that row only, named for TalkBack`() {
        val calls = Calls()
        state.value = editing()
        show(calls)
        rule.onNodeWithContentDescription("Masala Chaas, quantity 1, ₹60.00").performClick()
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithContentDescription("Remove Masala Chaas").performClick()
        assertThat(calls.removed).containsExactly("c")
    }

    @Test
    fun `an added line opens for typing`() {
        val calls = Calls()
        state.value = editing()
        show(calls)
        rule.onNodeWithTag(SplitSmartTestTags.REVIEW_ADD_ITEM).performClick()
        assertThat(calls.added).isEqualTo(1)
        commitState { state.value = editing(aiForm.copy(items = rows + ItemRow("new", "", "1", ""))) }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithTag(SplitSmartTestTags.REVIEW_ITEM_NAME).assertIsDisplayed()
        rule.onAllNodesWithTag(ReviewTags.EDITOR).assertCount(1)
    }

    @Test
    fun `a save with errors opens the first line that needs a fix and says what is wrong`() {
        state.value = editing()
        show()
        commitState {
            state.value = editing().copy(errors = ReviewErrors(items = mapOf("b" to setOf(ItemError.AMOUNT))))
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("Enter an amount like 1250.50").assertIsDisplayed()
        rule.onNodeWithText("× 2 · Needs a fix", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `totals say which way they are off and fix it in one tap`() {
        val calls = Calls()
        state.value = editing(mismatch = -2_000)
        show(calls)
        rule.onNodeWithTag(ReviewTags.LIST).performScrollToKey("totals")
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithText("Items, tax and tip come to ₹20.00 less than the printed total", substring = true).assertIsDisplayed()
        rule.onNodeWithTag(SplitSmartTestTags.USE_COMPUTED_TOTAL).performClick()
        assertThat(calls.computed).isEqualTo(1)
        commitState { state.value = editing(mismatch = 0) }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithContentDescription("Items + tax + tip match the total").assertIsDisplayed()
        rule.onNodeWithTag(SplitSmartTestTags.MISMATCH_BANNER).assertDoesNotExist()
    }

    @Test
    fun `who paid is one choice, and choosing them again clears it`() {
        val calls = Calls()
        state.value = editing().copy(members = listOf(testMember("m1", name = "Asha"), testMember("m2", name = "Ravi")))
        show(calls)
        rule.onNodeWithText("Ravi").performClick()
        commitState { state.value = state.value.copy(form = aiForm.copy(payerId = "m2")) }
        rule.mainClock.advanceTimeBy(600)
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(600))
        rule.onNodeWithText("Ravi").performClick()
        assertThat(calls.payers).containsExactly("m2", null).inOrder()
    }

    @Test
    fun `save takes one tap and shows it is working`() {
        val calls = Calls()
        state.value = editing()
        show(calls)
        repeat(10) { rule.onNodeWithTag(SplitSmartTestTags.REVIEW_SAVE).performClick() }
        assertThat(calls.saves).isEqualTo(1)
        commitState { state.value = state.value.copy(phase = ReviewPhase.SAVING) }
        rule.mainClock.advanceTimeBy(FRAME * 2)
        rule.onNodeWithTag(SplitSmartTestTags.REVIEW_SAVE).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Working on it"))
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(600))
        rule.onNodeWithTag(SplitSmartTestTags.REVIEW_SAVE).performClick()
        assertThat(calls.saves).isEqualTo(1)
    }

    @Test
    fun `a reading types its lines in from the frame clock, without recomposing per frame, then reports it played`() {
        val calls = Calls()
        state.value = editing().copy(reveal = ItemReveal(7, rows.map { it.key }))
        show(calls)
        val before = recompositionCycles()
        val total = ItemRevealLogic.durationMs(rows.size)
        var t = 0L
        while (t < total) {
            rule.mainClock.advanceTimeBy(FRAME)
            t += FRAME
        }
        assertThat(recompositionCycles() - before).isLessThan(6L)
        rule.mainClock.advanceTimeBy(FRAME * 4)
        assertThat(calls.shown).containsExactly(7L)
        // Every row is there for TalkBack from the first frame: the wipe is drawing only.
        rule.onNodeWithContentDescription("Masala Chaas, quantity 1, ₹60.00").assertExists()
    }

    @Test
    fun `under reduced motion the lines are simply there`() {
        val calls = Calls()
        state.value = editing().copy(reveal = ItemReveal(3, rows.map { it.key }))
        show(calls, reduceMotion = true)
        rule.mainClock.advanceTimeBy(FRAME * 2)
        assertThat(calls.shown).containsExactly(3L)
    }

    @Test
    fun `the largest amounts and a long name fit at 1_3x text`() {
        // A line at the ₹1 crore cap, and a subtotal at the display cap (₹99,99,99,999.99) after 200 such lines.
        val long = ItemRow("x", "Extra large family platter with every curry on the menu and three kinds of bread", "999", "10000000.00")
        state.value = editing(aiForm.copy(items = listOf(long))).copy(subtotalPaise = 999_999_999_999L)
        show(fontScale = 1.3f)
        val row = rule.onAllNodesWithTag(SplitSmartTestTags.REVIEW_ITEM).onFirst()
        rule.onNodeWithContentDescription("Extra large family platter", substring = true).assertExists()
        rule.onNodeWithTag(ReviewTags.ROW_AMOUNT, useUnmergedTree = true).assertInside(row)
        row.assertInside(rule.onNodeWithTag(ROOT))
        rule.onNodeWithTag(ReviewTags.LIST).performScrollToKey("totals")
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithContentDescription("₹99,99,99,999.99", useUnmergedTree = true).assertInside(rule.onNodeWithTag(ROOT))
        rule.onNodeWithTag(SplitSmartTestTags.REVIEW_SAVE).assertInside(rule.onNodeWithTag(ROOT))
    }

    @Test
    fun `the source pill names the AI for TalkBack`() {
        state.value = editing()
        show()
        rule.onNodeWithTag(SplitSmartTestTags.REVIEW_SOURCE + "_pill", useUnmergedTree = true).assertContentDescriptionEquals("Read by AI")
    }

    @Test
    fun `hostile receipt text cannot reorder what a row says`() {
        // A right-to-left override in a line the AI or OCR returned would show "Tea ₹10" as something else.
        val hostile = ItemRow("h", "Tea\u202E 00.009₹\u202C", "1", "10.00")
        state.value = editing(aiForm.copy(items = listOf(hostile)))
        show()
        val description = rule.onAllNodesWithTag(ReviewTags.ROW_SUMMARY).onFirst().fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].single()
        assertThat(description).doesNotContain("\u202E")
        assertThat(description).doesNotContain("\u202C")
        assertThat(description).endsWith("₹10.00")
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCount(n: Int) {
        assertThat(fetchSemanticsNodes()).hasSize(n)
    }

    private companion object {
        const val ROOT = "root"
        const val FRAME = 16L
    }
}
