package com.darshan.splitsmart.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.Density
import com.darshan.splitsmart.ui.designsystem.effects.Haptics
import com.darshan.splitsmart.ui.theme.MaterialLook
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import com.google.common.truth.Truth.assertThat

/** Sets themed content with what design-system tests vary: font scale, reduced motion, theme, fake haptics. */
fun ComposeContentTestRule.setDs(
    fontScale: Float = 1f,
    reduceMotion: Boolean = false,
    dark: Boolean = false,
    haptics: Haptics = FakeHaptics(),
    content: @Composable BoxScope.() -> Unit,
) {
    setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            SplitSmartTheme(darkTheme = dark, reduceMotion = reduceMotion, haptics = haptics, materialLook = MaterialLook.Marigold) {
                Box(Modifier.background(SplitSmartTheme.colors.bg), content = content)
            }
        }
    }
}

/**
 * Applies a state change the way a real frame would see it. With a paused clock nothing else delivers the snapshot to the
 * recomposer before the next `advanceTimeBy`, so without this an animation test could pass without animating.
 */
fun commitState(change: () -> Unit) {
    change()
    Snapshot.sendApplyNotifications()
}

/** Successful recomposition cycles so far. A count that grows every frame of an animation means it recomposes per frame. */
fun recompositionCycles(): Long = Recomposer.runningRecomposers.value.sumOf { it.changeCount }

/** Fails if this node pokes out of [parent]: the check behind "no clipping at 1.3×". */
fun SemanticsNodeInteraction.assertInside(parent: SemanticsNodeInteraction) {
    val inner = fetchSemanticsNode().boundsInRoot
    val outer = parent.fetchSemanticsNode().boundsInRoot
    val slack = 1f
    assertThat(inner.left).isAtLeast(outer.left - slack)
    assertThat(inner.top).isAtLeast(outer.top - slack)
    assertThat(inner.right).isAtMost(outer.right + slack)
    assertThat(inner.bottom).isAtMost(outer.bottom + slack)
}
