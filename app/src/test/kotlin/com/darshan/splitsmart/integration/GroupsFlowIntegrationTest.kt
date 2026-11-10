package com.darshan.splitsmart.integration

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.core.DefaultDispatcherProvider
import com.darshan.splitsmart.data.local.SplitSmartDatabase
import com.darshan.splitsmart.data.repository.BillRepositoryImpl
import com.darshan.splitsmart.data.repository.GroupRepositoryImpl
import com.darshan.splitsmart.data.repository.SettlementRepositoryImpl
import com.darshan.splitsmart.domain.usecase.AddMemberUseCase
import com.darshan.splitsmart.domain.usecase.CreateGroupUseCase
import com.darshan.splitsmart.domain.usecase.CreateManualBillUseCase
import com.darshan.splitsmart.testutil.fixedClock
import com.darshan.splitsmart.testutil.performCustomAction
import com.darshan.splitsmart.testutil.runOffMain
import com.darshan.splitsmart.testutil.waitUntilWithMainLooper
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.groupdetail.GroupDetailRoute
import com.darshan.splitsmart.ui.groupdetail.GroupDetailViewModel
import com.darshan.splitsmart.ui.groups.GroupsRoute
import com.darshan.splitsmart.ui.groups.GroupsTags
import com.darshan.splitsmart.ui.groups.GroupsViewModel
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Room (in-memory) + real repositories + real ViewModels + Compose screens wired together. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class GroupsFlowIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: SplitSmartDatabase
    private lateinit var groups: GroupRepositoryImpl
    private lateinit var bills: BillRepositoryImpl
    private val dispatchers = DefaultDispatcherProvider()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SplitSmartDatabase::class.java).build()
        groups = GroupRepositoryImpl(db.groupDao(), db.memberDao(), fixedClock, dispatchers)
        bills = BillRepositoryImpl(db.billDao(), dispatchers)
    }

    @After
    fun tearDown() = db.close()

    private fun homeVm() = GroupsViewModel(groups, bills, SettlementRepositoryImpl(db.settlementDao(), fixedClock, dispatchers), com.darshan.splitsmart.data.settings.InMemoryProfileRepository(), CreateGroupUseCase(groups), dispatchers)

    @Test
    fun `empty state then create a group from the sheet and open it`() {
        val vm = homeVm()
        var opened: String? = null
        compose.setContent { SplitSmartTheme(reduceMotion = true) { GroupsRoute(viewModel = vm, onOpenGroup = { opened = it }) } }
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.EMPTY) }
        compose.onNodeWithText("Create your first group").performClick()
        compose.onNodeWithTag(SplitSmartTestTags.GROUP_NAME_FIELD).performTextInput("Goa trip")
        compose.onNodeWithTag(SplitSmartTestTags.DIALOG_CONFIRM).performClick()
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.GROUP_ITEM) }
        compose.onNodeWithContentDescription("Goa trip", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(SplitSmartTestTags.GROUP_ITEM).performClick()
        val stored = runOffMain { groups.observeGroupSummaries().first() }
        assertThat(opened).isEqualTo(stored.single().group.id)
    }

    // A phone-sized screen, so both cards are composed: the rebuilt home does not loop in layout on tall screens.
    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun `the FAB menu makes a second group, and archiving one through TalkBack can be undone`() {
        runOffMain { groups.createGroup("Goa trip") }
        val vm = homeVm()
        compose.setContent { SplitSmartTheme(reduceMotion = true) { GroupsRoute(viewModel = vm, onOpenGroup = {}) } }
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.GROUP_ITEM) }
        compose.onNodeWithTag(SplitSmartTestTags.FAB_ADD_GROUP).performClick()
        compose.onNodeWithTag(GroupsTags.MENU_NEW_GROUP).performClick()
        compose.onNodeWithTag(SplitSmartTestTags.GROUP_NAME_FIELD).performTextInput("Flat 302")
        compose.onNodeWithTag(SplitSmartTestTags.DIALOG_CONFIRM).performClick()
        compose.waitUntilWithMainLooper { compose.onAllNodes(hasTestTag(SplitSmartTestTags.GROUP_ITEM)).fetchSemanticsNodes().size == 2 }

        // The undo window runs on the frame clock: pause it, or an idling test would sit straight through the five seconds.
        compose.mainClock.autoAdvance = false
        val goa = compose.onNode(hasTestTag(SplitSmartTestTags.GROUP_ITEM) and hasContentDescription("Goa trip", substring = true))
        goa.performCustomAction("Archive")
        waitFrameByFrame { compose.onAllNodes(hasText("Goa trip archived")).fetchSemanticsNodes().isNotEmpty() }
        assertThat(runOffMain { groups.observeGroupSummaries().first() }.single { it.group.name == "Goa trip" }.group.isArchived).isTrue()
        compose.onNodeWithText("Undo").performClick()
        waitFrameByFrame { compose.onAllNodes(hasTestTag(SplitSmartTestTags.GROUP_ITEM)).fetchSemanticsNodes().size == 2 }
        assertThat(runOffMain { groups.observeGroupSummaries().first() }.none { it.group.isArchived }).isTrue()
        compose.mainClock.autoAdvance = true
    }

    /** Waits on a paused clock: one frame per check, with the main looper drained for the view model. */
    private fun waitFrameByFrame(condition: () -> Boolean) = compose.waitUntil(5_000) {
        compose.mainClock.advanceTimeByFrame()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        condition()
    }

    @Test
    fun `group detail adds a member and a manual bill`() {
        val group = runOffMain { groups.createGroup("Goa trip") }
        val vm = GroupDetailViewModel(
            SavedStateHandle(mapOf("groupId" to group.id)), groups, bills, SettlementRepositoryImpl(db.settlementDao(), fixedClock, dispatchers),
            AddMemberUseCase(groups), CreateManualBillUseCase(bills, fixedClock), dispatchers,
        )
        var openedBill: String? = null
        compose.setContent {
            SplitSmartTheme { GroupDetailRoute(viewModel = vm, onBack = {}, onOpenBill = { openedBill = it }, onScanReceipt = {}) }
        }
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.ADD_MEMBER_BUTTON) }
        compose.onNodeWithTag(SplitSmartTestTags.ADD_MEMBER_BUTTON).performClick()
        compose.onNodeWithTag(SplitSmartTestTags.MEMBER_NAME_FIELD).performTextInput("Asha")
        compose.onNodeWithTag(SplitSmartTestTags.MEMBER_VPA_FIELD).performTextInput("asha@okicici")
        compose.onNodeWithTag(SplitSmartTestTags.DIALOG_CONFIRM).performClick()
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.MEMBER_ITEM) }
        // A face reads its name, and says when a UPI ID is on file.
        compose.onNodeWithContentDescription("Asha, has a UPI ID").assertIsDisplayed()

        // On Robolectric's 470dp screen the tile may sit below the fold.
        compose.onNodeWithTag(SplitSmartTestTags.ADD_BILL_BUTTON).performScrollTo().performClick()
        compose.onNodeWithTag(SplitSmartTestTags.BILL_TITLE_FIELD).performTextInput("Dinner")
        compose.onNodeWithTag(SplitSmartTestTags.BILL_AMOUNT_FIELD).performTextInput("1250.50")
        compose.onNodeWithTag(SplitSmartTestTags.DIALOG_CONFIRM).performClick()
        compose.waitUntilWithMainLooper { openedBill != null }
        val saved = runOffMain { bills.getBill(openedBill!!) }!!
        assertThat(saved.totalPaise).isEqualTo(125_050)
        assertThat(saved.title).isEqualTo("Dinner")
        // the bill list now sits below the summary, members and add-bill cards: scroll the lazy list to it
        compose.waitUntilWithMainLooper { compose.scrollListTo(SplitSmartTestTags.BILL_ITEM) }
        compose.onNodeWithContentDescription("₹1,250.50").assertIsDisplayed()
    }
}

internal fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTagExists(tag: String): Boolean =
    onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

/**
 * Scrolls the screen's lazy list until a node with [tag] is composed; false while it does not exist yet. The screen's own list is
 * the first scrollable in the tree (the group screen also has a sideways row of faces inside it).
 */
internal fun androidx.compose.ui.test.junit4.ComposeTestRule.scrollListTo(tag: String): Boolean = runCatching {
    onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction()).onFirst().performScrollToNode(androidx.compose.ui.test.hasTestTag(tag))
    Unit
}.isSuccess
