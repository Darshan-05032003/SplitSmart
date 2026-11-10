package com.darshan.splitsmart.integration

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.core.DefaultDispatcherProvider
import com.darshan.splitsmart.data.local.SplitSmartDatabase
import com.darshan.splitsmart.data.local.toEntity
import com.darshan.splitsmart.data.repository.BillRepositoryImpl
import com.darshan.splitsmart.data.repository.GroupRepositoryImpl
import com.darshan.splitsmart.data.repository.SettlementRepositoryImpl
import com.darshan.splitsmart.domain.model.SettlementMethod
import com.darshan.splitsmart.domain.model.SplitConfig
import com.darshan.splitsmart.domain.model.SplitMode
import com.darshan.splitsmart.domain.usecase.SaveSplitUseCase
import com.darshan.splitsmart.testutil.FakeUpiAppLauncher
import com.darshan.splitsmart.testutil.RecordingAnalyticsTracker
import com.darshan.splitsmart.testutil.RecordingCrashReporter
import com.darshan.splitsmart.testutil.fixedClock
import com.darshan.splitsmart.testutil.runOffMain
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testGroup
import com.darshan.splitsmart.testutil.testMember
import com.darshan.splitsmart.testutil.waitUntilWithMainLooper
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.settleup.SettleUpRoute
import com.darshan.splitsmart.ui.settleup.SettleUpTags
import com.darshan.splitsmart.ui.settleup.SettleUpViewModel
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Room + repositories + ViewModel + the rebuilt screen: pay, come back, say yes, all square. A fake launcher stands in for the UPI app. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class SettleUpFlowIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var db: SplitSmartDatabase
    private val dispatchers = DefaultDispatcherProvider()
    private val launcher = FakeUpiAppLauncher()
    private lateinit var groupRepo: GroupRepositoryImpl
    private lateinit var billRepo: BillRepositoryImpl
    private lateinit var settlementRepo: SettlementRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SplitSmartDatabase::class.java).build()
        groupRepo = GroupRepositoryImpl(db.groupDao(), db.memberDao(), fixedClock, dispatchers)
        billRepo = BillRepositoryImpl(db.billDao(), dispatchers)
        settlementRepo = SettlementRepositoryImpl(db.settlementDao(), fixedClock, dispatchers)
        runOffMain {
            db.groupDao().insert(testGroup(name = "Goa trip").toEntity())
            db.memberDao().insert(testMember("a", name = "Asha", vpa = "asha@okfake").toEntity())
            db.memberDao().insert(testMember("b", name = "Ravi").toEntity())
            val bill = testBill(totalPaise = 30_000, items = emptyList())
            billRepo.saveBill(bill)
            SaveSplitUseCase(billRepo, RecordingAnalyticsTracker(), RecordingCrashReporter())(bill, SplitConfig(SplitMode.EQUAL, listOf("a", "b")), payerId = "a")
        }
    }

    @After
    fun tearDown() = db.close()

    private fun vm() = SettleUpViewModel(SavedStateHandle(mapOf("groupId" to "g1")), groupRepo, billRepo, settlementRepo, launcher, RecordingAnalyticsTracker(), dispatchers)

    private fun show(onOpenBill: (String) -> Unit = {}) {
        compose.setContent { SplitSmartTheme(reduceMotion = true) { SettleUpRoute(viewModel = vm(), onBack = {}, onOpenBill = onOpenBill) } }
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.SETTLE_ITEM) }
    }

    @Test
    fun `pay opens the upi link, share sends text with the link, and the qr sheet shows the code`() {
        show()
        compose.onNodeWithText("Ravi owes Asha").assertIsDisplayed()
        compose.onNode(hasContentDescription("₹150.00") and hasAnyAncestor(hasTestTag(SplitSmartTestTags.SETTLE_ITEM))).assertIsDisplayed()
        compose.onNodeWithTag(SplitSmartTestTags.SETTLE_PAY).performClick()
        assertThat(launcher.opened).containsExactly("upi://pay?pa=asha%40okfake&pn=Asha&am=150.00&cu=INR&tn=Goa%20trip%20via%20SplitSmart")
        // While the UPI app is open the card waits for the person; Cancel brings the payment back.
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SettleUpTags.CHECK_NOT_YET) }
        compose.onNodeWithText("Paying in your UPI app").assertIsDisplayed()
        compose.onNodeWithTag(SettleUpTags.CHECK_NOT_YET).performClick()
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.SETTLE_SHARE) }
        compose.onNodeWithTag(SplitSmartTestTags.SETTLE_SHARE).performClick()
        assertThat(launcher.shared.single()).contains("upi://pay?pa=asha%40okfake")
        assertThat(launcher.shared.single()).contains("₹150.00")
        compose.onNodeWithTag(SplitSmartTestTags.SETTLE_QR).performClick()
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.QR_IMAGE) }
        compose.onNodeWithContentDescription("UPI QR code to pay Asha ₹150.00").assertIsDisplayed()
    }

    @Test
    fun `back from the upi app it asks, yes records the payment in Room and the group is all square`() {
        show()
        compose.onNodeWithTag(SplitSmartTestTags.SETTLE_PAY).performClick()
        // The UPI app covers SplitSmart, then the person comes back.
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SettleUpTags.CHECK_YES) }
        compose.onNodeWithText("Did it go through?").assertIsDisplayed()
        assertThat(runOffMain { db.settlementDao().count() }).isEqualTo(0)
        compose.onNodeWithTag(SettleUpTags.CHECK_YES).performClick()
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SettleUpTags.ALL_SQUARE) }
        compose.onNodeWithText("All square").assertIsDisplayed()
        compose.onNodeWithContentDescription("All square").assertExists()
        val stored = runOffMain { db.settlementDao().observeByGroup("g1").first() }.single()
        assertThat(stored.fromMemberId).isEqualTo("b")
        assertThat(stored.toMemberId).isEqualTo("a")
        assertThat(stored.amountPaise).isEqualTo(15_000)
        assertThat(stored.method).isEqualTo(SettlementMethod.UPI_APP.name)
        assertThat(compose.onAllNodesWithTag(SplitSmartTestTags.SETTLE_ITEM).fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun `not yet records nothing and the payment stays`() {
        show()
        compose.onNodeWithTag(SettleUpTags.MARK_PAID).performClick()
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SettleUpTags.CHECK_NOT_YET) }
        compose.onNodeWithText("Mark this as paid?").assertIsDisplayed()
        compose.onNodeWithTag(SettleUpTags.CHECK_NOT_YET).performClick()
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.SETTLE_PAY) }
        assertThat(runOffMain { db.settlementDao().count() }).isEqualTo(0)
    }

    @Test
    fun `payee without upi id can get one added inline`() {
        runOffMain {
            // flip: Ravi (no VPA) paid
            val bill = billRepo.getBill("b1")!!
            SaveSplitUseCase(billRepo, RecordingAnalyticsTracker(), RecordingCrashReporter())(bill, SplitConfig(SplitMode.EQUAL, listOf("a", "b")), payerId = "b")
        }
        compose.setContent { SplitSmartTheme(reduceMotion = true) { SettleUpRoute(viewModel = vm(), onBack = {}) } }
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.SETTLE_ADD_VPA) }
        assertThat(compose.onAllNodesWithTag(SplitSmartTestTags.SETTLE_PAY).fetchSemanticsNodes()).isEmpty()
        compose.onNodeWithText("Ravi has no UPI ID yet, so there is no payment link.").assertIsDisplayed()
        compose.onNodeWithTag(SplitSmartTestTags.SETTLE_ADD_VPA).performClick()
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.MEMBER_VPA_FIELD) }
        compose.onNodeWithTag(SplitSmartTestTags.MEMBER_VPA_FIELD).performTextInput("ravi@upi")
        compose.onNodeWithTag(SplitSmartTestTags.DIALOG_CONFIRM).performClick()
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.SETTLE_PAY) }
        assertThat(runOffMain { groupRepo.getMembers("g1") }.first { it.id == "b" }.vpa).isEqualTo("ravi@upi")
    }

    @Test
    fun `a bill whose member left is named with the reason and opens the bill`() {
        runOffMain {
            db.memberDao().insert(testMember("c", name = "Meera").toEntity())
            val ferry = testBill("b2", title = "Ferry", totalPaise = 9_000, items = emptyList())
            billRepo.saveBill(ferry)
            SaveSplitUseCase(billRepo, RecordingAnalyticsTracker(), RecordingCrashReporter())(ferry, SplitConfig(SplitMode.EQUAL, listOf("a", "b", "c")), payerId = "a")
            groupRepo.removeMember("c")
        }
        var opened: String? = null
        show(onOpenBill = { opened = it })
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SettleUpTags.TO_FIX_ROW) }
        compose.onNodeWithTag(SettleUpTags.LIST).performScrollToKey("toFix")
        compose.onNodeWithText("Someone in its split left the group. Split it again.").assertIsDisplayed()
        // Only the dinner moves money until the ferry is split again.
        compose.onNode(hasContentDescription("₹150.00") and hasAnyAncestor(hasTestTag(SplitSmartTestTags.SETTLE_ITEM))).assertIsDisplayed()
        compose.onNodeWithTag(SettleUpTags.TO_FIX_ROW).performClick()
        assertThat(opened).isEqualTo("b2")
    }
}
