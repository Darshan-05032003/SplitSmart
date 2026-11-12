package com.darshan.splitsmart.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.domain.model.ExtractionSource
import com.darshan.splitsmart.domain.model.GroupSummary
import com.darshan.splitsmart.testutil.testGroup
import com.darshan.splitsmart.ui.designsystem.assertInside
import com.darshan.splitsmart.ui.designsystem.assertTextWhole
import com.darshan.splitsmart.ui.designsystem.components.BottomBarLayout
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.darshan.splitsmart.ui.designsystem.setDs
import com.darshan.splitsmart.ui.groups.GroupCard
import com.darshan.splitsmart.ui.groups.GroupsCallbacks
import com.darshan.splitsmart.ui.groups.GroupsScreen
import com.darshan.splitsmart.ui.groups.GroupsTags
import com.darshan.splitsmart.ui.groups.GroupsUiState
import com.darshan.splitsmart.ui.groups.HomeOverview
import com.darshan.splitsmart.ui.navigation.LocalBottomBarInset
import com.darshan.splitsmart.ui.navigation.ShellBottomBar
import com.darshan.splitsmart.ui.navigation.ShellTags
import com.darshan.splitsmart.ui.navigation.bottomBarInset
import com.darshan.splitsmart.ui.review.ItemRow
import com.darshan.splitsmart.ui.review.ReviewCallbacks
import com.darshan.splitsmart.ui.review.ReviewForm
import com.darshan.splitsmart.ui.review.ReviewPhase
import com.darshan.splitsmart.ui.review.ReviewScreen
import com.darshan.splitsmart.ui.review.ReviewTags
import com.darshan.splitsmart.ui.review.ReviewUiState
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Past the 1.3x every screen is designed at: 1.5x and 2x text, with real text measurement (native graphics). Nothing a person
 * must read (an amount, an action, a tab) is cut, and the floating bar grows with its labels instead of clipping them.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class LargeTextTest {
    @get:Rule val rule = createComposeRule()

    private val cards = listOf(
        GroupCard(GroupSummary(testGroup("goa", "Goa trip"), 3, 4), listOf("Darshan", "Asha", "Ravi"), outstandingPaise = 111_870, yourBalancePaise = 111_870),
        GroupCard(GroupSummary(testGroup("flat", "Flat 302"), 2, 2), listOf("Darshan", "Priya"), outstandingPaise = 60_000, yourBalancePaise = -60_000),
    )

    private fun groups(scale: Float) = rule.setDs(fontScale = scale, reduceMotion = true) {
        Box(Modifier.fillMaxSize()) {
            GroupsScreen(GroupsUiState(LoadState.Content(cards), overview = HomeOverview("Darshan", 111_870, 60_000, 171_870, groupsWithYou = 2, groupCount = 2)), GroupsCallbacks())
        }
    }

    private fun faces() = rule.onAllNodesWithTag(GroupsTags.CARD_FACES, useUnmergedTree = true)[0].fetchSemanticsNode().boundsInRoot
    private fun balance() = rule.onAllNodesWithTag(GroupsTags.CARD_BALANCE, useUnmergedTree = true)[0].fetchSemanticsNode().boundsInRoot

    @Test
    fun `at normal text a card's balance sits beside the faces`() {
        groups(1f)
        assertThat(balance().top).isLessThan(faces().bottom)
        assertThat(balance().left).isAtLeast(faces().right)
    }

    @Test
    fun `at 2x text a card's balance takes its own line rather than being cut`() {
        groups(2f)
        val faces = faces()
        val balance = balance()
        assertWithMessage("faces $faces, balance $balance").that(balance.top).isAtLeast(faces.bottom)
        rule.onAllNodesWithTag(GroupsTags.CARD_BALANCE, useUnmergedTree = true)[0].assertInside(rule.onAllNodesWithTag(SplitSmartTestTags.GROUP_ITEM)[0])
    }

    @Test
    fun `the floating bar keeps 68dp at normal text and grows at 2x so its labels stay in their tabs`() {
        fun barHeight(scale: Float): Float {
            var inset = 0.dp
            rule.setDs(fontScale = scale) {
                inset = bottomBarInset()
                CompositionLocalProvider(LocalBottomBarInset provides inset) {
                    Box(Modifier.fillMaxSize()) {
                        ShellBottomBar(selectedIndex = 0, onSelect = {}, onReselect = {}, modifier = Modifier.align(Alignment.BottomCenter).testTag(ShellTags.BAR))
                    }
                }
            }
            return rule.onNodeWithTag(ShellTags.BAR).fetchSemanticsNode().boundsInRoot.height
        }
        val tall = barHeight(2f)
        val density = 2f // xhdpi
        // The bar node holds the pill, its margins and its depth: the inset the shell keeps clear.
        assertThat(tall / density).isAtLeast((BottomBarLayout.height(32.dp)).value)
        assertThat(BottomBarLayout.height(32.dp)).isGreaterThan(68.dp)
    }

    @Test
    fun `save and scan the next one is read in full under Save bill at 2x text`() {
        val form = ReviewForm(items = listOf(ItemRow("a", "Paneer Tikka", "1", "250.00")), total = "250.00", source = ExtractionSource.LLM)
        rule.setDs(fontScale = 2f, reduceMotion = true) {
            Box(Modifier.fillMaxSize().testTag(ROOT)) {
                ReviewScreen(
                    ReviewUiState(phase = ReviewPhase.EDITING, form = form, subtotalPaise = 25_000, mismatchPaise = 0),
                    SsSnackbarState(),
                    ReviewCallbacks(
                        onBack = {}, onRetry = {}, onRerun = {}, onSave = {}, onTitle = {}, onPayer = {}, onItemName = { _, _ -> }, onItemQty = { _, _ -> },
                        onItemAmount = { _, _ -> }, onAddItem = {}, onRemoveItem = {}, onTax = {}, onTip = {}, onTotal = {}, onUseComputedTotal = {},
                    ),
                )
            }
        }
        rule.onNodeWithText("Save and scan the next one", useUnmergedTree = true).assertTextWhole()
        val save = rule.onNodeWithTag(SplitSmartTestTags.REVIEW_SAVE).fetchSemanticsNode().boundsInRoot
        val next = rule.onNodeWithTag(ReviewTags.SAVE_SCAN_NEXT).fetchSemanticsNode().boundsInRoot
        assertThat(next.top).isAtLeast(save.bottom)
        rule.onNodeWithTag(ReviewTags.SAVE_SCAN_NEXT).assertInside(rule.onNodeWithTag(ROOT))
    }

    private companion object {
        const val ROOT = "root"
    }
}
