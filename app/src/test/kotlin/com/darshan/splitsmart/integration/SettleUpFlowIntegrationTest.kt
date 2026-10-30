package com.darshan.splitsmart.integration

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import com.darshan.splitsmart.data.local.toEntity
import com.darshan.splitsmart.data.repository.BillRepositoryImpl
import com.darshan.splitsmart.data.repository.GroupRepositoryImpl
import com.darshan.splitsmart.domain.model.SplitConfig
import com.darshan.splitsmart.domain.model.SplitMode
import com.darshan.splitsmart.domain.usecase.SaveSplitUseCase
import com.darshan.splitsmart.testutil.FakeUpiAppLauncher
import com.darshan.splitsmart.testutil.fixedClock
import com.darshan.splitsmart.testutil.runOffMain
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testGroup
import com.darshan.splitsmart.testutil.testMember
import com.darshan.splitsmart.testutil.waitUntilWithMainLooper
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.settleup.SettleUpRoute
import com.darshan.splitsmart.ui.settleup.SettleUpViewModel
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
class SettleUpFlowIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: SplitSmartDatabase
    private val dispatchers = DefaultDispatcherProvider()
    private val launcher = FakeUpiAppLauncher()
    private lateinit var groupRepo: GroupRepositoryImpl
    private lateinit var billRepo: BillRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SplitSmartDatabase::class.java).build()
        groupRepo = GroupRepositoryImpl(db.groupDao(), db.memberDao(), fixedClock, dispatchers)
        billRepo = BillRepositoryImpl(db.billDao(), dispatchers)
        runOffMain {
            db.groupDao().insert(testGroup(name = "Goa trip").toEntity())
            db.memberDao().insert(testMember("a", name = "Asha", vpa = "asha@okicici").toEntity())
            db.memberDao().insert(testMember("b", name = "Ravi").toEntity())
            val bill = testBill(totalPaise = 30_000, items = emptyList())
            billRepo.saveBill(bill)
            SaveSplitUseCase(billRepo, RecordingAnalyticsTracker(), RecordingCrashReporter())(bill, SplitConfig(SplitMode.EQUAL, listOf("a", "b")), payerId = "a")
        }
    }

    @After
    fun tearDown() = db.close()

    private fun vm() = SettleUpViewModel(SavedStateHandle(mapOf("groupId" to "g1")), groupRepo, billRepo, launcher, RecordingAnalyticsTracker(), dispatchers)

    @Test
    fun `pay opens the upi link and share sends text with the link`() {
        compose.setContent { SplitSmartTheme { SettleUpRoute(viewModel = vm(), onBack = {}) } }
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.SETTLE_ITEM) }
        compose.onNodeWithText("Ravi owes Asha").assertIsDisplayed()
        compose.onNodeWithText("₹150.00").assertIsDisplayed()
        compose.onNodeWithTag(SplitSmartTestTags.SETTLE_PAY).performClick()
        assertThat(launcher.opened).containsExactly("upi://pay?pa=asha%40okicici&pn=Asha&am=150.00&cu=INR&tn=Goa%20trip%20via%20SplitSmart")
        compose.onNodeWithTag(SplitSmartTestTags.SETTLE_SHARE).performClick()
        assertThat(launcher.shared.single()).contains("upi://pay?pa=asha%40okicici")
        assertThat(launcher.shared.single()).contains("₹150.00")
        compose.onNodeWithTag(SplitSmartTestTags.SETTLE_QR).performClick()
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.QR_IMAGE) }
    }

    @Test
    fun `payee without upi id can get one added inline`() {
        runOffMain {
            // flip: Ravi (no VPA) paid
            val bill = billRepo.getBill("b1")!!
            SaveSplitUseCase(billRepo, RecordingAnalyticsTracker(), RecordingCrashReporter())(bill, SplitConfig(SplitMode.EQUAL, listOf("a", "b")), payerId = "b")
        }
        compose.setContent { SplitSmartTheme { SettleUpRoute(viewModel = vm(), onBack = {}) } }
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.SETTLE_ADD_VPA) }
        assertThat(compose.onAllNodesWithTag(SplitSmartTestTags.SETTLE_PAY).fetchSemanticsNodes()).isEmpty()
        compose.onNodeWithTag(SplitSmartTestTags.SETTLE_ADD_VPA).performClick()
        compose.onNodeWithTag(SplitSmartTestTags.MEMBER_VPA_FIELD).performTextInput("ravi@upi")
        compose.onNodeWithTag(SplitSmartTestTags.DIALOG_CONFIRM).performClick()
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.SETTLE_PAY) }
        assertThat(runOffMain { groupRepo.getMembers("g1") }.first { it.id == "b" }.vpa).isEqualTo("ravi@upi")
    }
}
