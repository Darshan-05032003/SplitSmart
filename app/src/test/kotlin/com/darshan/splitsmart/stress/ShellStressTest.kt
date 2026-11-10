package com.darshan.splitsmart.stress

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.material3.Text
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.data.settings.InMemoryOnboardingRepository
import com.darshan.splitsmart.testutil.MainDispatcherRule
import com.darshan.splitsmart.ui.designsystem.recompositionCycles
import com.darshan.splitsmart.ui.designsystem.setDs
import com.darshan.splitsmart.ui.navigation.GroupsDestination
import com.darshan.splitsmart.ui.navigation.MeDestination
import com.darshan.splitsmart.ui.navigation.ScanChoice
import com.darshan.splitsmart.ui.navigation.ScanTarget
import com.darshan.splitsmart.ui.navigation.ShellLogic
import com.darshan.splitsmart.ui.navigation.ShellRoute
import com.darshan.splitsmart.ui.navigation.StatusBarController
import com.darshan.splitsmart.ui.navigation.StatusBarEntry
import com.darshan.splitsmart.ui.navigation.StatusBarLogic
import com.darshan.splitsmart.ui.navigation.openTab
import com.darshan.splitsmart.ui.onboarding.AppStart
import com.darshan.splitsmart.ui.onboarding.AppStartViewModel
import com.darshan.splitsmart.ui.onboarding.OnboardingScreen
import com.darshan.splitsmart.ui.onboarding.OnboardingTags
import com.darshan.splitsmart.ui.designsystem.tokens.MarigoldLight
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

/** Load for the shell and the intro: thousands of taps, tab switches and hand-overs. Prints `STRESS [...]` lines for the report. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ShellStressTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `1,000 skip taps finish the intro once`() {
        var finishes = 0
        rule.mainClock.autoAdvance = false
        rule.setDs(reduceMotion = true) { OnboardingScreen(onFinish = { finishes++ }) }
        rule.mainClock.advanceTimeBy(500)
        val ms = measureTimeMillis { repeat(1_000) { rule.onNodeWithTag(OnboardingTags.SKIP).performClick() } }
        rule.mainClock.advanceTimeBy(500)
        println("STRESS [intro skip] 1,000 taps -> $finishes finish in $ms ms")
        assertThat(finishes).isEqualTo(1)
    }

    @Test
    fun `1,000 next taps walk to the last page and finish at most once`() {
        var finishes = 0
        rule.mainClock.autoAdvance = false
        rule.setDs { Box(Modifier.fillMaxSize().testTag("root")) { OnboardingScreen(onFinish = { finishes++ }) } }
        rule.mainClock.advanceTimeBy(500)
        val ms = measureTimeMillis {
            repeat(1_000) {
                ShadowSystemClock.advanceBy(Duration.ofMillis(600))
                if (rule.onAllNodesWithTagCount(OnboardingTags.NEXT) > 0) rule.onNodeWithTag(OnboardingTags.NEXT).performClick()
                rule.mainClock.advanceTimeBy(16)
            }
            rule.mainClock.advanceTimeBy(1_500)
        }
        // Once the last page is reached the rising button covers the spot where Next was, so a hammering thumb finishes the intro:
        // once, never twice.
        println("STRESS [intro next] 1,000 taps -> on the last page, $finishes finish, $ms ms")
        assertThat(finishes).isAtMost(1)
        ShadowSystemClock.advanceBy(Duration.ofMillis(600))
        rule.onNodeWithTag(OnboardingTags.CTA).performClick()
        assertThat(finishes).isEqualTo(1)
    }

    @Test
    fun `a 40-frame swipe across the intro does not recompose per frame`() {
        rule.mainClock.autoAdvance = false
        rule.setDs { OnboardingScreen(onFinish = {}) }
        rule.mainClock.advanceTimeBy(500)
        val before = recompositionCycles()
        rule.onNodeWithTag(OnboardingTags.PAGER).performTouchInput {
            down(center)
            repeat(40) {
                moveBy(Offset(-width / 60f, 0f))
                rule.mainClock.advanceTimeBy(16)
            }
            up()
        }
        rule.mainClock.advanceTimeBy(1_000)
        val cycles = recompositionCycles() - before
        println("STRESS [intro swipe] 40 frames of drag + settle -> $cycles recomposer cycles")
        // A page change recomposes the indicator's label and the controls a few times; the layers and pill draw from the pager.
        assertThat(cycles).isLessThan(20)
    }

    @Test
    fun `1,000 tab switches keep one copy of each tab on the back stack`() {
        lateinit var nav: NavHostController
        rule.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = GroupsDestination) {
                composable<GroupsDestination> { Text("groups") }
                composable<MeDestination> { Text("me") }
            }
        }
        val ms = measureTimeMillis {
            repeat(1_000) { i -> rule.runOnIdle { nav.openTab(if (i % 2 == 0) MeDestination else GroupsDestination) } }
            rule.waitForIdle()
        }
        val depth = rule.runOnIdle { nav.currentBackStack.value.count { it.destination.route != null && it.destination.parent != null } }
        println("STRESS [tabs] 1,000 switches -> back stack $depth destinations in $ms ms")
        assertThat(depth).isAtMost(2)
    }

    @Test
    fun `status bar ownership survives 10,000 surfaces coming and going`() {
        val controller = StatusBarController()
        val ms = measureTimeMillis {
            repeat(10_000) { i ->
                val id = controller.register(if (i % 2 == 0) MarigoldLight.bg else MarigoldLight.bar, settled = false)
                controller.update(id, MarigoldLight.bar, settled = true)
                controller.unregister(id)
            }
        }
        println("STRESS [status bar] 10,000 register/settle/unregister -> ${controller.size} left in $ms ms")
        assertThat(controller.size).isEqualTo(0)
        assertThat(controller.darkIcons(themeDark = false)).isTrue()
        val entries = List(10_000) { StatusBarEntry(it.toLong(), MarigoldLight.bg, settled = it % 3 == 0) }
        val resolveMs = measureTimeMillis { repeat(1_000) { StatusBarLogic.darkIcons(entries, themeDark = false) } }
        println("STRESS [status bar] 1,000 resolutions over 10,000 entries in $resolveMs ms")
    }

    @Test
    fun `scan routing for 10,000 groups and every transition pair`() {
        val many = List(10_000) { ScanChoice("g$it", "Group $it") }
        val ms = measureTimeMillis { repeat(1_000) { ScanTarget.resolve(many) } }
        assertThat(ScanTarget.resolve(many)).isInstanceOf(ScanTarget.Pick::class.java)
        val pairs = ShellRoute.entries.flatMap { a -> ShellRoute.entries.map { b -> ShellLogic.transition(a, b) } }
        println("STRESS [shell] 1,000 scan resolutions over 10,000 groups in $ms ms; ${pairs.size} transition pairs")
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppStartStressTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val main = MainDispatcherRule(dispatcher)

    @Test
    fun `1,000 launches decide their first screen, and 1,000 finishes write once each`() = runTest(dispatcher) {
        var onboarding = 0
        var ready = 0
        val ms = measureTimeMillis {
            repeat(1_000) { i ->
                val repo = InMemoryOnboardingRepository(completed = i % 2 == 0)
                val vm = AppStartViewModel(repo)
                runCurrent()
                if (vm.start.value == AppStart.Onboarding) onboarding++ else ready++
                repeat(10) { vm.finish() }
                runCurrent()
                check(repo.completed.first())
            }
        }
        println("STRESS [app start] 1,000 launches -> $onboarding intro, $ready app, all finished in $ms ms")
        assertThat(onboarding).isEqualTo(500)
        assertThat(ready).isEqualTo(500)
    }
}
