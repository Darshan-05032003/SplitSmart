package com.darshan.splitsmart.ui.onboarding

import app.cash.turbine.test
import com.darshan.splitsmart.data.settings.InMemoryOnboardingRepository
import com.darshan.splitsmart.domain.repository.ExistingGroups
import com.darshan.splitsmart.domain.repository.OnboardingRepository
import com.darshan.splitsmart.testutil.MainDispatcherRule
import com.darshan.splitsmart.ui.splash.SplashPolicy
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class AppStartLogicTest {
    private fun AppStart.after(vararg events: AppStartEvent) = events.fold(this, AppStartLogic::reduce)

    @Test
    fun `the first stored answer decides where a launch starts`() {
        assertThat(AppStart.Loading.after(AppStartEvent.Stored(false))).isEqualTo(AppStart.Onboarding)
        assertThat(AppStart.Loading.after(AppStartEvent.Stored(true))).isEqualTo(AppStart.Ready)
    }

    @Test
    fun `a store that never answers cannot block the app`() {
        assertThat(AppStart.Loading.after(AppStartEvent.TimedOut)).isEqualTo(AppStart.Ready)
        // ...and a late answer does not pull the intro over a screen the user is already using.
        assertThat(AppStart.Loading.after(AppStartEvent.TimedOut, AppStartEvent.Stored(false))).isEqualTo(AppStart.Ready)
    }

    @Test
    fun `finishing or skipping leads into the app, and our own write does not bounce back`() {
        assertThat(AppStart.Onboarding.after(AppStartEvent.Finished)).isEqualTo(AppStart.Ready)
        assertThat(AppStart.Onboarding.after(AppStartEvent.Finished, AppStartEvent.Stored(true))).isEqualTo(AppStart.Ready)
    }

    @Test
    fun `a timeout during the intro keeps the intro`() {
        assertThat(AppStart.Onboarding.after(AppStartEvent.TimedOut)).isEqualTo(AppStart.Onboarding)
    }

    @Test
    fun `the intro can be replayed over the app and finished again`() {
        assertThat(AppStart.Ready.after(AppStartEvent.Replay)).isEqualTo(AppStart.Replaying)
        assertThat(AppStart.Replaying.after(AppStartEvent.Stored(false), AppStartEvent.TimedOut)).isEqualTo(AppStart.Replaying)
        assertThat(AppStart.Ready.after(AppStartEvent.Replay, AppStartEvent.Finished)).isEqualTo(AppStart.Ready)
        // Replay is only offered inside the app: it means nothing before the first answer.
        assertThat(AppStart.Loading.after(AppStartEvent.Replay)).isEqualTo(AppStart.Loading)
    }

    @Test
    fun `finishing before anything is known still opens the app`() {
        assertThat(AppStart.Loading.after(AppStartEvent.Finished)).isEqualTo(AppStart.Ready)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppStartViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val main = MainDispatcherRule(dispatcher)

    @Test
    fun `a fresh install starts on the intro, finishing it stores done and opens the app`() = runTest(dispatcher) {
        val repo = InMemoryOnboardingRepository(completed = false)
        val vm = AppStartViewModel(repo, NO_GROUPS)
        vm.start.test {
            assertThat(awaitItem()).isEqualTo(AppStart.Loading)
            assertThat(awaitItem()).isEqualTo(AppStart.Onboarding)
            vm.finish()
            assertThat(awaitItem()).isEqualTo(AppStart.Ready)
            runCurrent()
            assertThat(repo.completed.first()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a returning user goes straight to the app`() = runTest(dispatcher) {
        val vm = AppStartViewModel(InMemoryOnboardingRepository(completed = true), NO_GROUPS)
        vm.start.test {
            assertThat(awaitItem()).isEqualTo(AppStart.Loading)
            assertThat(awaitItem()).isEqualTo(AppStart.Ready)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a hung store gives way after the splash cap`() = runTest(dispatcher) {
        val hung = object : OnboardingRepository {
            override val completed: Flow<Boolean> = flow { awaitCancellation() }
            override suspend fun setCompleted(completed: Boolean) = Unit
        }
        val vm = AppStartViewModel(hung, NO_GROUPS)
        vm.start.test {
            assertThat(awaitItem()).isEqualTo(AppStart.Loading)
            advanceTimeBy(SplashPolicy.MAX_HOLD_MS - 1)
            runCurrent()
            expectNoEvents()
            advanceTimeBy(2)
            assertThat(awaitItem()).isEqualTo(AppStart.Ready)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `replay shows the intro without forgetting it was done`() = runTest(dispatcher) {
        val repo = InMemoryOnboardingRepository(completed = true)
        val vm = AppStartViewModel(repo, NO_GROUPS)
        vm.start.test {
            skipItems(2)
            vm.replay()
            assertThat(awaitItem()).isEqualTo(AppStart.Replaying)
            runCurrent()
            assertThat(repo.completed.first()).isTrue()
            vm.finish()
            assertThat(awaitItem()).isEqualTo(AppStart.Ready)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `finishing twice writes once`() = runTest(dispatcher) {
        val writes = MutableStateFlow(0)
        val repo = object : OnboardingRepository {
            override val completed: Flow<Boolean> = MutableStateFlow(false)
            override suspend fun setCompleted(completed: Boolean) { writes.value++ }
        }
        val vm = AppStartViewModel(repo, NO_GROUPS)
        runCurrent()
        repeat(1_000) { vm.finish() }
        runCurrent()
        assertThat(writes.value).isEqualTo(1)
        assertThat(vm.start.value).isEqualTo(AppStart.Ready)
    }

    @Test
    fun `someone updating with groups already on the phone skips the intro, and it is remembered`() = runTest(dispatcher) {
        val repo = InMemoryOnboardingRepository(completed = false)
        val vm = AppStartViewModel(repo) { true }
        vm.start.test {
            assertThat(awaitItem()).isEqualTo(AppStart.Loading)
            assertThat(awaitItem()).isEqualTo(AppStart.Ready)
            runCurrent()
            assertThat(repo.completed.first()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a returning user with the intro done never waits on the database`() = runTest(dispatcher) {
        val vm = AppStartViewModel(InMemoryOnboardingRepository(completed = true)) { error("the database must not be read") }
        vm.start.test {
            assertThat(awaitItem()).isEqualTo(AppStart.Loading)
            assertThat(awaitItem()).isEqualTo(AppStart.Ready)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a database that cannot be read still shows the intro`() = runTest(dispatcher) {
        val repo = InMemoryOnboardingRepository(completed = false)
        val vm = AppStartViewModel(repo) { throw IllegalStateException("corrupt") }
        vm.start.test {
            assertThat(awaitItem()).isEqualTo(AppStart.Loading)
            assertThat(awaitItem()).isEqualTo(AppStart.Onboarding)
            runCurrent()
            assertThat(repo.completed.first()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a slow database gives way after the splash cap`() = runTest(dispatcher) {
        val vm = AppStartViewModel(InMemoryOnboardingRepository(completed = false)) { awaitCancellation() }
        vm.start.test {
            assertThat(awaitItem()).isEqualTo(AppStart.Loading)
            advanceTimeBy(SplashPolicy.MAX_HOLD_MS - 1)
            runCurrent()
            expectNoEvents()
            advanceTimeBy(2)
            assertThat(awaitItem()).isEqualTo(AppStart.Ready)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        val NO_GROUPS = ExistingGroups { false }
    }
}
