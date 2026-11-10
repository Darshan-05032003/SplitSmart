package com.darshan.splitsmart.ui.history

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.darshan.splitsmart.domain.model.Settlement
import com.darshan.splitsmart.testutil.FakeBillRepository
import com.darshan.splitsmart.testutil.FakeGroupRepository
import com.darshan.splitsmart.testutil.FakeSettlementRepository
import com.darshan.splitsmart.testutil.MainDispatcherRule
import com.darshan.splitsmart.testutil.TestDispatcherProvider
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testGroup
import com.darshan.splitsmart.testutil.testMember
import com.darshan.splitsmart.testutil.testSettlement
import com.darshan.splitsmart.ui.ErrorReason
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.designsystem.effects.HapticEvent
import com.darshan.splitsmart.ui.designsystem.effects.UiEffect
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val mainRule = MainDispatcherRule(dispatcher)
    private val groups = FakeGroupRepository()
    private val bills = FakeBillRepository()
    private val settlements = FakeSettlementRepository()

    @Before
    fun seed() {
        groups.groups.value = listOf(testGroup())
        groups.members.value = listOf(testMember("a", name = "Asha"), testMember("b", name = "Ravi"))
        bills.bills.value = listOf(testBill("b1", title = "Dinner", totalPaise = 1_000, items = emptyList(), paidByMemberId = "a"))
        settlements.settlements.value = listOf(testSettlement("s1", from = "b", to = "a", amountPaise = 500))
    }

    private fun vm(groupId: String = "g1") = HistoryViewModel(SavedStateHandle(mapOf("groupId" to groupId)), groups, bills, settlements, TestDispatcherProvider(dispatcher))

    @Test
    fun `a failed read shows the error, and retry reads again`() = runTest(dispatcher) {
        val failing = object : FakeSettlementRepository() {
            var calls = 0
            override fun observeSettlements(groupId: String): Flow<List<Settlement>> = flow {
                if (calls++ == 0) throw android.database.sqlite.SQLiteException("disk") else emitAll(super.observeSettlements(groupId))
            }
        }
        failing.settlements.value = settlements.settlements.value
        val vm = HistoryViewModel(SavedStateHandle(mapOf("groupId" to "g1")), groups, bills, failing, TestDispatcherProvider(dispatcher))
        vm.uiState.test {
            var state = awaitItem()
            while (state.content !is LoadState.Error) state = awaitItem()
            assertThat(state.content).isEqualTo(LoadState.Error(ErrorReason.STORAGE))
            vm.retry()
            while (state.content !is LoadState.Content) state = awaitItem()
            assertThat((state.content as LoadState.Content).data.entryCount).isEqualTo(2)
        }
    }

    @Test
    fun `history has the timeline and the insights of the group`() = runTest(dispatcher) {
        vm().uiState.test {
            var state = awaitItem()
            while (state.content !is LoadState.Content) state = awaitItem()
            val content = (state.content as LoadState.Content).data
            assertThat(content.entryCount).isEqualTo(2)
            assertThat(content.insights.topPayers.single().label).isEqualTo("Asha")
            assertThat(content.insights.settledPaise).isEqualTo(500)
        }
    }

    @Test
    fun `removing a payment opened in its sheet happens once, with a heavy haptic and a message`() = runTest(dispatcher) {
        val vm = vm()
        val effects = mutableListOf<UiEffect>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(dispatcher.scheduler)) { vm.effects.collect { effects += it } }
        vm.events.test {
            vm.select(settlements.settlements.value.single())
            repeat(1_000) { vm.remove() }
            dispatcher.scheduler.advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(HistoryEvent.Removed("Ravi", "Asha"))
        }
        assertThat(settlements.settlements.value).isEmpty()
        assertThat(effects).containsExactly(UiEffect.Haptic(HapticEvent.Heavy))
        // Nothing open, nothing removed.
        vm.remove()
        dispatcher.scheduler.advanceUntilIdle()
        job.cancel()
    }

    @Test
    fun `a failed removal keeps the payment and says so`() = runTest(dispatcher) {
        val vm = vm()
        settlements.failWith = IllegalStateException("disk")
        vm.events.test {
            vm.select(settlements.settlements.value.single())
            vm.remove()
            dispatcher.scheduler.advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(HistoryEvent.Failed)
        }
        assertThat(settlements.settlements.value).hasSize(1)
    }

    @Test
    fun `nothing yet is empty and a missing group is not found`() = runTest(dispatcher) {
        bills.bills.value = emptyList()
        settlements.settlements.value = emptyList()
        vm().uiState.test {
            awaitItem()
            assertThat(awaitItem().content).isEqualTo(LoadState.Empty)
        }
        vm("nope").uiState.test {
            awaitItem()
            assertThat(awaitItem().content).isEqualTo(LoadState.Error(ErrorReason.NOT_FOUND))
        }
    }
}
