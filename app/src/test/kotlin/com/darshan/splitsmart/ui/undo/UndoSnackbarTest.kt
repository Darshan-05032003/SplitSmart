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

/** The shell's "… deleted · Undo" snackbar: names what went, hands it back once, and makes it final when the window closes. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class UndoSnackbarTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `the snackbar names what was deleted and Undo hands it back once`() {
        val removal = Removal.Group("g1", "Goa trip", FakeTrashRepository.Rows(testGroup(), emptyList(), emptyList()))
        val undone = mutableListOf<Removal>()
        val expired = mutableListOf<Removal>()
        val haptics = FakeHaptics()
        rule.mainClock.autoAdvance = false
        rule.setDs(haptics = haptics) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                UndoSnackbarHost(removal, onUndo = { undone += it }, onExpire = { expired += it })
            }
        }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("Goa trip deleted").assertExists()
        rule.onNodeWithText("Undo").performClick()
        rule.onNodeWithText("Undo").performClick()
        rule.mainClock.advanceTimeBy(100)
        assertThat(undone).containsExactly(removal)
        assertThat(expired).isEmpty()
        assertThat(haptics.events).containsExactly(HapticEvent.Light)
    }

    @Test
    fun `when the window runs out the delete is made final`() {
        val removal = Removal.Bill("b1", "g1", "Beach shack dinner", FakeTrashRepository.Rows(null, emptyList(), listOf(testBill())))
        val undone = mutableListOf<Removal>()
        val expired = mutableListOf<Removal>()
        rule.mainClock.autoAdvance = false
        rule.setDs {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                UndoSnackbarHost(removal, onUndo = { undone += it }, onExpire = { expired += it })
            }
        }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("Beach shack dinner deleted").assertExists()
        rule.mainClock.advanceTimeBy(5_500)
        assertThat(expired).containsExactly(removal)
        assertThat(undone).isEmpty()
    }

    @Test
    fun `a newer delete replaces the snackbar without ending the older one twice`() {
        val first = Removal.Bill("b1", "g1", "Ferry tickets", FakeTrashRepository.Rows(null, emptyList(), emptyList()))
        val second = Removal.Bill("b2", "g1", "Villa", FakeTrashRepository.Rows(null, emptyList(), emptyList()))
        var offer by mutableStateOf<Removal?>(first)
        val expired = mutableListOf<Removal>()
        rule.mainClock.autoAdvance = false
        rule.setDs {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                UndoSnackbarHost(offer, onUndo = {}, onExpire = { expired += it })
            }
        }
        rule.mainClock.advanceTimeBy(500)
        commitState { offer = second }
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("Villa deleted").assertExists()
        rule.mainClock.advanceTimeBy(5_500)
        assertThat(expired).containsExactly(second)
    }

    @Test
    fun `a failed Undo says so with a reject haptic`() {
        val outcomes = MutableSharedFlow<UndoOutcome>(extraBufferCapacity = 1)
        val haptics = FakeHaptics()
        rule.mainClock.autoAdvance = false
        rule.setDs(haptics = haptics) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                UndoSnackbarHost(null, onUndo = {}, onExpire = {}, outcomes = outcomes)
            }
        }
        rule.mainClock.advanceTimeBy(100)
        outcomes.tryEmit(UndoOutcome.Failed)
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("Couldn't bring it back: something in it changed meanwhile.").assertExists()
        assertThat(haptics.events).contains(HapticEvent.Reject)
    }
}
