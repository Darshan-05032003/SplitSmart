package com.darshan.splitsmart.integration

import android.content.Intent
import android.os.Bundle
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.MainActivity
import com.darshan.splitsmart.domain.repository.GroupRepository
import com.darshan.splitsmart.testutil.runOffMain
import com.darshan.splitsmart.testutil.waitUntilWithMainLooper
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.designsystem.effects.ThemeMode
import com.darshan.splitsmart.ui.designsystem.effects.UiPreferences
import com.darshan.splitsmart.ui.me.MeTags
import com.darshan.splitsmart.ui.navigation.ShellTags
import com.darshan.splitsmart.ui.onboarding.OnboardingTags
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/** The real shell with the full Hilt graph: the bar, its three items, scan routing, reselect, the intro replay and launch hardening. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class ShellFlowTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()

    @Inject lateinit var groups: GroupRepository
    @Inject lateinit var uiPreferences: UiPreferences

    @Before
    fun setUp() {
        hilt.inject()
        // Sheets show a bobbing illustration; under full motion an auto-advancing test clock would never go idle.
        runBlocking { uiPreferences.setReduceMotion(true) }
    }

    private fun launch(intent: Intent? = null): ActivityScenario<MainActivity> =
        if (intent == null) ActivityScenario.launch(MainActivity::class.java) else ActivityScenario.launch(intent)

    private fun waitForTag(tag: String) = compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(tag) }

    /** The camera screen in whichever permission state Robolectric gives it. */
    private fun waitForScanScreen() = compose.waitUntilWithMainLooper {
        listOf(SplitSmartTestTags.GRANT_PERMISSION_BUTTON, SplitSmartTestTags.OPEN_SETTINGS_BUTTON, SplitSmartTestTags.CAPTURE_BUTTON)
            .any { compose.onAllNodesWithTag(it).fetchSemanticsNodes().isNotEmpty() }
    }

    // Rows scrolled just into view sit under the floating bar on Robolectric's 470dp screen, where a touch would hit the bar:
    // those are clicked through their accessibility action, as TalkBack would.

    private fun tapBar(label: String) = compose.onNodeWithContentDescription(label).performClick()

    @Test
    fun `the groups list carries the bar with Groups, Scan and Me, and a group screen gets the whole screen`() {
        runOffMain { groups.createGroup("Goa trip") }
        launch().use {
            waitForTag(SplitSmartTestTags.HOME_BALANCE)
            compose.onNodeWithTag(ShellTags.BAR).assertIsDisplayed()
            for (label in listOf("Groups", "Scan", "Me")) compose.onNodeWithContentDescription(label).assertExists()
            compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag(SplitSmartTestTags.GROUP_ITEM))
            compose.onNodeWithTag(SplitSmartTestTags.GROUP_ITEM).performClick()
            waitForTag(SplitSmartTestTags.ADD_MEMBER_BUTTON)
            compose.onNodeWithText("Goa trip").assertExists()
            compose.waitUntilWithMainLooper { compose.onAllNodesWithTag(ShellTags.BAR).fetchSemanticsNodes().isEmpty() }
        }
    }

    @Test
    fun `Me is a tab, back from it returns to Groups, and its theme switch drives the status bar icons`() {
        launch().use { scenario ->
            waitForTag(SplitSmartTestTags.EMPTY)
            tapBar("Me")
            waitForTag(MeTags.ROOT)
            compose.onNodeWithText("How it feels").assertExists()
            compose.onNodeWithTag(MeTags.theme(ThemeMode.Dark)).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
            compose.waitUntilWithMainLooper { !lightStatusBarIcons(scenario) }
            compose.onNodeWithTag(MeTags.theme(ThemeMode.Light)).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
            compose.waitUntilWithMainLooper { lightStatusBarIcons(scenario) }
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            waitForTag(SplitSmartTestTags.EMPTY)
        }
    }

    @Test
    fun `scan with no groups explains why and opens New group on the groups list`() {
        launch().use {
            waitForTag(SplitSmartTestTags.EMPTY)
            tapBar("Me")
            waitForTag(MeTags.ROOT)
            tapBar("Scan")
            waitForTag(ShellTags.SCAN_NEEDS_GROUP)
            compose.onNodeWithTag(ShellTags.SCAN_CREATE_GROUP).performClick()
            waitForTag(SplitSmartTestTags.GROUP_NAME_FIELD)
        }
    }

    @Test
    fun `scan with one group opens the camera for it, with several it asks which`() {
        val goa = runOffMain { groups.createGroup("Goa trip") }
        launch().use {
            waitForTag(SplitSmartTestTags.HOME_BALANCE)
            tapBar("Scan")
            waitForScanScreen()
            compose.onNodeWithTag(ShellTags.BAR).assertDoesNotExist()
        }
        val flat = runOffMain { groups.createGroup("Flat 302") }
        launch().use {
            waitForTag(SplitSmartTestTags.HOME_BALANCE)
            tapBar("Scan")
            waitForTag(ShellTags.SCAN_PICKER)
            compose.onNodeWithTag(ShellTags.scanChoice(goa.id)).assertExists()
            compose.onNodeWithTag(ShellTags.scanChoice(flat.id)).performClick()
            waitForScanScreen()
            compose.onNodeWithTag(ShellTags.BAR).assertDoesNotExist()
        }
    }

    @Test
    fun `tapping Groups again scrolls the list back to the top`() {
        runOffMain { repeat(30) { groups.createGroup("Group $it") } }
        launch().use {
            waitForTag(SplitSmartTestTags.HOME_BALANCE)
            compose.onNode(hasScrollAction()).performScrollToIndex(31)
            compose.waitUntilWithMainLooper { compose.onAllNodesWithTag(SplitSmartTestTags.HOME_BALANCE).fetchSemanticsNodes().isEmpty() }
            tapBar("Groups")
            waitForTag(SplitSmartTestTags.HOME_BALANCE)
        }
    }

    @Test
    fun `the intro can be replayed from Me, and skipping it returns to Me`() {
        launch().use {
            waitForTag(SplitSmartTestTags.EMPTY)
            tapBar("Me")
            waitForTag(MeTags.INTRO)
            compose.onNodeWithTag(MeTags.INTRO).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
            waitForTag(OnboardingTags.SKIP)
            compose.onNodeWithTag(OnboardingTags.SKIP).performClick()
            waitForTag(MeTags.ROOT)
        }
    }

    @Test
    fun `back on the first page of a replayed intro closes it and leaves the app where it was`() {
        launch().use { scenario ->
            waitForTag(SplitSmartTestTags.EMPTY)
            tapBar("Me")
            waitForTag(MeTags.INTRO)
            compose.onNodeWithTag(MeTags.INTRO).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
            waitForTag(OnboardingTags.SKIP)
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitUntilWithMainLooper { compose.onAllNodesWithTag(OnboardingTags.PAGER).fetchSemanticsNodes().isEmpty() }
            waitForTag(MeTags.ROOT)
        }
    }

    @Test
    fun `a crafted launch intent can neither open a screen out of order nor crash the app`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Navigation's own explicit deep-link extras, naming the review screen (type-safe route ids are the route's hash) with no arguments.
        val reviewId = "com.darshan.splitsmart.ui.navigation.ReviewDestination/{draftId}".hashCode()
        val groupsId = "com.darshan.splitsmart.ui.navigation.GroupsDestination".hashCode()
        val hostile = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("android-support-nav:controller:deepLinkIds", intArrayOf(groupsId, reviewId))
            putParcelableArrayListExtra("android-support-nav:controller:deepLinkArgs", arrayListOf(Bundle(), Bundle()))
        }
        // ActivityScenario waits for an activity matching the intent it launched, and the app rewrites that intent on purpose.
        val controller = Robolectric.buildActivity(MainActivity::class.java, hostile).setup()
        try {
            waitForTag(SplitSmartTestTags.EMPTY)
            compose.onNodeWithTag(SplitSmartTestTags.REVIEW_SAVE).assertDoesNotExist()
            val intent = controller.get().intent
            assertThat(intent.extras).isNull()
            assertThat(intent.action).isEqualTo(Intent.ACTION_MAIN)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun lightStatusBarIcons(scenario: ActivityScenario<MainActivity>): Boolean {
        var light = false
        scenario.onActivity { light = WindowCompat.getInsetsController(it.window, it.window.decorView).isAppearanceLightStatusBars }
        return light
    }
}
