package com.darshan.splitsmart.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.domain.model.GroupSummary
import com.darshan.splitsmart.testutil.testGroup
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.designsystem.setDs
import com.darshan.splitsmart.ui.groups.GroupCard
import com.darshan.splitsmart.ui.groups.GroupsCallbacks
import com.darshan.splitsmart.ui.groups.GroupsScreen
import com.darshan.splitsmart.ui.groups.GroupsUiState
import com.darshan.splitsmart.ui.groups.HomeOverview
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Benchmark rule: on a phone the newest group's card is on the first screen with its name, even with the floating bar taking
 * the bottom (the scroll benchmark finds it by name without scrolling). Laid out exactly as the shell does.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class GroupsUnderBarTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `the newest group card is on the first screen above the bar, named`() {
        val cards = listOf(GroupCard(GroupSummary(testGroup("bt", "Benchmark trip"), 4, 200), listOf("Asha Rao", "Vikram Shah", "Neha Menon", "Rahul Verma"), outstandingPaise = 1_000_000))
        rule.setDs {
            // As in the shell: the list draws under the floating bar and keeps its end clear through LocalBottomBarInset.
            CompositionLocalProvider(LocalBottomBarInset provides BottomBarInset) {
                Box(Modifier.fillMaxSize()) {
                    GroupsScreen(
                        state = GroupsUiState(LoadState.Content(cards), overview = HomeOverview(null, outstandingPaise = 1_000_000, groupCount = 1)),
                        callbacks = GroupsCallbacks(),
                    )
                    ShellBottomBar(selectedIndex = 0, onSelect = {}, onReselect = {}, modifier = Modifier.align(Alignment.BottomCenter).testTag(ShellTags.BAR))
                }
            }
        }
        val card = rule.onNodeWithTag(SplitSmartTestTags.GROUP_ITEM)
        card.assertIsDisplayed()
        card.assertContentDescriptionContains("Benchmark trip", substring = true)
        val cardBottom = card.fetchSemanticsNode().boundsInRoot.bottom
        assertThat(cardBottom).isAtMost(rule.onNodeWithTag(ShellTags.BAR).fetchSemanticsNode().boundsInRoot.top)
        // The FAB floats above the bar, not under it.
        val fab = rule.onNodeWithTag(SplitSmartTestTags.FAB_ADD_GROUP).fetchSemanticsNode().boundsInRoot
        assertThat(fab.bottom).isAtMost(rule.onNodeWithTag(ShellTags.BAR).fetchSemanticsNode().boundsInRoot.top)
    }
}
