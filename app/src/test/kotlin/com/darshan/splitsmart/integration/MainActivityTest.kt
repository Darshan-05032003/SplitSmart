package com.darshan.splitsmart.integration

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.MainActivity
import com.darshan.splitsmart.testutil.waitUntilWithMainLooper
import com.darshan.splitsmart.ui.SplitSmartTestTags
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Full Hilt graph + navigation: launcher activity → create group → group detail → back. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = HiltTestApplication::class)
class MainActivityTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() = hilt.inject()

    @Test
    fun `launches, creates a group and navigates into it`() {
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.EMPTY) }
        compose.onNodeWithTag(SplitSmartTestTags.FAB_ADD_GROUP).performClick()
        compose.onNodeWithTag(SplitSmartTestTags.GROUP_NAME_FIELD).performTextInput("Flatmates")
        compose.onNodeWithTag(SplitSmartTestTags.DIALOG_CONFIRM).performClick()
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.HOME_BALANCE) }
        // Robolectric's default 320×470dp screen: under the floating bar the card sits just below the fold (on a phone it is on
        // the first screen, see GroupsUnderBarTest).
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag(SplitSmartTestTags.GROUP_ITEM))
        compose.onNodeWithTag(SplitSmartTestTags.GROUP_ITEM).performClick()
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.ADD_MEMBER_BUTTON) }
        compose.onNodeWithText("Flatmates").assertExists()
    }
}
