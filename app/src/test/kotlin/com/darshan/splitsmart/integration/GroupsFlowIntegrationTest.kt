package com.darshan.splitsmart.integration

import androidx.compose.ui.test.assertIsDisplayed
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
import com.darshan.splitsmart.domain.usecase.AddMemberUseCase
import com.darshan.splitsmart.domain.usecase.CreateGroupUseCase
import com.darshan.splitsmart.domain.usecase.CreateManualBillUseCase
import com.darshan.splitsmart.testutil.fixedClock
import com.darshan.splitsmart.testutil.runOffMain
import com.darshan.splitsmart.testutil.waitUntilWithMainLooper
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.groupdetail.GroupDetailRoute
import com.darshan.splitsmart.ui.groupdetail.GroupDetailViewModel
import com.darshan.splitsmart.ui.groups.GroupsRoute
import com.darshan.splitsmart.ui.groups.GroupsViewModel
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
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

    @Test
    fun `empty state then create a group from the dialog and open it`() {
        val vm = GroupsViewModel(groups, CreateGroupUseCase(groups), dispatchers)
        var opened: String? = null
        compose.setContent { SplitSmartTheme { GroupsRoute(viewModel = vm, onOpenGroup = { opened = it }) } }
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.EMPTY) }
        compose.onNodeWithTag(SplitSmartTestTags.FAB_ADD_GROUP).performClick()
        compose.onNodeWithTag(SplitSmartTestTags.GROUP_NAME_FIELD).performTextInput("Goa trip")
        compose.onNodeWithTag(SplitSmartTestTags.DIALOG_CONFIRM).performClick()
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.GROUP_ITEM) }
        compose.onNodeWithText("Goa trip").assertIsDisplayed()
        compose.onNodeWithTag(SplitSmartTestTags.GROUP_ITEM).performClick()
        val stored = runOffMain { groups.observeGroupSummaries().first() }
        assertThat(opened).isEqualTo(stored.single().group.id)
    }

    @Test
    fun `group detail adds a member and a manual bill`() {
        val group = runOffMain { groups.createGroup("Goa trip") }
        val vm = GroupDetailViewModel(
            SavedStateHandle(mapOf("groupId" to group.id)), groups, bills,
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
        compose.onNodeWithText("Asha").assertIsDisplayed()

        compose.onNodeWithTag(SplitSmartTestTags.ADD_BILL_BUTTON).performClick()
        compose.onNodeWithTag(SplitSmartTestTags.BILL_TITLE_FIELD).performTextInput("Dinner")
        compose.onNodeWithTag(SplitSmartTestTags.BILL_AMOUNT_FIELD).performTextInput("1250.50")
        compose.onNodeWithTag(SplitSmartTestTags.DIALOG_CONFIRM).performClick()
        compose.waitUntilWithMainLooper { openedBill != null }
        val saved = runOffMain { bills.getBill(openedBill!!) }!!
        assertThat(saved.totalPaise).isEqualTo(125_050)
        assertThat(saved.title).isEqualTo("Dinner")
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.BILL_ITEM) }
        compose.onNodeWithText("₹1,250.50").assertIsDisplayed()
    }
}

internal fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTagExists(tag: String): Boolean =
    onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
