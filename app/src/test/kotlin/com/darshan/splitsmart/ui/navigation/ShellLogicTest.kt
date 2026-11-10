package com.darshan.splitsmart.ui.navigation

import androidx.compose.ui.graphics.Color
import com.darshan.splitsmart.ui.designsystem.tokens.MarigoldDark
import com.darshan.splitsmart.ui.designsystem.tokens.MarigoldLight
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class ShellLogicTest {
    @Test
    fun `the bar is Groups, Scan, Me and belongs to the two tab roots only`() {
        assertThat(ShellLogic.BAR).containsExactly(ShellRoute.Groups, ShellRoute.Scan, ShellRoute.Me).inOrder()
        assertThat(ShellLogic.barVisible(ShellRoute.Groups)).isTrue()
        assertThat(ShellLogic.barVisible(ShellRoute.Me)).isTrue()
        for (route in ShellRoute.entries - setOf(ShellRoute.Groups, ShellRoute.Me)) {
            assertWithMessage("$route").that(ShellLogic.barVisible(route)).isFalse()
        }
        assertThat(ShellLogic.barVisible(null)).isFalse()
    }

    @Test
    fun `a sheet or menu open on a tab hides the bar, so nothing modal sits under it`() {
        assertThat(ShellLogic.barVisible(ShellRoute.Groups, modalOpen = true)).isFalse()
        assertThat(ShellLogic.barVisible(ShellRoute.Me, modalOpen = true)).isFalse()
        assertThat(ShellLogic.barVisible(ShellRoute.Groups, modalOpen = false)).isTrue()
        val bar = ShellBarState()
        assertThat(bar.modalOpen).isFalse()
        bar.push(); bar.push(); bar.pop()
        assertThat(bar.modalOpen).isTrue()
        bar.pop(); bar.pop()
        assertThat(bar.modalOpen).isFalse()
    }

    @Test
    fun `scan offers active groups only, unless every group is archived`() {
        val goa = com.darshan.splitsmart.testutil.testGroup("goa", "Goa trip")
        val old = com.darshan.splitsmart.testutil.testGroup("old", "Old trip").copy(archivedAt = java.time.Instant.EPOCH)
        assertThat(scanChoicesOf(listOf(goa, old))).containsExactly(ScanChoice("goa", "Goa trip"))
        assertThat(scanChoicesOf(listOf(old))).containsExactly(ScanChoice("old", "Old trip"))
        assertThat(scanChoicesOf(emptyList())).isEmpty()
        // One active group still goes straight to the camera, however many are archived.
        assertThat(ScanTarget.resolve(scanChoicesOf(listOf(goa, old)))).isEqualTo(ScanTarget.Direct("goa"))
    }

    @Test
    fun `scan is an action in the bar, never the selected tab`() {
        assertThat(ShellLogic.isAction(ShellRoute.Scan)).isTrue()
        assertThat(ShellLogic.selectedIndex(ShellRoute.Groups, lastTab = ShellRoute.Me)).isEqualTo(0)
        assertThat(ShellLogic.selectedIndex(ShellRoute.Me, lastTab = ShellRoute.Groups)).isEqualTo(2)
        // Anywhere else the blob stays on the tab the user came from.
        assertThat(ShellLogic.selectedIndex(ShellRoute.Scan, lastTab = ShellRoute.Me)).isEqualTo(2)
        assertThat(ShellLogic.selectedIndex(ShellRoute.GroupDetail, lastTab = ShellRoute.Groups)).isEqualTo(0)
        assertThat(ShellLogic.selectedIndex(null, lastTab = ShellRoute.Groups)).isEqualTo(0)
    }

    @Test
    fun `one motion vocabulary for every pair of screens`() {
        assertThat(ShellLogic.transition(ShellRoute.Groups, ShellRoute.Me)).isEqualTo(ScreenTransition.FadeThrough)
        assertThat(ShellLogic.transition(ShellRoute.Groups, ShellRoute.GroupDetail)).isEqualTo(ScreenTransition.ContainerTransform)
        assertThat(ShellLogic.transition(ShellRoute.GroupDetail, ShellRoute.BillDetail)).isEqualTo(ScreenTransition.SharedAxis)
        assertThat(ShellLogic.transition(ShellRoute.GroupDetail, ShellRoute.SettleUp)).isEqualTo(ScreenTransition.SharedAxis)
        assertThat(ShellLogic.transition(ShellRoute.BillDetail, ShellRoute.Split)).isEqualTo(ScreenTransition.SharedAxis)
        assertThat(ShellLogic.transition(ShellRoute.GroupDetail, ShellRoute.Scan)).isEqualTo(ScreenTransition.Rise)
        assertThat(ShellLogic.transition(ShellRoute.Groups, ShellRoute.Scan)).isEqualTo(ScreenTransition.Rise)
        assertThat(ShellLogic.transition(ShellRoute.Me, ShellRoute.Scan)).isEqualTo(ScreenTransition.Rise)
        // A scanned receipt moves forward into review: only entering and leaving the camera is the mode change.
        assertThat(ShellLogic.transition(ShellRoute.Scan, ShellRoute.Review)).isEqualTo(ScreenTransition.SharedAxis)
        assertThat(ShellLogic.transition(ShellRoute.Review, ShellRoute.BillDetail)).isEqualTo(ScreenTransition.SharedAxis)
        assertThat(ShellLogic.transition(null, ShellRoute.Groups)).isEqualTo(ScreenTransition.FadeThrough)
    }

    @Test
    fun `going back always undoes the way in`() {
        val routes = ShellRoute.entries + listOf(null)
        for (a in routes) for (b in routes) {
            assertWithMessage("$a <-> $b").that(ShellLogic.transition(a, b)).isEqualTo(ShellLogic.transition(b, a))
        }
    }

    @Test
    fun `scan from the bar goes straight to the only group, asks when there are several, and needs a group first`() {
        assertThat(ScanTarget.resolve(null)).isEqualTo(ScanTarget.Waiting)
        assertThat(ScanTarget.resolve(emptyList())).isEqualTo(ScanTarget.NeedsGroup)
        val one = ScanChoice("g1", "Goa trip")
        assertThat(ScanTarget.resolve(listOf(one))).isEqualTo(ScanTarget.Direct("g1"))
        val two = listOf(one, ScanChoice("g2", "Flat 302"))
        assertThat(ScanTarget.resolve(two)).isEqualTo(ScanTarget.Pick(two))
    }
}

class StatusBarLogicTest {
    @Test
    fun `dark icons on light surfaces, light icons on dark ones`() {
        assertThat(StatusBarLogic.darkIconsOn(MarigoldLight.bg)).isTrue()
        assertThat(StatusBarLogic.darkIconsOn(MarigoldLight.primary)).isTrue()
        assertThat(StatusBarLogic.darkIconsOn(MarigoldLight.pending.container)).isTrue()
        assertThat(StatusBarLogic.darkIconsOn(MarigoldDark.bg)).isFalse()
        assertThat(StatusBarLogic.darkIconsOn(MarigoldLight.bar)).isFalse()
        assertThat(StatusBarLogic.darkIconsOn(Color.Black)).isFalse()
    }

    @Test
    fun `with nothing on screen the icons follow the app theme, not the system`() {
        assertThat(StatusBarLogic.darkIcons(emptyList(), themeDark = false)).isTrue()
        assertThat(StatusBarLogic.darkIcons(emptyList(), themeDark = true)).isFalse()
    }

    @Test
    fun `the newest surface that has finished arriving owns the bar`() {
        val light = StatusBarEntry(id = 1, surface = MarigoldLight.bg, settled = true)
        val darkArriving = StatusBarEntry(id = 2, surface = MarigoldLight.bar, settled = false)
        // Mid-transition the incoming screen does not cover the bar yet: the old one keeps it.
        assertThat(StatusBarLogic.darkIcons(listOf(light, darkArriving), themeDark = false)).isTrue()
        // Once it has arrived, it hands over.
        assertThat(StatusBarLogic.darkIcons(listOf(light, darkArriving.copy(settled = true)), themeDark = false)).isFalse()
        // Order is registration order: a newer settled surface beats an older one.
        assertThat(StatusBarLogic.darkIcons(listOf(darkArriving.copy(settled = true), light.copy(id = 3)), themeDark = false)).isTrue()
    }

    @Test
    fun `if nothing has settled yet, the theme decides`() {
        val arriving = StatusBarEntry(id = 1, surface = MarigoldLight.bar, settled = false)
        assertThat(StatusBarLogic.darkIcons(listOf(arriving), themeDark = false)).isTrue()
    }
}
