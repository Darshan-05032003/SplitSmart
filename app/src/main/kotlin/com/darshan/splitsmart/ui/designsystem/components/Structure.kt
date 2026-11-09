package com.darshan.splitsmart.ui.designsystem.components

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.darshan.splitsmart.ui.designsystem.effects.LocalHaptics
import com.darshan.splitsmart.ui.designsystem.effects.light
import com.darshan.splitsmart.ui.designsystem.modifiers.Squash
import com.darshan.splitsmart.ui.designsystem.modifiers.SquashMath
import com.darshan.splitsmart.ui.designsystem.modifiers.rememberPressFeedback
import com.darshan.splitsmart.ui.designsystem.modifiers.squashAndSink
import com.darshan.splitsmart.ui.designsystem.modifiers.trackPress
import com.darshan.splitsmart.ui.designsystem.tokens.Depth
import com.darshan.splitsmart.ui.designsystem.tokens.Radii
import com.darshan.splitsmart.ui.designsystem.tokens.Sizes
import com.darshan.splitsmart.ui.designsystem.tokens.Spacing
import com.darshan.splitsmart.ui.theme.SplitSmartTheme

/**
 * A card that sits on the page like a sticker: [color] fill on a soft solid offset shadow. With [onClick] it presses down into
 * its shadow and squashes a touch; content is merged for TalkBack only when clickable.
 */
@Composable
fun ChunkyCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    color: Color = SplitSmartTheme.colors.surface,
    shape: Shape = Radii.mediumShape,
    depth: Dp = Depth.card,
    contentPadding: PaddingValues = PaddingValues(Spacing.md),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shadow = SplitSmartTheme.colors.shadow
    val press = rememberPressFeedback()
    val haptics = LocalHaptics.current
    val currentOnClick by rememberUpdatedState(onClick)
    val guard = remember { ClickGuard() }
    val clickable = onClick != null
    Column(
        modifier
            .then(
                if (clickable) {
                    Modifier
                        .trackPress(press)
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClickLabel = onClickLabel) {
                            if (guard.tryAccept(SystemClock.uptimeMillis())) {
                                haptics.light()
                                currentOnClick?.invoke()
                            }
                        }
                } else {
                    Modifier
                },
            )
            .padding(bottom = depth)
            .graphicsLayer { squashAndSink(press.progress, Squash.Subtle, depth.toPx()) }
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                onDrawBehind {
                    val lip = depth.toPx() * (1f - SquashMath.depthTravel(press.progress))
                    if (lip > 0f) translate(top = lip) { drawOutline(outline, shadow) }
                }
            }
            .background(color, shape)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * One row of a list: [leading] (an avatar, an emoji tile), a title and an optional subtitle, and [trailing] (an amount pill, a
 * chevron). Text from outside the app is cleaned with [DisplayText.safe]; the title stays on one line, the subtitle on two.
 * Clickable rows squash a little and merge for TalkBack. Transparent: put rows in a [ChunkyCard] or on the page.
 */
@Composable
fun SsListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    minHeight: Dp = Sizes.listRowHeight,
) {
    val type = SplitSmartTheme.type
    val colors = SplitSmartTheme.colors
    val press = rememberPressFeedback()
    val currentOnClick by rememberUpdatedState(onClick)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .then(
                if (onClick != null) {
                    Modifier
                        .trackPress(press)
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClickLabel = onClickLabel) { currentOnClick?.invoke() }
                        .graphicsLayer { squashAndSink(press.progress, Squash.Subtle) }
                } else {
                    Modifier.semantics(mergeDescendants = true) {}
                },
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (leading != null) leading()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs / 2)) {
            BasicText(DisplayText.safe(title), style = type.bodyStrong.copy(color = colors.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                BasicText(DisplayText.safe(subtitle), style = type.label.copy(color = colors.textSecondary), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) trailing()
    }
}

/** A section title with an optional emoji and one text action on the right ("See all"). Marked as a heading for TalkBack. */
@Composable
fun SsSectionHeader(title: String, modifier: Modifier = Modifier, emoji: String? = null, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    val type = SplitSmartTheme.type
    val colors = SplitSmartTheme.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).padding(horizontal = Spacing.screen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically) {
            if (emoji != null) {
                BasicText(emoji, style = type.title.copy(color = colors.text))
                Spacer(Modifier.width(Spacing.xs))
            }
            BasicText(title, style = type.title.copy(color = colors.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (actionLabel != null && onAction != null) {
            TextAction(actionLabel, onAction)
        }
    }
}

/** A text-only action in the accent colour with a full touch target. */
@Composable
fun TextAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SplitSmartTheme.colors
    val press = rememberPressFeedback()
    val currentOnClick by rememberUpdatedState(onClick)
    Box(
        modifier
            .heightIn(min = Sizes.touchTarget)
            .trackPress(press)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { currentOnClick() }
            .graphicsLayer { squashAndSink(press.progress, Squash.Chunky) }
            .padding(horizontal = Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = SplitSmartTheme.type.bodyStrong.copy(color = colors.accent), maxLines = 1)
    }
}

/**
 * The top bar. [large] uses the display face for tab roots and big moments; otherwise the title face. A back button appears when
 * [onBack] is given. Applies the status bar inset itself.
 */
@Composable
fun SsTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backLabel: String = "Back",
    large: Boolean = false,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val type = SplitSmartTheme.type
    val colors = SplitSmartTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .heightIn(min = Sizes.topBarHeight)
            .padding(start = if (onBack != null) Spacing.xs else Spacing.screen, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            ChunkyIconButton(Icons.AutoMirrored.Filled.ArrowBack, backLabel, onBack, style = ButtonStyle.Ghost)
            Spacer(Modifier.width(Spacing.xxs))
        }
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
            BasicText(DisplayText.safe(title), style = (if (large) type.display else type.title).copy(color = colors.text), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) BasicText(subtitle, style = type.label.copy(color = colors.textSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}
