package com.darshan.splitsmart.ui.me

import app.cash.turbine.test
import com.darshan.splitsmart.data.settings.InMemoryProfileRepository
import com.darshan.splitsmart.domain.model.NameError
import com.darshan.splitsmart.testutil.MainDispatcherRule
import com.darshan.splitsmart.ui.designsystem.effects.InMemoryUiPreferences
import com.darshan.splitsmart.ui.designsystem.effects.ThemeMode
import com.darshan.splitsmart.ui.designsystem.effects.UiSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val main = MainDispatcherRule(dispatcher)

    private val profile = InMemoryProfileRepository("Asha")
    private val prefs = InMemoryUiPreferences(UiSettings())

    private fun vm(aiReading: Boolean = false) = MeViewModel(profile, prefs, aiReading)

    @Test
    fun `shows the stored name, the look-and-feel settings and whether AI reading is on in this build`() = runTest(dispatcher) {
        vm(aiReading = true).uiState.test {
            var state = awaitItem()
            while (state.name == null) state = awaitItem()
            assertThat(state.name).isEqualTo("Asha")
            assertThat(state.settings).isEqualTo(UiSettings())
            assertThat(state.aiReading).isTrue()
            assertThat(state.nameDraft).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switches write through to the app-wide settings`() = runTest(dispatcher) {
        val vm = vm()
        vm.setHaptics(false)
        vm.setReduceMotion(true)
        vm.setTheme(ThemeMode.Dark)
        runCurrent()
        assertThat(prefs.settings.value).isEqualTo(UiSettings(hapticsEnabled = false, reduceMotion = true, theme = ThemeMode.Dark))
    }

    @Test
    fun `the name editor validates, trims and saves`() = runTest(dispatcher) {
        val vm = vm()
        vm.uiState.test {
            skipItems(1)
            vm.openNameEditor()
            assertThat(expectMostRecentItemAfterIdle().nameDraft?.name).isEqualTo("Asha")
            vm.onNameChange("   ")
            vm.saveName()
            assertThat(expectMostRecentItemAfterIdle().nameDraft?.error).isEqualTo(NameError.BLANK)
            vm.onNameChange("  Ravi   Kumar ")
            vm.saveName()
            assertThat(expectMostRecentItemAfterIdle().nameDraft).isNull()
            assertThat(profile.yourName.first()).isEqualTo("Ravi Kumar")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a bidi trick in the name is refused, and dismissing keeps the old name`() = runTest(dispatcher) {
        val vm = vm()
        vm.uiState.test {
            skipItems(1)
            vm.openNameEditor()
            assertThat(expectMostRecentItemAfterIdle().nameDraft).isNotNull()
            vm.onNameChange("Asha\u202Eevil")
            vm.saveName()
            assertThat(expectMostRecentItemAfterIdle().nameDraft?.error).isEqualTo(NameError.INVALID_CHARS)
            vm.dismissNameEditor()
            assertThat(expectMostRecentItemAfterIdle().nameDraft).isNull()
            assertThat(profile.yourName.first()).isEqualTo("Asha")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `typing is capped, so a paste of a novel cannot bloat the saved state`() = runTest(dispatcher) {
        val vm = vm()
        vm.uiState.test {
            skipItems(1)
            vm.openNameEditor()
            assertThat(expectMostRecentItemAfterIdle().nameDraft).isNotNull()
            vm.onNameChange("x".repeat(10_000))
            assertThat(expectMostRecentItemAfterIdle().nameDraft!!.name.length).isAtMost(MeViewModel.NAME_INPUT_LIMIT)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<MeUiState>.expectMostRecentItemAfterIdle(): MeUiState {
        dispatcher.scheduler.runCurrent()
        return expectMostRecentItem()
    }
}
