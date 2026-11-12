package com.darshan.splitsmart.integration

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.MainActivity
import com.darshan.splitsmart.testutil.waitUntilWithMainLooper
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.groupdetail.GroupDetailTags
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import com.darshan.splitsmart.ui.designsystem.effects.UiPreferences
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Full Hilt graph + navigation: launcher activity → create group → group detail → back. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
// Phone size: since row 10 rebuilt the groups list, the New group sheet and the group screen, a tall Robolectric screen no longer
// loops in layout (the old Material dialog and group screen did, until the heap ran out).
@Config(sdk = [35], application = HiltTestApplication::class, qualifiers = "w411dp-h891dp-xhdpi")
class MainActivityTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var uiPreferences: UiPreferences

    @Before
    fun setUp() {
        hilt.inject()
        // App-level tests run with reduced motion: the empty page's illustration bobs forever under full motion.
        runBlocking { uiPreferences.setReduceMotion(true) }
    }

    @Test
    fun `launches, creates a group and navigates into it`() {
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.EMPTY) }
        compose.onNodeWithText("Create your first group").performClick()
        compose.onNodeWithTag(SplitSmartTestTags.GROUP_NAME_FIELD).performTextInput("Flatmates")
        compose.onNodeWithTag(SplitSmartTestTags.DIALOG_CONFIRM).performClick()
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.HOME_BALANCE) }
        // On a phone the new card is on the first screen, above the floating bar: tapped where it is, no scrolling.
        compose.onNodeWithTag(SplitSmartTestTags.GROUP_ITEM).assertIsDisplayed().performClick()
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.ADD_MEMBER_BUTTON) }
        compose.onNodeWithText("Flatmates").assertExists()
    }

    @Test
    fun `deleting a group lands on the groups list with Undo, and Undo brings it back`() {
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.EMPTY) }
        compose.onNodeWithText("Create your first group").performClick()
        compose.onNodeWithTag(SplitSmartTestTags.GROUP_NAME_FIELD).performTextInput("Flatmates")
        compose.onNodeWithTag(SplitSmartTestTags.DIALOG_CONFIRM).performClick()
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.GROUP_ITEM) }
        compose.onNodeWithTag(SplitSmartTestTags.GROUP_ITEM).performClick()
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(GroupDetailTags.OPTIONS) }
        compose.onNodeWithTag(GroupDetailTags.OPTIONS).performClick()
        compose.onNodeWithTag(SplitSmartTestTags.DELETE_GROUP_BUTTON).performClick()
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(GroupDetailTags.DELETE_CONFIRM) }
        compose.waitForIdle()
        // The undo window runs on the frame clock and an auto-advancing clock would idle straight through it, so from the
        // confirmation on the clock is stepped by hand (a frame at a time) while the delete runs on its own thread.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(GroupDetailTags.DELETE_CONFIRM).performClick()
        stepUntil { compose.onAllNodesWithText("Flatmates deleted").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(SplitSmartTestTags.EMPTY).assertExists()
        compose.onNodeWithText("Undo").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.mainClock.autoAdvance = true
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.GROUP_ITEM) }
        compose.onNodeWithText("Flatmates", useUnmergedTree = true).assertExists()
    }

    /** Steps the paused clock a frame at a time, letting background work land, until [condition] holds (or 5 s of frames). */
    private fun stepUntil(condition: () -> Boolean) {
        repeat(300) {
            if (condition()) return
            compose.mainClock.advanceTimeByFrame()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        error("condition not met")
    }
}
