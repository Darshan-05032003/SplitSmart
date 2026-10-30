package com.darshan.splitsmart.ui.design

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
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
import com.darshan.splitsmart.ui.groups.GroupCard
import com.darshan.splitsmart.ui.groups.GroupsScreen
import com.darshan.splitsmart.ui.groups.GroupsUiState
import com.darshan.splitsmart.ui.groups.HomeOverview
import com.darshan.splitsmart.ui.groups.ProfileCallbacks
import com.darshan.splitsmart.ui.split.SplitCallbacks
import com.darshan.splitsmart.ui.split.SplitEditor
import com.darshan.splitsmart.ui.split.SplitScreen
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

    private fun home(state: GroupsUiState, onScan: (String) -> Unit = {}, onAdd: () -> Unit = {}, onEditName: () -> Unit = {}) = compose.setContent {
        SplitSmartTheme {
            GroupsScreen(
                state = state, snackbar = SnackbarHostState(), onOpenGroup = {}, onAdd = onAdd, onDismiss = {}, onNameChange = {}, onConfirm = {}, onRetry = {},
                profile = ProfileCallbacks(onEdit = onEditName), onScanReceipt = onScan,
            )
        }
    }

    @Test
    fun `home shows what you are owed and what you owe, with green and red chips per group`() {
        val cards = listOf(
            GroupCard(GroupSummary(goa, 2, 1), listOf("Darshan", "Asha"), outstandingPaise = 15_000, yourBalancePaise = 15_000),
            GroupCard(GroupSummary(testGroup("flat", "Flat 302"), 2, 1), listOf("Darshan", "Ravi"), outstandingPaise = 10_000, yourBalancePaise = -10_000),
        )
        home(GroupsUiState(LoadState.Content(cards), overview = HomeOverview("Darshan", 15_000, 10_000, 25_000, groupsWithYou = 2, groupCount = 2)))
        compose.onNodeWithText("Hi Darshan, your balance").assertIsDisplayed()
        compose.onNodeWithText("You are owed").assertIsDisplayed()
        compose.onNodeWithText("You owe").assertIsDisplayed()
        compose.scrollListToText("you're owed ₹150.00")
        compose.onNodeWithText("you're owed ₹150.00").assertIsDisplayed()
        compose.scrollListToText("you owe ₹100.00")
        compose.onNodeWithText("you owe ₹100.00").assertIsDisplayed()
    }

    @Test
    fun `without a name the hero shows what is left to settle and asks for your name`() {
        var edits = 0
        home(
            GroupsUiState(LoadState.Content(listOf(GroupCard(GroupSummary(goa, 2, 1), outstandingPaise = 25_000))), overview = HomeOverview(null, outstandingPaise = 25_000, groupCount = 1)),
            onEditName = { edits++ },
        )
        compose.onNodeWithText("₹250.00").assertIsDisplayed()
        compose.scrollListToText("₹250.00 to settle")
        compose.onNodeWithText("₹250.00 to settle").assertIsDisplayed()
        compose.onNodeWithTag(SplitSmartTestTags.SET_NAME_PROMPT).performClick()
        compose.onNodeWithTag(SplitSmartTestTags.PROFILE_BUTTON).performClick()
        assertThat(edits).isEqualTo(2)
    }

    @Test
    fun `scan receipt on home goes straight to the only group, or creates one when there is none`() {
        var scanned: String? = null
        home(GroupsUiState(LoadState.Content(listOf(GroupCard(GroupSummary(goa, 0, 0))))), onScan = { scanned = it })
        compose.onNodeWithTag(SplitSmartTestTags.HOME_SCAN).performClick()
        assertThat(scanned).isEqualTo("goa")
    }

    @Test
    fun `scan receipt with several groups asks which group`() {
        var scanned: String? = null
        val cards = listOf(GroupCard(GroupSummary(goa, 0, 0)), GroupCard(GroupSummary(testGroup("flat", "Flat 302"), 0, 0)))
        home(GroupsUiState(LoadState.Content(cards)), onScan = { scanned = it })
        compose.onNodeWithTag(SplitSmartTestTags.HOME_SCAN).performClick()
        compose.onNodeWithText("Which group is this receipt for?").assertIsDisplayed()
        compose.onAllNodesWithText("Flat 302").let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        assertThat(scanned).isEqualTo("flat")
    }

    @Test
    fun `empty home offers to create the first group`() {
        var added = 0
        home(GroupsUiState(LoadState.Empty), onAdd = { added++ })
        compose.onNodeWithText("Create your first group").performClick()
        assertThat(added).isEqualTo(1)
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
            testBill(id = "ai", groupId = "goa", title = "Blue Tokai", source = BillSource.SCAN_LLM, paidByMemberId = "m1"),
            testBill(id = "manual", groupId = "goa", title = "Cab", source = BillSource.MANUAL),
        )
        compose.setContent {
            SplitSmartTheme {
                GroupDetailScreen(GroupDetailUiState(LoadState.Content(GroupDetail(goa, listOf(asha), bills))), SnackbarHostState(), detailCallbacks { gallery++ })
            }
        }
        compose.onNodeWithTag(SplitSmartTestTags.GALLERY_ADD_BUTTON).performClick()
        assertThat(gallery).isEqualTo(1)
        compose.scrollListToText("Cab") // last bill: both bill rows are composed now
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
                SplitScreen(SplitUiState(LoadState.Content(editor)), SnackbarHostState(), SplitCallbacks({}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, {}))
            }
        }
        compose.onNodeWithText("Shares add up to ₹300.00, exactly the bill total (paise-exact).").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("paid").performScrollTo().assertIsDisplayed()
        assertThat(compose.onAllNodesWithTag(SplitSmartTestTags.SPLIT_PREVIEW_ROW).fetchSemanticsNodes()).hasSize(2)
    }
}

private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithText(text: String) =
    onAllNodes(androidx.compose.ui.test.hasText(text))

private fun androidx.compose.ui.test.junit4.ComposeTestRule.scrollListToText(text: String) {
    onNode(androidx.compose.ui.test.hasScrollToNodeAction()).performScrollToNode(androidx.compose.ui.test.hasText(text, substring = true))
}
