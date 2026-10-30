package com.darshan.splitsmart.integration

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.core.DefaultDispatcherProvider
import com.darshan.splitsmart.data.local.SplitSmartDatabase
import com.darshan.splitsmart.data.local.toEntity
import com.darshan.splitsmart.data.repository.BillRepositoryImpl
import com.darshan.splitsmart.data.repository.GroupRepositoryImpl
import com.darshan.splitsmart.domain.usecase.AddMemberUseCase
import com.darshan.splitsmart.domain.usecase.CreateManualBillUseCase
import com.darshan.splitsmart.domain.usecase.SaveSplitUseCase
import com.darshan.splitsmart.testutil.fixedClock
import com.darshan.splitsmart.testutil.runOffMain
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testGroup
import com.darshan.splitsmart.testutil.testItem
import com.darshan.splitsmart.testutil.testMember
import com.darshan.splitsmart.testutil.waitUntilWithMainLooper
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.groupdetail.GroupDetailRoute
import com.darshan.splitsmart.ui.groupdetail.GroupDetailViewModel
import com.darshan.splitsmart.ui.split.SplitRoute
import com.darshan.splitsmart.ui.split.SplitViewModel
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import com.darshan.splitsmart.testutil.RecordingAnalyticsTracker
import com.darshan.splitsmart.testutil.RecordingCrashReporter
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SplitFlowIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: SplitSmartDatabase
    private val dispatchers = DefaultDispatcherProvider()
    private lateinit var billRepo: BillRepositoryImpl
    private lateinit var groupRepo: GroupRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SplitSmartDatabase::class.java).build()
        billRepo = BillRepositoryImpl(db.billDao(), dispatchers)
        groupRepo = GroupRepositoryImpl(db.groupDao(), db.memberDao(), fixedClock, dispatchers)
        runOffMain {
            db.groupDao().insert(testGroup().toEntity())
            db.memberDao().insert(testMember("a", name = "Asha").toEntity())
            db.memberDao().insert(testMember("b", name = "Ravi").toEntity())
            billRepo.saveBill(testBill(totalPaise = 30_000, items = listOf(testItem(unitPaise = 30_000))))
        }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `split by share with a payer, then the group shows who owes whom`() {
        var saved: String? = null
        val vm = SplitViewModel(SavedStateHandle(mapOf("billId" to "b1")), billRepo, groupRepo, SaveSplitUseCase(billRepo, RecordingAnalyticsTracker(), RecordingCrashReporter()), dispatchers)
        compose.setContent { SplitSmartTheme { SplitRoute(viewModel = vm, onBack = {}, onSaved = { saved = it }) } }
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.SPLIT_SAVE) }
        compose.onNodeWithTag(SplitSmartTestTags.SPLIT_MODE_BY_SHARE).performClick()
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTag(SplitSmartTestTags.SPLIT_SHARE_FIELD).fetchSemanticsNodes().size == 2 }
        compose.onAllNodesWithTag(SplitSmartTestTags.SPLIT_SHARE_FIELD)[0].performScrollTo().performTextClearance()
        compose.onAllNodesWithTag(SplitSmartTestTags.SPLIT_SHARE_FIELD)[0].performTextInput("2")
        compose.onAllNodesWithTag(SplitSmartTestTags.SPLIT_PAYER_CHIP)[0].performScrollTo().performClick()
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.SPLIT_PREVIEW_ROW) }
        compose.onNodeWithText("₹200.00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(SplitSmartTestTags.SPLIT_SAVE).performClick()
        compose.waitUntilWithMainLooper { saved == "b1" }
        val bill = runOffMain { billRepo.getBill("b1") }!!
        assertThat(bill.paidByMemberId).isEqualTo("a")
        assertThat(bill.split!!.shares.associate { it.memberId to it.totalPaise }).containsExactly("a", 20_000L, "b", 10_000L)
    }

    @Test
    fun `group detail lists simplified debts for split bills`() {
        runOffMain {
            val bill = billRepo.getBill("b1")!!
            SaveSplitUseCase(billRepo, RecordingAnalyticsTracker(), RecordingCrashReporter())(bill, com.darshan.splitsmart.domain.model.SplitConfig(com.darshan.splitsmart.domain.model.SplitMode.EQUAL, listOf("a", "b")), payerId = "a")
        }
        val vm = GroupDetailViewModel(SavedStateHandle(mapOf("groupId" to "g1")), groupRepo, billRepo, AddMemberUseCase(groupRepo), CreateManualBillUseCase(billRepo, fixedClock), dispatchers)
        compose.setContent { SplitSmartTheme { GroupDetailRoute(viewModel = vm, onBack = {}, onOpenBill = {}, onScanReceipt = {}) } }
        compose.waitUntilWithMainLooper { compose.scrollListTo(SplitSmartTestTags.DEBT_ITEM) } // below the summary and add-bill cards now
        compose.onNodeWithTag(SplitSmartTestTags.DEBT_ITEM).performScrollTo()
        compose.onNodeWithText("Ravi owes Asha").assertExists()
        compose.onNodeWithText("₹150.00").assertExists()
    }
}
