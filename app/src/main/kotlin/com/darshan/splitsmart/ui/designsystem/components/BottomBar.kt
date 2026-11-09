package com.darshan.splitsmart.ui.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.darshan.splitsmart.ui.designsystem.effects.LocalHaptics
import com.darshan.splitsmart.ui.designsystem.effects.tick
import com.darshan.splitsmart.ui.designsystem.motion.LocalMotion
import com.darshan.splitsmart.ui.designsystem.motion.MotionToken
import com.darshan.splitsmart.ui.designsystem.tokens.Depth
import com.darshan.splitsmart.ui.designsystem.tokens.Radii
import com.darshan.splitsmart.ui.designsystem.tokens.Sizes
import com.darshan.splitsmart.ui.designsystem.tokens.Spacing
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import kotlin.math.abs
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Immutable
data class BottomBarItem(val label: String, val icon: ImageVector, val badge: Int = 0)

/** Width of the blob inside its tab, and how far the icon jumps when its tab is picked. */
private const val BLOB_WIDTH_FRACTION = 0.84f
private const val ICON_HOP = 0.3f

/**
 * The floating bottom bar: an aubergine pill with a marigold blob behind the selected tab. Picking a tab sends the blob there on
 * the Bouncy spring; on the way it stretches and flattens with its speed, then settles round, and the new icon hops. Each tab's
 * colour follows the blob's distance, so the colour change travels with it. A tick haptic per change; reselecting the current
 * tab calls [onReselect] (scroll to top). Under reduced motion the blob fades between tabs. Applies the navigation bar inset.
 */
@Composable
fun SsBottomBar(
    items: List<BottomBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onReselect: (Int) -> Unit = {},
) {
    val colors = SplitSmartTheme.colors
    val motion = LocalMotion.current
    val haptics = LocalHaptics.current
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnReselect by rememberUpdatedState(onReselect)
    val position = remember { Animatable(selectedIndex.toFloat()) }
    val blobAlpha = remember { Animatable(1f) }
    val hop = remember { Animatable(0f) }
    var hopping by remember { mutableIntStateOf(selectedIndex) }
    LaunchedEffect(selectedIndex, motion) {
        if (position.value == selectedIndex.toFloat()) return@LaunchedEffect
        hopping = selectedIndex
        if (motion.reduced) {
            blobAlpha.animateTo(0f, motion.spec(MotionToken.Micro))
            position.snapTo(selectedIndex.toFloat())
            blobAlpha.animateTo(1f, motion.spec(MotionToken.Micro))
        } else {
            coroutineScope {
                launch { position.animateTo(selectedIndex.toFloat(), motion.spec(MotionToken.Bounce)) }
                launch {
                    hop.snapTo(1f)
                    hop.animateTo(0f, motion.spec(MotionToken.Bump))
                }
            }
        }
    }
    val bar = colors.bar
    val blob = colors.primary
    val shadow = colors.shadow
    val shape = Radii.largeShape
    Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = Sizes.bottomBarInset, vertical = Spacing.xs)
            .fillMaxWidth()
            .padding(bottom = Depth.card)
            .height(Sizes.bottomBarHeight)
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                val depthPx = Depth.card.toPx()
                val itemWidth = size.width / items.size.coerceAtLeast(1)
                val baseWidth = itemWidth * BLOB_WIDTH_FRACTION
                val baseHeight = size.height - Spacing.xs.toPx() * 2
                val radius = CornerRadius(Radii.medium.toPx())
                onDrawBehind {
                    translate(top = depthPx) { drawOutline(outline, shadow) }
                    drawOutline(outline, bar)
                    val shapeNow = BlobLogic.shape(position.value, position.velocity, itemWidth, baseWidth, baseHeight)
                    drawRoundRect(
                        blob,
                        topLeft = Offset(shapeNow.centerX - shapeNow.width / 2, (size.height - shapeNow.height) / 2),
                        size = Size(shapeNow.width, shapeNow.height),
                        cornerRadius = radius,
                        alpha = blobAlpha.value,
                    )
                }
            }
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(selected, remember { MutableInteractionSource() }, indication = null, role = Role.Tab) {
                        if (index == selectedIndex) {
                            currentOnReselect(index)
                        } else {
                            haptics.tick()
                            currentOnSelect(index)
                        }
                    }
                    .semantics { contentDescription = item.label },
                contentAlignment = Alignment.Center,
            ) {
                val closeness = { (1f - abs(position.value - index)).coerceIn(0f, 1f) }
                val hopFor = { if (hopping == index) hop.value else 0f }
                TabFace(item, colors.onBarSecondary, Modifier.graphicsLayer { alpha = 1f - closeness() }, hopFor)
                TabFace(item, colors.onPrimary, Modifier.graphicsLayer { alpha = closeness() }, hopFor)
                if (item.badge > 0) {
                    Box(Modifier.align(Alignment.TopCenter).padding(start = Sizes.iconLarge, top = Spacing.xxs)) {
                        CountBadge(item.badge, contentDescription = "${item.badge} new")
                    }
                }
            }
        }
    }
}

@Composable
private fun TabFace(item: BottomBarItem, tint: Color, modifier: Modifier, hop: () -> Float) {
    Column(
        modifier.clearAndSetSemantics { },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs / 2),
    ) {
        Image(
            rememberVectorPainter(item.icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier
                .size(Sizes.icon)
                .graphicsLayer {
                    val h = hop()
                    translationY = -size.height * ICON_HOP * h
                    val s = 1f + 0.15f * h
                    scaleX = s
                    scaleY = s
                },
        )
        BasicText(item.label, style = SplitSmartTheme.type.labelStrong.copy(color = tint), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
