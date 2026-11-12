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
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage

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
            SplitSmartTheme(darkTheme = dark, reduceMotion = reduceMotion, haptics = haptics) {
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

/**
 * Fails if this text node was cut: a line ending in an ellipsis, or lines past its box. Text that must be read in full (a UPI ID before paying, a
 * label at large text) checks this rather than only its bounds.
 */
fun SemanticsNodeInteraction.assertTextWhole() {
    val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
    val node = fetchSemanticsNode()
    node.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(results)
    assertWithMessage("text layout of ${node.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }}").that(results).isNotEmpty()
    val r = results.first()
    // Wrapped text never overflows its width; it is cut when a line ends in an ellipsis or the lines run past the box.
    val cut = (0 until r.lineCount).any { r.isLineEllipsized(it) } || r.didOverflowHeight
    assertWithMessage("cut text: ${r.layoutInput.text} (lines ${r.lineCount} of ${r.layoutInput.maxLines})").that(cut).isFalse()
}
