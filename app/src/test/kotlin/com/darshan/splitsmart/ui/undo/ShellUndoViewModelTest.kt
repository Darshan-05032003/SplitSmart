package com.darshan.splitsmart.ui.undo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.domain.repository.Removal
import com.darshan.splitsmart.testutil.FakeBillRepository
import com.darshan.splitsmart.testutil.FakeGroupRepository
import com.darshan.splitsmart.testutil.FakeTrashRepository
import com.darshan.splitsmart.testutil.MainDispatcherRule
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testGroup
import com.darshan.splitsmart.ui.designsystem.FakeHaptics
import com.darshan.splitsmart.ui.designsystem.commitState
import com.darshan.splitsmart.ui.designsystem.effects.HapticEvent
import com.darshan.splitsmart.ui.designsystem.setDs
import com.darshan.splitsmart.ui.navigation.ShellViewModel
import com.darshan.splitsmart.ui.navigation.UndoOutcome
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Undo after a delete: offered once app-wide, ended exactly once (Undo or timeout), and honest when it can't put things back. */
@OptIn(ExperimentalCoroutinesApi::class)
class ShellUndoViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val mainRule = MainDispatcherRule(dispatcher)

    private val groups = FakeGroupRepository().apply { groups.value = listOf(testGroup()) }
    private val bills = FakeBillRepository().apply { bills.value = listOf(testBill()) }
    private val trash = FakeTrashRepository(groups, bills)
    private val center = UndoCenter()

    @Test
    fun `an offer ends once, whichever comes first, and a newer delete replaces it`() = runTest(dispatcher) {
        val first = trash.removeBill("b1")!!
        center.offer(first)
        val second = trash.removeGroup("g1")!!
        center.offer(second)
        // The older delete stands: its Undo can no longer take it.
        assertThat(center.take(first)).isFalse()
        assertThat(center.take(second)).isTrue()
        assertThat(center.take(second)).isFalse()
        assertThat(center.offer.value).isNull()
    }

    @Test
    fun `1,000 Undo taps put the group back once`() = runTest(dispatcher) {
        val vm = ShellViewModel(groups, trash, center)
        val outcomes = mutableListOf<UndoOutcome>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.undoOutcomes.collect { outcomes += it } }
        val removal = trash.removeGroup("g1")!!
        center.offer(removal)
        repeat(1_000) { vm.undo(removal) }
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(trash.restores).hasSize(1)
        assertThat(groups.groups.value).containsExactly(testGroup())
        assertThat(bills.bills.value).containsExactly(testBill())
        assertThat(outcomes).containsExactly(UndoOutcome.Restored)
        assertThat(vm.undoOffer.value).isNull()
    }

    @Test
    fun `after the window closes Undo does nothing and the delete stands`() = runTest(dispatcher) {
        val vm = ShellViewModel(groups, trash, center)
        val removal = trash.removeGroup("g1")!!
        center.offer(removal)
        vm.expire(removal)
        vm.undo(removal)
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(trash.restores).isEmpty()
        assertThat(groups.groups.value).isEmpty()
    }

    @Test
    fun `a put-back that can't be exact is reported, not half done`() = runTest(dispatcher) {
        val vm = ShellViewModel(groups, trash, center)
        val outcomes = mutableListOf<UndoOutcome>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.undoOutcomes.collect { outcomes += it } }
        trash.restoreFails = true
        val removal = trash.removeBill("b1")!!
        center.offer(removal)
        vm.undo(removal)
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(outcomes).containsExactly(UndoOutcome.Failed)
        assertThat(bills.bills.value).isEmpty()
    }
}
