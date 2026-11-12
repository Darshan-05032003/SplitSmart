package com.darshan.splitsmart.stress

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.domain.model.BillSource
import com.darshan.splitsmart.domain.model.BillStatus
import com.darshan.splitsmart.domain.model.GroupSummary
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testGroup
import com.darshan.splitsmart.testutil.testMember
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.darshan.splitsmart.ui.designsystem.recompositionCycles
import com.darshan.splitsmart.ui.designsystem.setDs
import com.darshan.splitsmart.ui.groupdetail.GroupDetail
import com.darshan.splitsmart.ui.groupdetail.GroupDetailCallbacks
import com.darshan.splitsmart.ui.groupdetail.GroupDetailScreen
import com.darshan.splitsmart.ui.groupdetail.GroupDetailTags
import com.darshan.splitsmart.ui.groupdetail.GroupDetailUiState
import com.darshan.splitsmart.ui.groups.GroupCard
import com.darshan.splitsmart.ui.groups.GroupsCallbacks
import com.darshan.splitsmart.ui.groups.GroupsScreen
import com.darshan.splitsmart.ui.groups.GroupsTags
import com.darshan.splitsmart.ui.groups.GroupsUiState
import com.darshan.splitsmart.ui.groups.HomeOverview
import com.darshan.splitsmart.ui.navigation.LocalNavAnimatedScope
import com.darshan.splitsmart.ui.navigation.LocalSharedTransitionScope
import com.google.common.truth.Truth.assertThat
import java.lang.management.ManagementFactory
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What a fling costs on the main thread, measured on the JVM: recomposition cycles and bytes allocated per fling over the group
 * screen's bills (the list the emulator showed getting heavier) and over the groups home, inside the shell's shared-element
 * layout (where a bill row or a group card can be one end of a container transform) and on its own. Device numbers (gfxinfo,
 * emulator) are in LOAD_TEST_REPORT; these are the JVM half, comparable only with each other.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ListFlingCostStressTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun billsFling() {
        val members = List(6) { testMember("m$it", "trip", NAMES[it]) }
        val bills = List(BILLS) {
            testBill(
                id = "b$it", groupId = "trip", title = "Bill $it", totalPaise = 10_000L + it, paidByMemberId = "m${it % 6}",
                createdAt = Instant.EPOCH.plusSeconds(BILLS - it.toLong()),
            ).copy(status = BillStatus.entries[it % 3], source = BillSource.entries[it % BillSource.entries.size])
        }
        val state = GroupDetailUiState(LoadState.Content(GroupDetail(testGroup("trip", "Benchmark trip"), members, bills)))
        measure("bills", GroupDetailTags.LIST) { GroupDetailScreen(state, SsSnackbarState(), NOOP_DETAIL) }
    }

    @Test
    fun groupsFling() {
        val cards = List(GROUPS) { i ->
            val group = testGroup("g$i", "Group $i", createdAt = Instant.EPOCH.plusSeconds(GROUPS - i.toLong()))
            GroupCard(GroupSummary(group, memberCount = 4, billCount = 3), NAMES.take(2 + i % 4), group.createdAt, outstandingPaise = 54_321L, yourBalancePaise = (i % 5 - 2) * 12_345L)
        }
        val state = GroupsUiState(groups = LoadState.Content(cards), overview = HomeOverview())
        measure("groups", GroupsTags.LIST) { GroupsScreen(state, GroupsCallbacks()) }
    }

    /**
     * One composition switches between the three set-ups (on its own, inside the shell with no row taking part, inside the shell
     * as in the app), twice: the first round warms the JVM for every set-up, the second is reported. Times from separate test
     * methods were dominated by which one ran first.
     */
    private fun measure(label: String, listTag: String, screen: @Composable () -> Unit) {
        var variant by mutableStateOf(Variant.Alone)
        rule.setDs {
            key(variant) { Shell(variant) { screen() } }
        }
        val results = mutableMapOf<Variant, String>()
        var shellCycles = 0L
        repeat(2) { round ->
            for (v in Variant.entries) {
                variant = v
                rule.waitForIdle()
                val list = rule.onNodeWithTag(listTag)
                repeat(WARM_UP) { list.performTouchInput { swipeUp() }; rule.waitForIdle() }
                val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
                val thread = Thread.currentThread().id
                val bytesBefore = threads.getThreadAllocatedBytes(thread)
                val cyclesBefore = recompositionCycles()
                val start = System.nanoTime()
                repeat(FLINGS) { list.performTouchInput { swipeUp() }; rule.waitForIdle() }
                val ms = (System.nanoTime() - start) / 1_000_000
                val kbPerFling = (threads.getThreadAllocatedBytes(thread) - bytesBefore) / 1024 / FLINGS
                val cycles = recompositionCycles() - cyclesBefore
                if (round == 1) {
                    results[v] = "flings=$FLINGS total=${ms}ms perFling=${ms / FLINGS}ms allocPerFling=${kbPerFling}KB recompositionCycles=$cycles"
                    if (v == Variant.Shell) shellCycles = cycles
                }
            }
        }
        results.forEach { (v, line) -> println("STRESS [$label-fling-cost-${v.label}] $line") }
        // Before row 17 every row was a shared element: 998 (bills) and 1,042 (groups) cycles over these 60 flings inside the
        // shell. Now only the row under a still finger is, and a finger that travels gives it back.
        assertThat(shellCycles).isAtMost(FLINGS * 2L)
    }

    private enum class Variant(val label: String) { Alone("alone"), ShellLayoutOnly("shell-layout-only"), Shell("shell") }

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    private fun Shell(variant: Variant, content: @Composable () -> Unit) {
        if (variant == Variant.Alone) {
            content()
            return
        }
        SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                AnimatedContent(targetState = SCREEN, label = "screen") { screen ->
                    CompositionLocalProvider(LocalNavAnimatedScope provides if (variant == Variant.Shell) this else null) { if (screen == SCREEN) content() }
                }
            }
        }
    }

    private companion object {
        const val WARM_UP = 10
        const val FLINGS = 60
        const val BILLS = 2_000
        const val GROUPS = 2_000
        const val SCREEN = "list"
        val NAMES = listOf("Asha", "Ravi", "Meera", "Darshan", "Kiran", "Neel")
        val NOOP_DETAIL = GroupDetailCallbacks(
            onBack = {}, onRetry = {}, onOpenBill = {}, onScan = {}, onSettleUp = {}, onDeleteGroup = {}, onAddMember = {}, onDismissMember = {},
            onMemberName = {}, onMemberVpa = {}, onSubmitMember = {}, onRemoveMember = {}, onAddBill = {}, onDismissBill = {}, onBillTitle = {},
            onBillAmount = {}, onBillPayer = {}, onSubmitBill = {},
        )
    }
}
