package com.darshan.splitsmart.ui.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.Dp
import com.darshan.splitsmart.ui.designsystem.motion.MotionDurations
import com.darshan.splitsmart.ui.designsystem.tokens.Radii
import com.darshan.splitsmart.ui.designsystem.tokens.Sizes
import com.darshan.splitsmart.ui.designsystem.tokens.Spacing
import com.darshan.splitsmart.ui.theme.SplitSmartTheme

/** The shared shimmer phase of a [SkeletonGroup]; null outside a group or under reduced motion (static blocks). */
private val LocalShimmer = compositionLocalOf<State<Float>?> { null }

/** Width of the warm highlight band, as a fraction of each block. */
private const val BAND = 0.7f

/**
 * One shimmer clock for every block inside, so a whole loading list sweeps in step instead of each block on its own timer.
 * The sweep is decorative: under reduced motion the blocks are still.
 */
@Composable
fun SkeletonGroup(content: @Composable () -> Unit) {
    val phase = rememberAmbientPhase(MotionDurations.Shimmer)
    CompositionLocalProvider(LocalShimmer provides phase, content = content)
}

/** A placeholder block in the skeleton colour with a warm highlight sweeping across. Read only in draw. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, shape: Shape = Radii.smallShape) {
    val colors = SplitSmartTheme.colors
    val shimmer = LocalShimmer.current
    val base = colors.skeleton
    val highlight = colors.skeletonHighlight
    Spacer(
        modifier.drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val clip = Path().apply { addOutline(outline) }
            val band = size.width * BAND
            val brush = Brush.horizontalGradient(listOf(base, highlight, base), startX = 0f, endX = band)
            val bandSize = Size(band, size.height)
            onDrawBehind {
                drawOutline(outline, base)
                val p = shimmer?.value ?: return@onDrawBehind
                clipPath(clip) { translate(left = -band + (size.width + band) * p) { drawRect(brush, size = bandSize) } }
            }
        },
    )
}

@Composable
fun SkeletonLine(modifier: Modifier = Modifier, widthFraction: Float = 1f, height: Dp = Sizes.skeletonLine) {
    SkeletonBlock(modifier.fillMaxWidth(widthFraction).height(height), Radii.pillShape)
}

@Composable
fun SkeletonCircle(modifier: Modifier = Modifier, size: Dp = Sizes.avatar) {
    SkeletonBlock(modifier.size(size), Radii.circleShape)
}

/** The shape of a list row while it loads: a face, a title and a shorter subtitle, an amount pill. */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier, titleFraction: Float = 0.6f) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SkeletonCircle()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SkeletonLine(widthFraction = titleFraction, height = Sizes.skeletonTitle)
            SkeletonLine(widthFraction = titleFraction * 0.6f)
        }
        Box(Modifier.size(width = Sizes.avatarLarge, height = Sizes.pillHeight)) { SkeletonBlock(Modifier.matchParentSize(), Radii.pillShape) }
    }
}

/** A loading list: [count] rows in one shimmer group, read by TalkBack once as [label]. Row widths vary so it looks like content. */
@Composable
fun SkeletonList(count: Int, modifier: Modifier = Modifier, label: String = "Loading") {
    SkeletonGroup {
        Column(
            modifier.clearAndSetSemantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            },
        ) {
            repeat(count) { index -> SkeletonRow(titleFraction = ROW_WIDTHS[index % ROW_WIDTHS.size]) }
        }
    }
}

private val ROW_WIDTHS = floatArrayOf(0.62f, 0.48f, 0.7f, 0.55f)
