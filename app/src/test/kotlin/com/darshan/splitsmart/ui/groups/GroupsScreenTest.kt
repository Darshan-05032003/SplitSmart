package com.darshan.splitsmart.ui.groups

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.domain.model.GroupSummary
import com.darshan.splitsmart.testutil.performCustomAction
import com.darshan.splitsmart.testutil.testGroup
import com.darshan.splitsmart.ui.ErrorReason
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.designsystem.FakeHaptics
import com.darshan.splitsmart.ui.designsystem.assertInside
import com.darshan.splitsmart.ui.designsystem.commitState
import com.darshan.splitsmart.ui.designsystem.effects.HapticEvent
import com.darshan.splitsmart.ui.designsystem.recompositionCycles
import com.darshan.splitsmart.ui.designsystem.setDs
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The groups home's behaviour: FAB menu, swipe to archive, archived section, states, motion and text size. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class GroupsScreenTest {
    @get:Rule val rule = createComposeRule()

    private val goa = testGroup("goa", "Goa trip")
    private val flat = testGroup("flat", "Flat 302")
    private val cards = listOf(
        GroupCard(GroupSummary(goa, 3, 4), listOf("Darshan", "Asha", "Ravi"), outstandingPaise = 111_870, yourBalancePaise = 111_870),
        GroupCard(GroupSummary(flat, 2, 2), listOf("Darshan", "Priya"), outstandingPaise = 60_000, yourBalancePaise = -60_000),
    )
    private val overview = HomeOverview("Darshan", 111_870, 60_000, 171_870, groupsWithYou = 2, groupCount = 2)
    private val content = GroupsUiState(LoadState.Content(cards), overview = overview)

    private fun show(state: GroupsUiState = content, callbacks: GroupsCallbacks = GroupsCallbacks(), reduceMotion: Boolean = true, fontScale: Float = 1f, haptics: FakeHaptics = FakeHaptics()) =
        rule.setDs(reduceMotion = reduceMotion, fontScale = fontScale, haptics = haptics) { GroupsScreen(state, callbacks) }

    // ----- FAB menu -----
    @Test
    fun `the FAB bursts into scan, expense and new group, and the scrim closes it`() {
        show()
        rule.onNodeWithTag(GroupsTags.MENU_SCAN).assertDoesNotExist()
        rule.onNodeWithTag(SplitSmartTestTags.FAB_ADD_GROUP).assertContentDescriptionEquals("Add").performClick()
        for (tag in listOf(GroupsTags.MENU_SCAN, GroupsTags.MENU_EXPENSE, GroupsTags.MENU_NEW_GROUP)) rule.onNodeWithTag(tag).assertIsDisplayed()
        rule.onNode(hasTestTag(SplitSmartTestTags.FAB_ADD_GROUP) and hasStateDescription("Menu open")).assertContentDescriptionEquals("Close menu")
        // Nearest the button first: Scan sits just above the FAB, New group at the top.
        val scanTop = rule.onNodeWithTag(GroupsTags.MENU_SCAN).fetchSemanticsNode().boundsInRoot.top
        val newGroupTop = rule.onNodeWithTag(GroupsTags.MENU_NEW_GROUP).fetchSemanticsNode().boundsInRoot.top
        assertThat(newGroupTop).isLessThan(scanTop)
        rule.onNodeWithTag(GroupsTags.MENU_SCRIM).performClick()
        rule.onNodeWithTag(GroupsTags.MENU_SCAN).assertDoesNotExist()
    }

    @Test
    fun `new group from the menu opens the sheet's owner once, however fast the taps come`() {
        var creates = 0
        rule.mainClock.autoAdvance = false
        show(callbacks = GroupsCallbacks(onCreateGroup = { creates++ }), reduceMotion = false)
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithTag(SplitSmartTestTags.FAB_ADD_GROUP).performClick()
        rule.mainClock.advanceTimeBy(1_000)
        // Items stay on screen while the menu closes (each injected tap moves the clock on a little): taps on a fading item
        // must not act again.
        var landed = 0
        while (landed < 100 && rule.onAllNodes(hasTestTag(GroupsTags.MENU_NEW_GROUP)).fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithTag(GroupsTags.MENU_NEW_GROUP).performClick()
            landed++
        }
        assertThat(landed).isGreaterThan(1)
        assertThat(creates).isEqualTo(1)
    }

    @Test
    fun `with one group, add an expense goes straight to it`() {
        var expense: String? = null
        show(GroupsUiState(LoadState.Content(cards.take(1)), overview = overview), GroupsCallbacks(onAddExpense = { expense = it }))
        rule.onNodeWithTag(SplitSmartTestTags.FAB_ADD_GROUP).performClick()
        rule.onNodeWithTag(GroupsTags.MENU_EXPENSE).performClick()
        assertThat(expense).isEqualTo("goa")
        rule.onNodeWithTag(GroupsTags.EXPENSE_PICKER).assertDoesNotExist()
    }

    @Test
    fun `opening the menu animates in the layer, items arriving over frames without recomposing each one`() {
        rule.mainClock.autoAdvance = false
        show(reduceMotion = false)
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithTag(SplitSmartTestTags.FAB_ADD_GROUP).performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag(GroupsTags.MENU_SCAN).assertExists()
        val before = recompositionCycles()
        repeat(30) { rule.mainClock.advanceTimeByFrame() }
        assertThat(recompositionCycles() - before).isLessThan(6)
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithTag(GroupsTags.MENU_NEW_GROUP).assertIsDisplayed()
    }

    // ----- swipe to archive -----
    @Test
    fun `a long swipe archives with a tick at the threshold, a short one springs back`() {
        val archived = mutableListOf<String>()
        val haptics = FakeHaptics()
        show(callbacks = GroupsCallbacks(onArchive = { archived += it }), haptics = haptics)
        val card = rule.onNode(hasTestTag(SplitSmartTestTags.GROUP_ITEM) and androidx.compose.ui.test.hasContentDescription("Goa trip", substring = true))
        card.performTouchInput { swipe(center, center.copy(x = center.x - width * 0.2f), durationMillis = 600) }
        rule.waitForIdle()
        assertThat(archived).isEmpty()
        card.performTouchInput { swipeLeft(startX = right - 1f, endX = left - width * 0.2f, durationMillis = 600) }
        rule.waitForIdle()
        assertThat(archived).containsExactly("goa")
        assertThat(haptics.events).contains(HapticEvent.Tick)
        assertThat(haptics.events.last()).isEqualTo(HapticEvent.Light)
    }

    @Test
    fun `TalkBack archives a card from its actions, and the card reads as one sentence`() {
        val archived = mutableListOf<String>()
        show(callbacks = GroupsCallbacks(onArchive = { archived += it }))
        val card = rule.onNodeWithContentDescription("Flat 302, 2 members, 2 bills, you owe ₹600.00")
        card.performCustomAction("Archive")
        assertThat(archived).containsExactly("flat")
    }

    // ----- archived -----
    @Test
    fun `archived groups fold away at the end, open on tap and come back with Unarchive`() {
        val opened = mutableListOf<String>()
        val unarchived = mutableListOf<String>()
        val old = GroupCard(GroupSummary(testGroup("old", "Old trip").copy(archivedAt = java.time.Instant.EPOCH), 2, 3))
        show(content.copy(archived = listOf(old)), GroupsCallbacks(onOpenGroup = { opened += it }, onUnarchive = { unarchived += it }))
        scrollTo(GroupsTags.ARCHIVED_TOGGLE)
        rule.onNodeWithText("Archived · 1").assertIsDisplayed()
        rule.onNodeWithTag(GroupsTags.ARCHIVED_ITEM).assertDoesNotExist()
        rule.onNodeWithTag(GroupsTags.ARCHIVED_TOGGLE).performClick()
        scrollTo(GroupsTags.ARCHIVED_ITEM)
        rule.onNodeWithTag(GroupsTags.unarchive("old")).performClick()
        rule.onNodeWithTag(GroupsTags.ARCHIVED_ITEM).performClick()
        assertThat(unarchived).containsExactly("old")
        assertThat(opened).containsExactly("old")
        // The swipe hint is for people who have never archived anything.
        rule.onNodeWithText("Swipe a group sideways to archive it").assertDoesNotExist()
    }

    @Test
    fun `with everything archived the page says so above the archived list`() {
        val old = GroupCard(GroupSummary(testGroup("old", "Old trip").copy(archivedAt = java.time.Instant.EPOCH), 2, 3))
        show(GroupsUiState(LoadState.Content(emptyList()), overview = HomeOverview(groupCount = 1), archived = listOf(old)))
        rule.onNodeWithTag(GroupsTags.ALL_ARCHIVED).assertIsDisplayed()
        rule.onNodeWithText("Archived · 1").assertExists()
        rule.onNodeWithTag(SplitSmartTestTags.FAB_ADD_GROUP).assertIsDisplayed()
    }

    // ----- hostile text -----
    @Test
    fun `a bidi override or a novel-length name cannot reorder or flood the card's sentence`() {
        val evil = "\u202Eevil\u202C" + "x".repeat(10_000)
        val card = GroupCard(GroupSummary(testGroup("evil", evil), 1, 1), listOf(evil), outstandingPaise = 100, yourBalancePaise = -100)
        show(GroupsUiState(LoadState.Content(listOf(card)), overview = HomeOverview(evil, 0, 100, 100, 1, 1)))
        val description = rule.onNodeWithTag(SplitSmartTestTags.GROUP_ITEM).fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].single()
        assertThat(description).doesNotContain("\u202E")
        assertThat(description.length).isLessThan(400)
        assertThat(description).endsWith("you owe ₹1.00")
        rule.onNodeWithTag(SplitSmartTestTags.GROUP_ITEM).assertInside(rule.onRoot())
    }

    // ----- states -----
    @Test
    fun `loading is a skeleton that TalkBack reads once`() {
        rule.mainClock.autoAdvance = false
        show(GroupsUiState(LoadState.Loading), reduceMotion = false)
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithTag(SplitSmartTestTags.LOADING).assertContentDescriptionEquals("Loading your groups")
        rule.onNodeWithTag(SplitSmartTestTags.FAB_ADD_GROUP).assertDoesNotExist()
    }

    @Test
    fun `an error explains itself and retries`() {
        var retries = 0
        show(GroupsUiState(LoadState.Error(ErrorReason.STORAGE)), GroupsCallbacks(onRetry = { retries++ }))
        rule.onNodeWithText("Couldn't load your groups").assertIsDisplayed()
        rule.onNodeWithText("Could not read local storage. Try again.").assertIsDisplayed()
        rule.onNodeWithText("Try again").performClick()
        assertThat(retries).isEqualTo(1)
    }

    // ----- hero -----
    @Test
    fun `the hero's colour morph and odometer run in draw and layer, not composition`() {
        var state by mutableStateOf(content)
        rule.mainClock.autoAdvance = false
        rule.setDs { GroupsScreen(state, GroupsCallbacks()) }
        rule.mainClock.advanceTimeBy(2_000)
        val before = recompositionCycles()
        commitState { state = content.copy(overview = overview.copy(youAreOwedPaise = 0)) }
        repeat(40) { rule.mainClock.advanceTimeByFrame() }
        // A value change recomposes the changed digits once; the 40 frames of rolling and colour do not.
        assertThat(recompositionCycles() - before).isLessThan(8)
        rule.onNodeWithText("Overall, you owe").assertIsDisplayed()
        rule.onNodeWithTag(GroupsTags.HERO_AMOUNT, useUnmergedTree = true).assertContentDescriptionEquals("₹600.00")
    }

    @Test
    fun `at 130 percent text the largest amount still fits the hero and every card fits the screen`() {
        val huge = overview.copy(youAreOwedPaise = 99_99_99_999_99L, youOwePaise = 0)
        show(content.copy(overview = huge), fontScale = 1.3f)
        rule.onNodeWithTag(GroupsTags.HERO_AMOUNT, useUnmergedTree = true).assertInside(rule.onNodeWithTag(SplitSmartTestTags.HOME_BALANCE))
        rule.onNodeWithTag(GroupsTags.HERO_AMOUNT, useUnmergedTree = true).assertContentDescriptionEquals("₹99,99,99,999.99")
        scrollTo(SplitSmartTestTags.GROUP_ITEM)
        rule.onNode(hasTestTag(SplitSmartTestTags.GROUP_ITEM) and androidx.compose.ui.test.hasContentDescription("Goa trip", substring = true)).assertInside(rule.onRoot())
    }

    private fun scrollTo(tag: String) {
        rule.onNode(androidx.compose.ui.test.hasScrollToNodeAction()).performScrollToNode(hasTestTag(tag))
    }
}
