package com.darshan.splitsmart.ui.design

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.domain.model.BillSource
import com.darshan.splitsmart.domain.model.GroupSummary
import com.darshan.splitsmart.domain.model.MemberShare
import com.darshan.splitsmart.domain.model.SplitConfig
import com.darshan.splitsmart.domain.model.SplitMode
import com.darshan.splitsmart.domain.model.SplitResult
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testGroup
import com.darshan.splitsmart.testutil.testItem
import com.darshan.splitsmart.testutil.testMember
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.groupdetail.GroupDetail
import com.darshan.splitsmart.ui.groupdetail.GroupDetailCallbacks
import com.darshan.splitsmart.ui.groupdetail.GroupDetailScreen
import com.darshan.splitsmart.ui.groupdetail.GroupDetailUiState
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.darshan.splitsmart.ui.groups.GroupCard
import com.darshan.splitsmart.ui.groups.GroupsCallbacks
import com.darshan.splitsmart.ui.groups.GroupsTags
import com.darshan.splitsmart.ui.groups.GroupsScreen
import com.darshan.splitsmart.ui.groups.GroupsUiState
import com.darshan.splitsmart.ui.groups.HomeOverview
import com.darshan.splitsmart.ui.groups.ProfileCallbacks
import com.darshan.splitsmart.ui.navigation.ShellTags
import com.darshan.splitsmart.ui.split.SplitCallbacks
import com.darshan.splitsmart.ui.split.SplitEditor
import com.darshan.splitsmart.ui.split.SplitScreen
import com.darshan.splitsmart.ui.split.SplitTags
import com.darshan.splitsmart.ui.split.SplitUiState
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The redesigned screens' new states: home balances, AI badges, gallery entry point, paise-exact split check. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RedesignedScreensTest {
    @get:Rule val compose = createComposeRule()

    private val goa = testGroup("goa", "Goa trip")

    private fun home(state: GroupsUiState, callbacks: GroupsCallbacks = GroupsCallbacks()) = compose.setContent {
        // Reduced motion: the empty page's illustration would otherwise bob forever and an auto-advancing clock never idles.
        SplitSmartTheme(reduceMotion = true) { GroupsScreen(state = state, callbacks = callbacks) }
    }

    @Test
    fun `home shows your net balance with what you get and pay, and where you stand per group`() {
        val cards = listOf(
            GroupCard(GroupSummary(goa, 2, 1), listOf("Darshan", "Asha"), outstandingPaise = 15_000, yourBalancePaise = 15_000),
            GroupCard(GroupSummary(testGroup("flat", "Flat 302"), 2, 1), listOf("Darshan", "Ravi"), outstandingPaise = 10_000, yourBalancePaise = -10_000),
        )
        home(GroupsUiState(LoadState.Content(cards), overview = HomeOverview("Darshan", 15_000, 10_000, 25_000, groupsWithYou = 2, groupCount = 2)))
        compose.onNodeWithText("Hi Darshan 👋").assertIsDisplayed()
        compose.onNodeWithText("Overall, you're owed").assertIsDisplayed()
        compose.onNodeWithTag(GroupsTags.HERO_AMOUNT, useUnmergedTree = true).assertContentDescriptionEquals("₹50.00")
        compose.onNodeWithContentDescription("you get ₹150.00", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("you pay ₹100.00", useUnmergedTree = true).assertExists()
        compose.scrollListToDescription("you're owed ₹150.00")
        compose.onNodeWithContentDescription("Goa trip, 2 members, 1 bill, you're owed ₹150.00").assertIsDisplayed()
        compose.scrollListToDescription("you owe ₹100.00")
        compose.onNodeWithContentDescription("Flat 302, 2 members, 1 bill, you owe ₹100.00").assertIsDisplayed()
    }

    @Test
    fun `without a name the hero shows what is left to settle and asks for your name in one place`() {
        var edits = 0
        home(
            GroupsUiState(LoadState.Content(listOf(GroupCard(GroupSummary(goa, 2, 1), outstandingPaise = 25_000))), overview = HomeOverview(null, outstandingPaise = 25_000, groupCount = 1)),
            GroupsCallbacks(profile = ProfileCallbacks(onEdit = { edits++ })),
        )
        compose.onNodeWithText("Still to settle").assertIsDisplayed()
        compose.onNodeWithTag(GroupsTags.HERO_AMOUNT, useUnmergedTree = true).assertContentDescriptionEquals("₹250.00")
        compose.scrollListToDescription("₹250.00 to settle")
        compose.onNodeWithContentDescription("₹250.00 to settle", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(SplitSmartTestTags.SET_NAME_PROMPT).performClick()
        assertThat(edits).isEqualTo(1)
        // The old person icon duplicated the Me tab and is gone; so is the hero's scan button (the bar and the FAB have Scan).
        compose.onNodeWithTag(SplitSmartTestTags.PROFILE_BUTTON).assertDoesNotExist()
        compose.onNodeWithTag(SplitSmartTestTags.HOME_SCAN).assertDoesNotExist()
    }

    @Test
    fun `scan from the FAB menu hands over to the shell's scan once`() {
        var scans = 0
        home(GroupsUiState(LoadState.Content(listOf(GroupCard(GroupSummary(goa, 0, 0))))), GroupsCallbacks(onScan = { scans++ }))
        compose.onNodeWithTag(SplitSmartTestTags.FAB_ADD_GROUP).performClick()
        compose.onNodeWithTag(GroupsTags.MENU_SCAN).performClick()
        assertThat(scans).isEqualTo(1)
        // The menu closed on the way out.
        compose.onNodeWithTag(GroupsTags.MENU_SCAN).assertDoesNotExist()
    }

    @Test
    fun `adding an expense with several groups asks which group`() {
        var picked: String? = null
        val cards = listOf(GroupCard(GroupSummary(goa, 0, 0)), GroupCard(GroupSummary(testGroup("flat", "Flat 302"), 0, 0)))
        home(GroupsUiState(LoadState.Content(cards)), GroupsCallbacks(onAddExpense = { picked = it }))
        compose.onNodeWithTag(SplitSmartTestTags.FAB_ADD_GROUP).performClick()
        compose.onNodeWithTag(GroupsTags.MENU_EXPENSE).performClick()
        compose.onNodeWithText("Which group is this expense for?").assertIsDisplayed()
        compose.onNodeWithTag(ShellTags.scanChoice("flat")).performClick()
        assertThat(picked).isEqualTo("flat")
    }

    @Test
    fun `empty home offers to create the first group and hides the FAB`() {
        var added = 0
        home(GroupsUiState(LoadState.Empty), GroupsCallbacks(onCreateGroup = { added++ }))
        compose.onNodeWithText("Create your first group").performClick()
        assertThat(added).isEqualTo(1)
        compose.onNodeWithTag(SplitSmartTestTags.FAB_ADD_GROUP).assertDoesNotExist()
    }

    private fun detailCallbacks(onGallery: () -> Unit = {}) = GroupDetailCallbacks(
        onBack = {}, onRetry = {}, onOpenBill = {}, onScan = {}, onSettleUp = {}, onDeleteGroup = {}, onAddMember = {}, onDismissMember = {},
        onMemberName = {}, onMemberVpa = {}, onSubmitMember = {}, onRemoveMember = {}, onAddBill = {}, onDismissBill = {}, onBillTitle = {},
        onBillAmount = {}, onBillPayer = {}, onSubmitBill = {}, onScanFromGallery = onGallery,
    )

    @Test
    fun `group detail marks AI bills, names the payer and offers gallery import`() {
        var gallery = 0
        val asha = testMember("m1", "goa", "Asha")
        val bills = listOf(
            testBill(id = "ai", groupId = "goa", title = "Marigold Cafe", source = BillSource.SCAN_LLM, paidByMemberId = "m1"),
            testBill(id = "manual", groupId = "goa", title = "Cab", source = BillSource.MANUAL),
        )
        compose.setContent {
            SplitSmartTheme(reduceMotion = true) {
                GroupDetailScreen(GroupDetailUiState(LoadState.Content(GroupDetail(goa, listOf(asha), bills))), SsSnackbarState(), detailCallbacks { gallery++ })
            }
        }
        compose.onNodeWithTag(SplitSmartTestTags.GALLERY_ADD_BUTTON).performScrollTo().performClick()
        assertThat(gallery).isEqualTo(1)
        compose.scrollListToText("Cab") // the manual bill: no AI badge, no payer
        compose.onNodeWithText("no payer yet", substring = true).assertExists()
        compose.scrollListToText("Marigold Cafe") // the AI bill, paid by Asha
        // the badge sits inside the clickable bill card, whose semantics are merged: look in the unmerged tree
        assertThat(compose.onAllNodesWithTag(SplitSmartTestTags.AI_BADGE, useUnmergedTree = true).fetchSemanticsNodes()).hasSize(1)
        compose.onNodeWithText("Paid by Asha", substring = true).assertExists()
    }

    @Test
    fun `split preview confirms the shares are paise-exact`() {
        val bill = testBill(totalPaise = 30_000, items = listOf(testItem(unitPaise = 30_000)))
        val members = listOf(testMember("a", name = "Asha"), testMember("b", name = "Ravi"))
        val preview = SplitResult(SplitMode.EQUAL, listOf(MemberShare("a", 15_000, 0, 0, 0), MemberShare("b", 15_000, 0, 0, 0)))
        val editor = SplitEditor(bill, members, SplitConfig(SplitMode.EQUAL, listOf("a", "b")), "a", emptyMap(), preview, null)
        compose.setContent {
            SplitSmartTheme {
                SplitScreen(SplitUiState(LoadState.Content(editor)), SsSnackbarState(), SplitCallbacks({}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, {}))
            }
        }
        // The split screen is a lazy list now: rows further down are reached by key.
        compose.onNodeWithTag(SplitTags.LIST).performScrollToKey("check")
        compose.onNodeWithText("Shares add up to ₹300.00, exactly the bill total (paise-exact).").assertIsDisplayed()
        compose.onNodeWithTag(SplitTags.LIST).performScrollToKey("person:a")
        compose.onNodeWithText("paid").assertIsDisplayed()
        compose.onNodeWithTag(SplitTags.LIST).performScrollToKey("person:b")
        assertThat(compose.onAllNodesWithTag(SplitSmartTestTags.SPLIT_PREVIEW_ROW).fetchSemanticsNodes()).hasSize(2)
        compose.onNodeWithContentDescription("Asha pays ₹150.00").assertExists()
        compose.onNodeWithContentDescription("Ravi pays ₹150.00").assertExists()
    }
}

private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithText(text: String) =
    onAllNodes(androidx.compose.ui.test.hasText(text))

private fun androidx.compose.ui.test.junit4.ComposeTestRule.scrollListToDescription(text: String) {
    onNode(androidx.compose.ui.test.hasScrollToNodeAction()).performScrollToNode(androidx.compose.ui.test.hasContentDescription(text, substring = true))
}

private fun androidx.compose.ui.test.junit4.ComposeTestRule.scrollListToText(text: String) {
    // The screen's list is the first scrollable; the group screen also has a sideways row of faces.
    onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction()).onFirst().performScrollToNode(androidx.compose.ui.test.hasText(text, substring = true))
}
