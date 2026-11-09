package com.darshan.splitsmart.ui.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import com.darshan.splitsmart.ui.designsystem.effects.LocalHaptics
import com.darshan.splitsmart.ui.designsystem.effects.light
import com.darshan.splitsmart.ui.designsystem.modifiers.Squash
import com.darshan.splitsmart.ui.designsystem.modifiers.squashAndSink
import com.darshan.splitsmart.ui.designsystem.modifiers.rememberPressFeedback
import com.darshan.splitsmart.ui.designsystem.modifiers.trackPress
import com.darshan.splitsmart.ui.designsystem.motion.LocalMotion
import com.darshan.splitsmart.ui.designsystem.motion.MotionToken
import com.darshan.splitsmart.ui.designsystem.tokens.Depth
import com.darshan.splitsmart.ui.designsystem.tokens.Radii
import com.darshan.splitsmart.ui.designsystem.tokens.Sizes
import com.darshan.splitsmart.ui.designsystem.tokens.Spacing
import com.darshan.splitsmart.ui.theme.SplitSmartTheme

/**
 * Shows the [state]'s current message as a chunky aubergine (dark: cream) card that pops up from the bottom on the Bouncy spring.
 * An undo message carries its action and a thin bar that drains over the undo window; when it empties the change is final.
 * With TalkBack on, the window stretches to what the system recommends, so nobody has to race the timer. The message is a
 * polite live region. Put the host at the bottom of a full-screen `Box`.
 */
@Composable
fun SsSnackbarHost(state: SsSnackbarState, modifier: Modifier = Modifier) {
    val motion = LocalMotion.current
    val current = state.current
    val lastShown = remember { arrayOfNulls<SsSnackbarData>(1) }
    if (current != null) lastShown[0] = current
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = current != null
    val enter = if (motion.reduced) fadeIn(motion.spec(MotionToken.Micro)) else {
        slideInVertically(motion.spec<IntOffset>(MotionToken.Bounce)) { it } + scaleIn(motion.spec(MotionToken.Bounce), initialScale = POP_FROM) +
            fadeIn(motion.spec(MotionToken.Micro))
    }
    val exit = if (motion.reduced) fadeOut(motion.spec(MotionToken.Micro)) else {
        slideOutVertically(motion.spec<IntOffset>(MotionToken.ScreenExit)) { it } + fadeOut(motion.spec(MotionToken.ScreenExit))
    }
    AnimatedVisibility(visibility, modifier.fillMaxWidth().navigationBarsPadding(), enter = enter, exit = exit) {
        val data = current ?: lastShown[0] ?: return@AnimatedVisibility
        SnackbarCard(data)
    }
}

private const val POP_FROM = 0.85f

@Composable
private fun SnackbarCard(data: SsSnackbarData) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val haptics = LocalHaptics.current
    val accessibility = LocalAccessibilityManager.current
    val hasAction = data.actionLabel != null
    val window = remember(data.id, accessibility) {
        accessibility?.calculateRecommendedTimeoutMillis(data.durationMs.toLong(), containsIcons = false, containsText = true, containsControls = hasAction)
            ?: data.durationMs.toLong()
    }
    var remaining by remember(data.id) { mutableFloatStateOf(1f) }
    LaunchedEffect(data.id) {
        val start = withFrameMillis { it }
        while (!data.resolved) {
            val now = withFrameMillis { it }
            remaining = SsSnackbarState.remainingFraction(now - start, window.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            if (remaining <= 0f) data.timeOut()
        }
    }
    val shape = Radii.mediumShape
    val shadow = colors.shadow
    val timer = colors.snackbarAction
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen, vertical = Spacing.sm)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                paneTitle = data.message
            }
            .padding(bottom = Depth.card)
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                val depthPx = Depth.card.toPx()
                onDrawBehind { translate(top = depthPx) { drawOutline(outline, shadow) } }
            }
            .clip(shape)
            .background(colors.snackbar)
            .drawWithCache {
                val barHeight = Sizes.snackbarTimerHeight.toPx()
                onDrawWithContent {
                    drawContent()
                    if (hasAction) drawRect(timer, topLeft = Offset(0f, size.height - barHeight), size = Size(size.width * remaining, barHeight))
                }
            },
    ) {
        Row(
            Modifier.heightIn(min = Sizes.snackbarMinHeight).padding(start = Spacing.md, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (data.emoji != null) BasicText(data.emoji, style = type.title.copy(color = colors.onSnackbar))
            BasicText(
                data.message,
                Modifier.weight(1f).padding(vertical = Spacing.sm),
                style = type.bodyStrong.copy(color = colors.onSnackbar),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (data.actionLabel != null) {
                val press = rememberPressFeedback()
                Box(
                    Modifier
                        .heightIn(min = Sizes.touchTarget)
                        .trackPress(press)
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                            if (data.performAction()) haptics.light()
                        }
                        .graphicsLayer { squashAndSink(press.progress, Squash.Chunky) }
                        .padding(horizontal = Spacing.sm),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(data.actionLabel, style = type.button.copy(color = colors.snackbarAction), maxLines = 1)
                }
            }
        }
    }
}
