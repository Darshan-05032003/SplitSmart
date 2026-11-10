package com.darshan.splitsmart.ui.groups

import com.darshan.splitsmart.domain.model.GroupSummary
import com.darshan.splitsmart.testutil.testGroup
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GroupsHomeLogicTest {
    // ----- hero -----
    @Test
    fun `the hero shows your net balance across groups and colours it by direction`() {
        val owed = GroupsHomeLogic.hero(HomeOverview("Darshan", youAreOwedPaise = 111_870, youOwePaise = 60_000, groupsWithYou = 2, groupCount = 2))
        assertThat(owed.mood).isEqualTo(HeroMood.Owed)
        assertThat(owed.paise).isEqualTo(51_870)
        assertThat(owed.showBreakdown).isTrue()
        assertThat(owed.askForName).isFalse()

        val owe = GroupsHomeLogic.hero(HomeOverview("Darshan", youAreOwedPaise = 0, youOwePaise = 60_000, groupsWithYou = 1, groupCount = 1))
        assertThat(owe.mood).isEqualTo(HeroMood.Owe)
        assertThat(owe.paise).isEqualTo(60_000)
        assertThat(owe.showBreakdown).isFalse()
    }

    @Test
    fun `equal amounts each way, or nothing at all, is all square`() {
        val even = GroupsHomeLogic.hero(HomeOverview("Darshan", youAreOwedPaise = 5_000, youOwePaise = 5_000, groupsWithYou = 2, groupCount = 2))
        assertThat(even.mood).isEqualTo(HeroMood.Square)
        assertThat(even.paise).isEqualTo(0)
        assertThat(even.showBreakdown).isTrue()
        val clear = GroupsHomeLogic.hero(HomeOverview("Darshan", groupsWithYou = 1, groupCount = 1))
        assertThat(clear.mood).isEqualTo(HeroMood.Square)
        assertThat(clear.showBreakdown).isFalse()
    }

    @Test
    fun `without your name the hero shows what is left to settle and asks for it`() {
        val noName = GroupsHomeLogic.hero(HomeOverview(null, outstandingPaise = 25_000, groupCount = 2))
        assertThat(noName.mood).isEqualTo(HeroMood.ToSettle)
        assertThat(noName.paise).isEqualTo(25_000)
        assertThat(noName.askForName).isTrue()
        assertThat(noName.nameNotFound).isFalse()

        val unknown = GroupsHomeLogic.hero(HomeOverview("Zoya", outstandingPaise = 0, groupsWithYou = 0, groupCount = 1))
        assertThat(unknown.mood).isEqualTo(HeroMood.Nothing)
        assertThat(unknown.askForName).isTrue()
        assertThat(unknown.nameNotFound).isTrue()
    }

    @Test
    fun `the hero amount never exceeds what the odometer can show`() {
        val huge = GroupsHomeLogic.hero(HomeOverview("Darshan", youAreOwedPaise = Long.MAX_VALUE / 2, youOwePaise = 0, groupsWithYou = 1, groupCount = 1))
        assertThat(huge.paise).isEqualTo(GroupsHomeLogic.MAX_SHOWN_PAISE)
    }

    // ----- card balance -----
    private fun card(yours: Long?, outstanding: Long = 0, bills: Int = 1) =
        GroupCard(GroupSummary(testGroup(), 2, bills), outstandingPaise = outstanding, yourBalancePaise = yours)

    @Test
    fun `each card says where you stand in that group`() {
        assertThat(GroupsHomeLogic.cardBalance(card(15_000, 15_000))).isEqualTo(CardBalance(CardBalanceKind.Owed, 15_000))
        assertThat(GroupsHomeLogic.cardBalance(card(-10_000, 10_000))).isEqualTo(CardBalance(CardBalanceKind.Owe, 10_000))
        assertThat(GroupsHomeLogic.cardBalance(card(0, 4_000))).isEqualTo(CardBalance(CardBalanceKind.YouSquare, 0))
        assertThat(GroupsHomeLogic.cardBalance(card(null, 25_000))).isEqualTo(CardBalance(CardBalanceKind.ToSettle, 25_000))
        assertThat(GroupsHomeLogic.cardBalance(card(null, 0, bills = 0))).isEqualTo(CardBalance(CardBalanceKind.NoBills, 0))
        assertThat(GroupsHomeLogic.cardBalance(card(0, 0, bills = 0))).isEqualTo(CardBalance(CardBalanceKind.NoBills, 0))
        assertThat(GroupsHomeLogic.cardBalance(card(null, 0, bills = 3))).isEqualTo(CardBalance(CardBalanceKind.Settled, 0))
    }

    // ----- staggered entrance -----
    @Test
    fun `on first load the first screenful pops in staggered and the rest is already there`() {
        val tracker = EntranceTracker(staggerFirstLoad = true, maxStaggered = 3)
        tracker.update(listOf("a", "b", "c", "d", "e"))
        assertThat(tracker.take("a")).isEqualTo(0)
        assertThat(tracker.take("c")).isEqualTo(2)
        assertThat(tracker.take("d")).isNull()
        // Played once: scrolling a card out and back does not replay it.
        assertThat(tracker.take("a")).isNull()
    }

    @Test
    fun `a group created later pops in at once, and coming back to the list replays nothing`() {
        val tracker = EntranceTracker(staggerFirstLoad = true)
        tracker.update(listOf("a"))
        tracker.update(listOf("new", "a"))
        assertThat(tracker.take("new")).isEqualTo(0)

        val returning = EntranceTracker(staggerFirstLoad = false)
        returning.update(listOf("a", "b"))
        assertThat(returning.take("a")).isNull()
        returning.update(listOf("c", "a", "b"))
        assertThat(returning.take("c")).isEqualTo(0)
    }

    @Test
    fun `a card that leaves and comes back, like an undone archive, pops in again`() {
        val tracker = EntranceTracker(staggerFirstLoad = false)
        tracker.update(listOf("a", "b"))
        tracker.update(listOf("b"))
        assertThat(tracker.take("a")).isNull()
        tracker.update(listOf("a", "b"))
        assertThat(tracker.take("a")).isEqualTo(0)
        assertThat(tracker.take("b")).isNull()
    }

    @Test
    fun `menu items open from the button outwards and all arrive together at the end`() {
        assertThat(FabMenuLogic.itemProgress(0f, 0, 3)).isEqualTo(0f)
        assertThat(FabMenuLogic.itemProgress(0.2f, 2, 3)).isEqualTo(0f)
        assertThat(FabMenuLogic.itemProgress(0.5f, 0, 3)).isGreaterThan(FabMenuLogic.itemProgress(0.5f, 1, 3))
        for (i in 0 until 3) assertThat(FabMenuLogic.itemProgress(1f, i, 3)).isAtLeast(1f)
        // A spring's overshoot passes through so the items bounce with the button.
        assertThat(FabMenuLogic.itemProgress(1.1f, 2, 3)).isGreaterThan(1f)
        assertThat(FabMenuLogic.itemProgress(0.5f, 0, 1)).isWithin(1e-4f).of(0.5f)
    }

    @Test
    fun `ten thousand groups cost one entry each and only the first screenful animates`() {
        val tracker = EntranceTracker(staggerFirstLoad = true)
        val ids = List(10_000) { "g$it" }
        tracker.update(ids)
        assertThat(ids.count { tracker.take(it) != null }).isEqualTo(EntranceTracker.MAX_STAGGERED)
    }

    // ----- swipe to archive -----
    private val width = 1_000f
    private val fling = 2_000f

    @Test
    fun `a slow drag archives only past the threshold, either way`() {
        assertThat(SwipeToArchiveLogic.settle(-399f, width, 0f, fling)).isEqualTo(SwipeOutcome.Return)
        assertThat(SwipeToArchiveLogic.settle(-400f, width, 0f, fling)).isEqualTo(SwipeOutcome.Archive)
        assertThat(SwipeToArchiveLogic.settle(450f, width, 0f, fling)).isEqualTo(SwipeOutcome.Archive)
        assertThat(SwipeToArchiveLogic.settle(0f, width, 0f, fling)).isEqualTo(SwipeOutcome.Return)
    }

    @Test
    fun `a fling archives in its direction from a short drag, and a fling back cancels`() {
        assertThat(SwipeToArchiveLogic.settle(-150f, width, -2_500f, fling)).isEqualTo(SwipeOutcome.Archive)
        // Too short to be a swipe at all: a fling from a twitch is not intent.
        assertThat(SwipeToArchiveLogic.settle(-20f, width, -2_500f, fling)).isEqualTo(SwipeOutcome.Return)
        assertThat(SwipeToArchiveLogic.settle(-600f, width, 2_500f, fling)).isEqualTo(SwipeOutcome.Return)
    }

    @Test
    fun `the threshold arms once per crossing, for one tick`() {
        assertThat(SwipeToArchiveLogic.armed(-399f, width)).isFalse()
        assertThat(SwipeToArchiveLogic.armed(-400f, width)).isTrue()
        assertThat(SwipeToArchiveLogic.crossed(-390f, -410f, width)).isTrue()
        assertThat(SwipeToArchiveLogic.crossed(-410f, -420f, width)).isFalse()
        assertThat(SwipeToArchiveLogic.crossed(-410f, -390f, width)).isTrue()
    }

    @Test
    fun `the reveal grows to full at the threshold and drags never leave the row`() {
        assertThat(SwipeToArchiveLogic.reveal(-200f, width)).isWithin(1e-4f).of(0.5f)
        assertThat(SwipeToArchiveLogic.reveal(-900f, width)).isEqualTo(1f)
        assertThat(SwipeToArchiveLogic.clamp(-5_000f, width)).isEqualTo(-width)
        assertThat(SwipeToArchiveLogic.clamp(5_000f, width)).isEqualTo(width)
        assertThat(SwipeToArchiveLogic.reveal(10f, 0f)).isEqualTo(0f)
    }
}
