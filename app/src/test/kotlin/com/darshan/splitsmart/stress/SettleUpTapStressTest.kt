package com.darshan.splitsmart.stress

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.core.DefaultDispatcherProvider
import com.darshan.splitsmart.domain.model.BillSplit
import com.darshan.splitsmart.domain.model.BillStatus
import com.darshan.splitsmart.domain.model.MemberShare
import com.darshan.splitsmart.domain.model.SplitConfig
import com.darshan.splitsmart.domain.model.SplitMode
import com.darshan.splitsmart.integration.onAllNodesWithTagExists
import com.darshan.splitsmart.testutil.FakeBillRepository
import com.darshan.splitsmart.testutil.FakeGroupRepository
import com.darshan.splitsmart.testutil.FakeSettlementRepository
import com.darshan.splitsmart.testutil.FakeUpiAppLauncher
import com.darshan.splitsmart.testutil.RecordingAnalyticsTracker
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testGroup
import com.darshan.splitsmart.testutil.testMember
import com.darshan.splitsmart.testutil.waitUntilWithMainLooper
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.designsystem.recompositionCycles
import com.darshan.splitsmart.ui.designsystem.setDs
import com.darshan.splitsmart.ui.settleup.SettleUpRoute
import com.darshan.splitsmart.ui.settleup.SettleUpTags
import com.darshan.splitsmart.ui.settleup.SettleUpViewModel
import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import kotlin.system.measureTimeMillis
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The settle-up screen with its real view model over 200 people: a long list of payments, and 1,000 taps on Pay. Real dispatchers
 * (no test Main), like the integration tests, so the screen and the view model run as in the app.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class SettleUpTapStressTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `199 payments - first frame, 300 jumps, and 1,000 taps on pay open the upi app once`() {
        val people = (1..200).map { testMember("p$it", name = "Guest $it", vpa = "guest$it@okfake") }
        val random = Random(11)
        val bills = (1..2_000).map { n ->
            val payer = people[random.nextInt(200)].id
            val sharers = (1..random.nextInt(2, 6)).map { people[random.nextInt(200)].id }.distinct()
            val each = random.nextLong(100, 50_000)
            testBill(id = "b$n", totalPaise = each * sharers.size, items = emptyList(), paidByMemberId = payer, status = BillStatus.SPLIT)
                .copy(split = BillSplit(SplitConfig(SplitMode.EQUAL, sharers), sharers.map { MemberShare(it, each, 0, 0, 0) }))
        }
        val groups = FakeGroupRepository().apply {
            this.groups.value = listOf(testGroup())
            members.value = people
        }
        val launcher = FakeUpiAppLauncher()
        val vm = SettleUpViewModel(
            SavedStateHandle(mapOf("groupId" to "g1")), groups, FakeBillRepository().apply { this.bills.value = bills }, FakeSettlementRepository(),
            launcher, RecordingAnalyticsTracker(), DefaultDispatcherProvider(),
        )
        val first = measureTimeMillis {
            rule.setDs(reduceMotion = true) { SettleUpRoute(viewModel = vm, onBack = {}) }
            rule.waitUntilWithMainLooper { rule.onAllNodesWithTagExists(SplitSmartTestTags.SETTLE_PAY) }
        }
        val count = rule.onAllNodesWithTag(SettleUpTags.LIST).fetchSemanticsNodes().size
        assertThat(count).isEqualTo(1)
        val before = recompositionCycles()
        val jumps = measureTimeMillis { repeat(300) { rule.onNodeWithTag(SettleUpTags.LIST).performScrollToIndex(random.nextInt(150)) } }
        val cycles = recompositionCycles() - before
        rule.onNodeWithTag(SettleUpTags.LIST).performScrollToIndex(0)
        val taps = measureTimeMillis {
            repeat(1_000) {
                val pay = rule.onAllNodesWithTag(SplitSmartTestTags.SETTLE_PAY).fetchSemanticsNodes()
                if (pay.isNotEmpty()) rule.onAllNodesWithTag(SplitSmartTestTags.SETTLE_PAY)[0].performClick()
            }
        }
        println("STRESS [settle-list] 200 people, 2,000 bills: first frame ${first}ms, 300 jumps ${jumps}ms, $cycles recomposer cycles; 1,000 taps on Pay in ${taps}ms -> ${launcher.opened.size} UPI link opened")
        assertThat(launcher.opened).hasSize(1)
    }
}
