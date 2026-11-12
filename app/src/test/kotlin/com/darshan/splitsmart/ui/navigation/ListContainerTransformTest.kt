package com.darshan.splitsmart.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.domain.model.GroupSummary
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testGroup
import com.darshan.splitsmart.testutil.testMember
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.designsystem.commitState
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.darshan.splitsmart.ui.designsystem.setDs
import com.darshan.splitsmart.ui.groupdetail.GroupDetail
import com.darshan.splitsmart.ui.groupdetail.GroupDetailCallbacks
import com.darshan.splitsmart.ui.groupdetail.GroupDetailScreen
import com.darshan.splitsmart.ui.groupdetail.GroupDetailTags
import com.darshan.splitsmart.ui.groupdetail.GroupDetailUiState
import com.darshan.splitsmart.ui.groups.GroupCard
import com.darshan.splitsmart.ui.groups.GroupsCallbacks
import com.darshan.splitsmart.ui.groups.GroupsScreen
import com.darshan.splitsmart.ui.groups.GroupsUiState
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A bill row grows into its bill screen and the screen shrinks back into it; a group card does the same with its group screen.
 * A shell-shaped harness (shared-element layout around an animated screen switch, saved state per screen like NavHost) checks
 * that both ways find a matching pair, which is what makes it a container transform rather than a cross-fade. Since row 17 only
 * the row under the finger takes part; these fail if rows lose the transform altogether.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ListContainerTransformTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun aBillRowGrowsIntoItsBillAndTheBillShrinksBackIntoIt() {
        val members = listOf(testMember("m1", "trip", "Asha"), testMember("m2", "trip", "Ravi"))
        val bills = List(30) { testBill(id = "b$it", groupId = "trip", title = "Bill $it", createdAt = Instant.EPOCH.plusSeconds(100L - it)) }
        val detail = GroupDetail(testGroup("trip", "Goa trip"), members, bills)
        roundTrip(
            key = ::billContainerKey,
            prepare = { rule.onNodeWithTag(GroupDetailTags.LIST).performScrollToKey("bb5") },
            row = { rule.onAllNodesWithTag(SplitSmartTestTags.BILL_ITEM).onFirst() },
        ) { open ->
            GroupDetailScreen(GroupDetailUiState(LoadState.Content(detail)), SsSnackbarState(), noopDetail(open))
        }
    }

    @Test
    fun aGroupCardGrowsIntoItsGroupAndTheGroupShrinksBackIntoIt() {
        val cards = List(12) { GroupCard(GroupSummary(testGroup("g$it", "Group $it"), memberCount = 2, billCount = 1), listOf("Asha", "Ravi")) }
        roundTrip(key = ::groupContainerKey, row = { rule.onAllNodesWithTag(SplitSmartTestTags.GROUP_ITEM).onFirst() }) { open ->
            GroupsScreen(GroupsUiState(groups = LoadState.Content(cards)), GroupsCallbacks(onOpenGroup = open))
        }
    }

    @Test
    fun onlyTheRowUnderTheFingerIsAnEndOfTheTransform() {
        val anchor = TransformAnchor()
        assertThat(anchor.key).isNull()
        // The last row opened survives the trip to the other screen (and process death), so the way back finds its pair.
        val saved = with(TransformAnchor.Saver) { SaverScope { true }.save(TransformAnchor("bill:b2")) }
        assertThat(TransformAnchor.Saver.restore(saved!!)?.key).isEqualTo("bill:b2")
    }

    private fun roundTrip(
        key: (String) -> String,
        row: () -> SemanticsNodeInteraction,
        prepare: () -> Unit = {},
        list: @Composable (open: (String) -> Unit) -> Unit,
    ) {
        var opened by mutableStateOf<String?>(null)
        lateinit var shared: SharedTransitionScope
        rule.setDs {
            SharedTransitionLayout {
                shared = this
                CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                    val saved = rememberSaveableStateHolder()
                    AnimatedContent(opened, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { target ->
                        CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                            saved.SaveableStateProvider(target ?: "list") {
                                if (target == null) list { opened = it } else Box(Modifier.size(300.dp).containerTransform(key(target)))
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        prepare()
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false

        // A real tap: the finger rests on the row for a few frames before it lifts.
        val node = row()
        node.performTouchInput { down(center) }
        repeat(FRAMES_PRESSED) { rule.mainClock.advanceTimeByFrame() }
        node.performTouchInput { up() }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        assertThat(opened).isNotNull()
        assertThat(shared.isTransitionActive).isTrue()
        rule.mainClock.advanceTimeBy(SETTLE_MS)
        assertThat(shared.isTransitionActive).isFalse()

        commitState { opened = null }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        assertThat(shared.isTransitionActive).isTrue()
        rule.mainClock.advanceTimeBy(SETTLE_MS)
        assertThat(shared.isTransitionActive).isFalse()
    }

    private fun noopDetail(open: (String) -> Unit) = GroupDetailCallbacks(
        onBack = {}, onRetry = {}, onOpenBill = open, onScan = {}, onSettleUp = {}, onDeleteGroup = {}, onAddMember = {}, onDismissMember = {},
        onMemberName = {}, onMemberVpa = {}, onSubmitMember = {}, onRemoveMember = {}, onAddBill = {}, onDismissBill = {}, onBillTitle = {},
        onBillAmount = {}, onBillPayer = {}, onSubmitBill = {},
    )

    private companion object {
        const val SETTLE_MS = 2_000L
        const val FRAMES_PRESSED = 4
    }
}
