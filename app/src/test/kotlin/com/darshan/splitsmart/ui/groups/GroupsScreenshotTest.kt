package com.darshan.splitsmart.ui.groups

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.domain.model.GroupSummary
import com.darshan.splitsmart.domain.model.NameError
import com.darshan.splitsmart.testutil.testGroup
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.designsystem.captureNode
import com.darshan.splitsmart.ui.designsystem.commitState
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.darshan.splitsmart.ui.designsystem.setDs
import com.darshan.splitsmart.ui.navigation.BottomBarInset
import com.darshan.splitsmart.ui.navigation.LocalBottomBarInset
import com.darshan.splitsmart.ui.navigation.ShellBottomBar
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The groups home as people see it: under the bar, in every state, mid-motion, light and dark, and at 1.3× text. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class GroupsScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val goa = testGroup("goa", "Goa trip")
    private val cards = listOf(
        GroupCard(GroupSummary(goa, 3, 4), listOf("Darshan", "Asha", "Ravi"), outstandingPaise = 111_870, yourBalancePaise = 111_870),
        GroupCard(GroupSummary(testGroup("flat", "Flat 302"), 2, 2), listOf("Darshan", "Priya"), outstandingPaise = 60_000, yourBalancePaise = -60_000),
        GroupCard(GroupSummary(testGroup("office", "Office lunches"), 6, 12), listOf("Darshan", "Meera", "Kabir", "Neha", "Rahul", "Zoya"), outstandingPaise = 42_500, yourBalancePaise = 0),
    )
    private val overview = HomeOverview("Darshan", 111_870, 60_000, 214_370, groupsWithYou = 3, groupCount = 3)
    private val content = GroupsUiState(LoadState.Content(cards), overview = overview)

    private fun shot(name: String, dark: Boolean = false, fontScale: Float = 1f, reduceMotion: Boolean = false, settle: Long = 2_000, act: () -> Unit = {}, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setDs(dark = dark, fontScale = fontScale, reduceMotion = reduceMotion) {
            CompositionLocalProvider(LocalBottomBarInset provides BottomBarInset) {
                Box(Modifier.fillMaxSize().testTag(ROOT)) {
                    content()
                    ShellBottomBar(selectedIndex = 0, onSelect = {}, onReselect = {}, modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        rule.mainClock.advanceTimeBy(settle)
        act()
        rule.onNodeWithTag(ROOT).captureNode("groups_${name}_${if (dark) "dark" else "light"}${if (fontScale != 1f) "_font130" else ""}${if (reduceMotion) "_reduced" else ""}")
    }

    @Test fun home_light() = shot("home") { GroupsScreen(content, GroupsCallbacks()) }
    @Test fun home_dark() = shot("home", dark = true) { GroupsScreen(content, GroupsCallbacks()) }
    @Test fun home_font130() = shot("home", fontScale = 1.3f) { GroupsScreen(content, GroupsCallbacks()) }

    @Test
    fun no_name_light() = shot("no_name") {
        GroupsScreen(GroupsUiState(LoadState.Content(cards.map { it.copy(yourBalancePaise = null) }), overview = HomeOverview(null, outstandingPaise = 214_370, groupCount = 3)), GroupsCallbacks())
    }

    @Test fun empty_light() = shot("empty") { GroupsScreen(GroupsUiState(LoadState.Empty), GroupsCallbacks()) }
    @Test fun empty_dark() = shot("empty", dark = true) { GroupsScreen(GroupsUiState(LoadState.Empty), GroupsCallbacks()) }
    @Test fun loading_light() = shot("loading", settle = 400) { GroupsScreen(GroupsUiState(LoadState.Loading), GroupsCallbacks()) }

    @Test
    fun fab_menu_light() = shot("fab_menu", act = { openMenu(1_000) }) { GroupsScreen(content, GroupsCallbacks()) }

    @Test
    fun fab_menu_dark() = shot("fab_menu", dark = true, act = { openMenu(1_000) }) { GroupsScreen(content, GroupsCallbacks()) }

    /** Mid-burst: the actions nearest the button lead, the cross is half turned. */
    @Test
    fun fab_menu_opening_light() = shot("fab_menu_opening", act = { openMenu(96) }) { GroupsScreen(content, GroupsCallbacks()) }

    @Test
    fun swipe_archive_light() = shot(
        "swipe_archive",
        act = {
            rule.onNode(hasTestTag(SplitSmartTestTags.GROUP_ITEM) and hasContentDescription("Flat 302", substring = true)).performTouchInput {
                down(center)
                moveBy(androidx.compose.ui.geometry.Offset(-width * 0.25f, 0f))
                moveBy(androidx.compose.ui.geometry.Offset(-width * 0.2f, 0f))
            }
            rule.mainClock.advanceTimeBy(200)
        },
    ) { GroupsScreen(content, GroupsCallbacks()) }

    @Test
    fun archived_light() = shot("archived", act = {
        // Key-based, not performScrollToNode: on a paused clock that keeps asking for scrolls that never land, until the heap is gone.
        rule.onNode(androidx.compose.ui.test.hasScrollToKeyAction()).performScrollToKey("archived_toggle")
        rule.onNodeWithTag(GroupsTags.ARCHIVED_TOGGLE).performClick()
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNode(androidx.compose.ui.test.hasScrollToKeyAction()).performScrollToKey("archived:old")
        rule.mainClock.advanceTimeBy(1_000)
    }) {
        val old = GroupCard(GroupSummary(testGroup("old", "Manali 2024").copy(archivedAt = java.time.Instant.EPOCH), 4, 9), outstandingPaise = 0, yourBalancePaise = 0)
        GroupsScreen(content.copy(archived = listOf(old)), GroupsCallbacks())
    }

    @Test
    fun create_sheet_light() = shot("create_sheet") {
        GroupsScreen(content.copy(draft = GroupDraft("", NameError.BLANK)), GroupsCallbacks())
    }

    @Test
    fun undo_snackbar_dark() = shot("undo_snackbar", dark = true) {
        val snackbar = remember { SsSnackbarState() }
        LaunchedEffect(Unit) { snackbar.show("Goa trip archived", emoji = "📦", actionLabel = "Undo") }
        GroupsScreen(content.copy(groups = LoadState.Content(cards.drop(1)), archived = cards.take(1)), GroupsCallbacks(), snackbar)
    }

    /** The hero halfway from "you're owed" (mint) to "you owe" (chilli): digits rolling, colour mixing. */
    @Test
    fun hero_morph_light() {
        var state by mutableStateOf(content)
        shot("hero_morph", act = {
            commitState { state = content.copy(overview = overview.copy(youAreOwedPaise = 0)) }
            repeat(9) { rule.mainClock.advanceTimeByFrame() }
        }) { GroupsScreen(state, GroupsCallbacks()) }
    }

    private fun openMenu(afterMs: Long) {
        rule.onNodeWithTag(SplitSmartTestTags.FAB_ADD_GROUP).performClick()
        rule.mainClock.advanceTimeBy(afterMs)
    }

    private companion object {
        const val ROOT = "root"
    }
}
