package com.darshan.splitsmart.ui.split

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.BillSource
import com.darshan.splitsmart.domain.model.Member
import com.darshan.splitsmart.domain.model.SplitConfig
import com.darshan.splitsmart.domain.model.SplitMode
import com.darshan.splitsmart.domain.split.SplitCalculator
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testItem
import com.darshan.splitsmart.testutil.testMember
import com.darshan.splitsmart.ui.ErrorReason
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.designsystem.assertInside
import com.darshan.splitsmart.ui.designsystem.commitState
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.darshan.splitsmart.ui.designsystem.recompositionCycles
import com.darshan.splitsmart.ui.designsystem.setDs
import com.google.common.truth.Truth.assertThat
import androidx.compose.runtime.mutableStateOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Fixtures shared by the split screen's UI and screenshot tests: a fictional beach-shack dinner for three. */
internal object SplitFixtures {
    val members: List<Member> = listOf(testMember("a", name = "Asha"), testMember("r", name = "Ravi"), testMember("m", name = "Meera"))
    val bill: Bill = testBill(
        title = "Beach shack dinner",
        totalPaise = 1_262_50, // lines ₹1,130 + tax ₹62.50 + tip ₹100 − a ₹30 discount
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

    fun editor(
        config: SplitConfig = SplitConfig(SplitMode.EQUAL, SplitFixtures.members.map { it.id }),
        shareTexts: Map<String, String> = SplitFixtures.members.associate { it.id to "1" },
        payer: String? = "a",
        selected: Set<String> = emptySet(),
        bill: Bill = this.bill,
        members: List<Member> = this.members,
    ): SplitEditor {
        val withShares = config.copy(shares = shareTexts.mapValues { it.value.toIntOrNull() ?: 0 })
        val left = SplitLogic.percentLeft(withShares)
        val preview = if (left != null && left != 0) null else runCatching { SplitCalculator.split(bill, withShares) }.getOrNull()
        return SplitEditor(bill, members, withShares, payer, shareTexts, preview, null, selected = selected)
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class SplitScreenTest {
    @get:Rule val rule = createComposeRule()

    private class Calls {
        val modes = mutableListOf<SplitModeOption>()
        val toggledItems = mutableListOf<Pair<String, String>>()
        val picks = mutableListOf<String>()
        val assigned = mutableListOf<Pair<List<String>, String>>()
        val assignedPicks = mutableListOf<String>()
        val steps = mutableListOf<Pair<String, Int>>()
        var saves = 0
        var retries = 0
        var cleared = 0
        val payers = mutableListOf<String?>()
        val participants = mutableListOf<String>()

        fun callbacks() = SplitCallbacks(
            onBack = {}, onRetry = { retries++ }, onMode = { modes += it }, onToggleParticipant = { participants += it },
            onToggleItem = { item, member -> toggledItems += item to member }, onShareText = { _, _ -> }, onPayer = { payers += it },
            onSave = { saves++ }, onToggleSelect = { picks += it }, onClearSelection = { cleared++ },
            onAssign = { ids, member -> assigned += ids.toList() to member }, onAssignSelected = { assignedPicks += it },
            onStepShare = { id, delta -> steps += id to delta },
        )
    }

    private fun show(editor: SplitEditor, calls: Calls = Calls(), fontScale: Float = 1f, reduceMotion: Boolean = true, saving: Boolean = false): Calls {
        rule.setDs(fontScale = fontScale, reduceMotion = reduceMotion) {
            SplitScreen(SplitUiState(LoadState.Content(editor), saving = saving), SsSnackbarState(), calls.callbacks())
        }
        return calls
    }

    private fun scrollTo(key: String) = rule.onNodeWithTag(SplitTags.LIST).performScrollToKey(key)

    @Test
    fun `the donut, four modes, the payer and a paise-exact check`() {
        show(SplitFixtures.editor())
        rule.onNodeWithTag(SplitTags.DONUT).assertIsDisplayed()
        listOf(SplitSmartTestTags.SPLIT_MODE_EQUAL, SplitSmartTestTags.SPLIT_MODE_BY_ITEM, SplitSmartTestTags.SPLIT_MODE_BY_SHARE, SplitTags.MODE_PERCENT)
            .forEach { rule.onNodeWithTag(it).assertExists() }
        scrollTo("person:a")
        rule.onNodeWithText("paid").assertIsDisplayed()
        scrollTo("check")
        rule.onNodeWithText("Shares add up to ₹1,262.50, exactly the bill total (paise-exact).").assertIsDisplayed()
        rule.onAllNodesWithTag(SplitSmartTestTags.SPLIT_PREVIEW_ROW).assertCountEquals(3)
        // ₹1,262.50 over three: 420.84 + 420.83 + 420.83, read out per person.
        rule.onNodeWithContentDescription("Asha pays ₹420.84").assertExists()
        rule.onNodeWithContentDescription("Ravi pays ₹420.83").assertExists()
    }

    @Test
    fun `mode chips report the chosen option and the selected one is checked`() {
        val calls = show(SplitFixtures.editor())
        rule.onNodeWithTag(SplitSmartTestTags.SPLIT_MODE_EQUAL).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        rule.onNodeWithTag(SplitTags.MODE_PERCENT).performClick()
        rule.onNodeWithTag(SplitSmartTestTags.SPLIT_MODE_BY_ITEM).performClick()
        assertThat(calls.modes).containsExactly(SplitModeOption.Percent, SplitModeOption.ByItem).inOrder()
    }

    @Test
    fun `payer and participant faces call back, and say whether they are chosen`() {
        val calls = show(SplitFixtures.editor(config = SplitConfig(SplitMode.EQUAL, listOf("a", "r"))))
        rule.onNodeWithContentDescription("Meera, left out").assertExists()
        rule.onNodeWithContentDescription("Asha, in the split").assertExists()
        rule.onAllNodesWithTag(SplitSmartTestTags.SPLIT_PAYER_CHIP)[1].performClick()
        rule.onAllNodesWithTag(SplitSmartTestTags.SPLIT_PARTICIPANT)[2].performClick()
        assertThat(calls.payers).containsExactly("r")
        assertThat(calls.participants).containsExactly("m")
        rule.onAllNodesWithTag(SplitSmartTestTags.SPLIT_PAYER_CHIP)[0].assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
    }

    @Test
    fun `by item - tapping a line opens its owners and a chip toggles one person`() {
        val calls = show(SplitFixtures.editor(config = SplitConfig(SplitMode.BY_ITEM, listOf("a", "r", "m"), itemAssignments = mapOf("i1" to setOf("a")))))
        scrollTo("itemsHeader") // the faces stay pinned above the lines
        rule.onAllNodesWithTag(SplitSmartTestTags.SPLIT_ITEM_CHIP).assertCountEquals(0)
        rule.onNodeWithText("Paneer tikka").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithTag(SplitSmartTestTags.SPLIT_ITEM_CHIP).assertCountEquals(3)
        rule.onAllNodesWithTag(SplitSmartTestTags.SPLIT_ITEM_CHIP)[1].performClick()
        assertThat(calls.toggledItems).containsExactly("i1" to "r")
        rule.onNodeWithText("× 2 · Asha").assertExists() // who has the tikka
        rule.onAllNodesWithText("Shared by everyone").onFirst().assertExists() // the dosa: nobody given it yet
    }

    @Test
    fun `long-press picks a line, and a face takes the picks`() {
        val calls = show(SplitFixtures.editor(config = SplitConfig(SplitMode.BY_ITEM, listOf("a", "r", "m")), selected = setOf("i1", "i3")))
        rule.onNodeWithText("2 picked. Tap a face to hand them over.").assertIsDisplayed()
        scrollTo("itemsHeader")
        rule.onNodeWithText("Masala dosa").performTouchInput { longClick() }
        assertThat(calls.picks).containsExactly("i2")
        rule.onNodeWithContentDescription("Ravi, 0 lines").performClick()
        assertThat(calls.assignedPicks).containsExactly("r")
        rule.onNodeWithTag(SplitTags.CLEAR_PICKS).performClick()
        assertThat(calls.cleared).isEqualTo(1)
    }

    @Test
    fun `without picks a face is only a drop target`() {
        val calls = show(SplitFixtures.editor(config = SplitConfig(SplitMode.BY_ITEM, listOf("a", "r", "m"))))
        rule.onNodeWithContentDescription("Ravi, 0 lines").performClick()
        assertThat(calls.assignedPicks).isEmpty()
        rule.onNodeWithText(rule.activityString(com.darshan.splitsmart.R.string.split_dock_hint)).assertIsDisplayed()
    }

    @Test
    fun `dragging a line onto a face assigns it, a drop anywhere else does nothing`() {
        val calls = show(SplitFixtures.editor(config = SplitConfig(SplitMode.BY_ITEM, listOf("a", "r", "m"))))
        scrollTo("itemsHeader")
        dragHandleTo("i1", "Ravi, 0 lines")
        assertThat(calls.assigned).containsExactly(listOf("i1") to "r")
        dragHandleBy("i1", Offset(0f, 40f))
        assertThat(calls.assigned).hasSize(1)
    }

    @Test
    fun `dragging a picked line carries every pick with it`() {
        val calls = show(SplitFixtures.editor(config = SplitConfig(SplitMode.BY_ITEM, listOf("a", "r", "m")), selected = setOf("i1", "i3")))
        scrollTo("itemsHeader")
        dragHandleTo("i3", "Meera, 0 lines")
        assertThat(calls.assigned.single().first).containsExactly("i1", "i3")
        assertThat(calls.assigned.single().second).isEqualTo("m")
    }

    @Test
    fun `faces count their lines and every line owned is said in words`() {
        val owned = mapOf("i1" to setOf("a"), "i2" to setOf("a", "r"), "i3" to setOf("m"), "i4" to setOf("r"))
        show(SplitFixtures.editor(config = SplitConfig(SplitMode.BY_ITEM, listOf("a", "r", "m"), itemAssignments = owned)))
        rule.onNodeWithContentDescription("Asha, 2 lines").assertExists()
        rule.onNodeWithContentDescription("Meera, 1 line").assertExists()
        scrollTo("itemsHeader")
        rule.onNodeWithTag(SplitTags.ALL_ASSIGNED).assertIsDisplayed()
    }

    @Test
    fun `percent mode says what is left and keeps save off until it adds up`() {
        val config = SplitConfig(SplitMode.BY_SHARE, listOf("a", "r", "m"), percent = true)
        show(SplitFixtures.editor(config = config, shareTexts = mapOf("a" to "50", "r" to "20", "m" to "10")))
        scrollTo("check")
        rule.onNodeWithText("20% still to give out").assertIsDisplayed()
        rule.onNodeWithTag(SplitSmartTestTags.SPLIT_SAVE).assertIsNotEnabled()
        rule.onAllNodesWithTag(SplitSmartTestTags.SPLIT_SHARE_FIELD).assertCountEquals(3)
    }

    @Test
    fun `share steppers step one person at a time`() {
        val calls = show(SplitFixtures.editor(config = SplitConfig(SplitMode.BY_SHARE, listOf("a", "r", "m"))))
        scrollTo("person:r")
        rule.onNodeWithContentDescription("One more for Ravi").performClick()
        rule.onNodeWithContentDescription("One less for Ravi").performClick()
        assertThat(calls.steps).containsExactly("r" to 1, "r" to -1).inOrder()
    }

    @Test
    fun `a thousand rapid taps on save are one save`() {
        // Like the view model: the first accepted tap turns the button busy. The clock is paused, so the taps are rapid.
        val calls = Calls()
        val state = mutableStateOf(SplitUiState(LoadState.Content(SplitFixtures.editor())))
        val callbacks = calls.callbacks().copy(onSave = { calls.saves++; state.value = state.value.copy(saving = true) })
        rule.mainClock.autoAdvance = false
        rule.setDs(reduceMotion = true) { SplitScreen(state.value, SsSnackbarState(), callbacks) }
        rule.mainClock.advanceTimeBy(500)
        repeat(1_000) { rule.onNodeWithTag(SplitSmartTestTags.SPLIT_SAVE).performClick() }
        assertThat(calls.saves).isEqualTo(1)
    }

    @Test
    fun `saving keeps the button busy`() {
        val calls = show(SplitFixtures.editor(), saving = true)
        rule.onNodeWithTag(SplitSmartTestTags.SPLIT_SAVE).performClick()
        assertThat(calls.saves).isEqualTo(0)
    }

    @Test
    fun `loading and error states are designed, and error retries`() {
        val calls = Calls()
        val state = mutableStateOf(SplitUiState())
        rule.mainClock.autoAdvance = false
        rule.setDs(reduceMotion = true) { SplitScreen(state.value, SsSnackbarState(), calls.callbacks()) }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag(SplitSmartTestTags.LOADING).assertExists()
        commitState { state.value = SplitUiState(LoadState.Error(ErrorReason.NOT_FOUND)) }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithTag(SplitSmartTestTags.ERROR).assertExists()
        rule.onNodeWithText("Try again").performClick()
        assertThat(calls.retries).isEqualTo(1)
    }

    @Test
    fun `at 1·3x text the rows stay on screen`() {
        show(SplitFixtures.editor(config = SplitConfig(SplitMode.BY_SHARE, listOf("a", "r", "m"))), fontScale = 1.3f)
        scrollTo("person:a")
        rule.onAllNodesWithTag(SplitSmartTestTags.SPLIT_PREVIEW_ROW).onFirst().assertInside(rule.onRoot())
        rule.onAllNodesWithTag(SplitSmartTestTags.SPLIT_SHARE_FIELD).onFirst().assertInside(rule.onRoot())
    }

    @Test
    fun `the donut morphs between modes in draw, not by recomposing every frame`() {
        val state = mutableStateOf(SplitUiState(LoadState.Content(SplitFixtures.editor())))
        rule.mainClock.autoAdvance = false
        rule.setDs(reduceMotion = false) { SplitScreen(state.value, SsSnackbarState(), Calls().callbacks()) }
        rule.mainClock.advanceTimeBy(2_000)
        val byItem = SplitFixtures.editor(config = SplitConfig(SplitMode.BY_ITEM, listOf("a", "r", "m"), itemAssignments = mapOf("i1" to setOf("a"))))
        commitState { state.value = SplitUiState(LoadState.Content(byItem)) }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        val before = recompositionCycles()
        repeat(30) { rule.mainClock.advanceTimeByFrame() }
        // Rolling amounts and the donut animate inside layers and draw; only a few settle-time recompositions are allowed.
        assertThat(recompositionCycles() - before).isAtMost(6L)
    }

    private fun handleCenter(itemId: String): Offset {
        val node = rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, SplitTags.handle(itemId))).fetchSemanticsNode()
        return node.boundsInRoot.center
    }

    private fun dragHandleTo(itemId: String, faceDescription: String) {
        val target = rule.onNode(hasContentDescription(faceDescription)).fetchSemanticsNode().boundsInRoot.center
        dragHandleBy(itemId, target - handleCenter(itemId))
    }

    private fun dragHandleBy(itemId: String, delta: Offset) {
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, SplitTags.handle(itemId))).performTouchInput {
            down(center)
            val steps = 12
            repeat(steps) { moveBy(delta / steps.toFloat()) }
            up()
        }
        rule.waitForIdle()
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.activityString(id: Int): String =
    androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)
