package com.darshan.splitsmart.ui.designsystem.components

import android.os.SystemClock
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.darshan.splitsmart.ui.designsystem.effects.LocalHaptics
import com.darshan.splitsmart.ui.designsystem.effects.light
import com.darshan.splitsmart.ui.designsystem.modifiers.PressFeedback
import com.darshan.splitsmart.ui.designsystem.modifiers.Squash
import com.darshan.splitsmart.ui.designsystem.modifiers.SquashMath
import com.darshan.splitsmart.ui.designsystem.modifiers.rememberPressFeedback
import com.darshan.splitsmart.ui.designsystem.modifiers.squashAndSink
import com.darshan.splitsmart.ui.designsystem.modifiers.trackPress
import com.darshan.splitsmart.ui.designsystem.motion.MotionDurations
import com.darshan.splitsmart.ui.designsystem.tokens.Depth
import com.darshan.splitsmart.ui.designsystem.tokens.Radii
import com.darshan.splitsmart.ui.designsystem.tokens.Sizes
import com.darshan.splitsmart.ui.designsystem.tokens.Spacing
import com.darshan.splitsmart.ui.designsystem.tokens.Strokes
import com.darshan.splitsmart.ui.theme.SplitSmartTheme

enum class ButtonStyle {
    /** The one main action on a screen: marigold, pressing down into its own base. */
    Primary,

    /** Supporting actions: cream (dark: plum) with a softer base. */
    Secondary,

    /** Low emphasis: outline only, squashes but has no base. */
    Ghost,

    /** Destructive: chilli. Pair it with a confirmation or an undo. */
    Danger,
}

enum class ButtonSize(val height: Dp, val horizontalPadding: Dp) {
    Regular(Sizes.buttonHeight, Spacing.xl),
    Compact(Sizes.buttonHeightCompact, Spacing.md),
}

@Immutable
internal data class ButtonColors(val container: Color, val content: Color, val depth: Color, val border: Color?)

@Composable
internal fun buttonColors(style: ButtonStyle, enabled: Boolean): ButtonColors {
    val c = SplitSmartTheme.colors
    if (!enabled) return ButtonColors(c.disabledContainer, c.textDisabled, Color.Transparent, null)
    return when (style) {
        ButtonStyle.Primary -> ButtonColors(c.primary, c.onPrimary, c.primaryDepth, null)
        ButtonStyle.Secondary -> ButtonColors(c.raised, c.text, c.secondaryDepth, null)
        ButtonStyle.Ghost -> ButtonColors(Color.Transparent, c.text, Color.Transparent, c.outline)
        ButtonStyle.Danger -> ButtonColors(c.danger, c.onDanger, c.dangerDepth, null)
    }
}

/**
 * The chunky tactile surface shared by buttons: a face on a solid base. On finger-down the face squashes (wider, flatter) and
 * sinks into its base within one frame; on release it springs back past rest, which is the stretch.
 *
 * One action per [debounceMs], a light haptic per accepted tap, nothing at all while [loading]. The face reads [press] only in
 * its graphics layer, so pressing never recomposes.
 */
@Composable
internal fun TactileSurface(
    onClick: () -> Unit,
    colors: ButtonColors,
    shape: Shape,
    depth: Dp,
    enabled: Boolean,
    loading: Boolean,
    debounceMs: Int,
    press: PressFeedback,
    onClickLabel: String?,
    modifier: Modifier = Modifier,
    squash: Squash = Squash.Chunky,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHaptics.current
    val currentOnClick by rememberUpdatedState(onClick)
    val currentLoading by rememberUpdatedState(loading)
    val guard = remember(debounceMs) { ClickGuard(debounceMs.toLong()) }
    val interaction = remember { MutableInteractionSource() }
    val showDepth = enabled && colors.depth != Color.Transparent
    val depthColor = colors.depth
    Box(
        modifier
            .trackPress(press, enabled = enabled && !loading)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClickLabel = onClickLabel) {
                if (!currentLoading && guard.tryAccept(SystemClock.uptimeMillis())) {
                    haptics.light()
                    currentOnClick()
                }
            }
            .semantics { if (loading) stateDescription = LOADING_STATE }
            .padding(bottom = depth)
            .graphicsLayer { squashAndSink(press.progress, squash, depth.toPx()) }
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                onDrawBehind {
                    // The base is the lip under the face: it closes as the face sinks, so a pressed button sits flush.
                    val lip = depth.toPx() * (1f - SquashMath.depthTravel(press.progress))
                    if (showDepth && lip > 0f) translate(top = lip) { drawOutline(outline, depthColor) }
                }
            }
            .background(colors.container, shape)
            .then(if (colors.border != null) Modifier.border(Strokes.outline, colors.border, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

internal const val LOADING_STATE = "Working on it"

/**
 * A chunky rounded button. Use one [ButtonStyle.Primary] per screen.
 *
 * [loading] keeps the label's width and swaps it for bouncing dots; taps are ignored until it clears. Set it the moment an
 * action is accepted (saving, settling) so no second tap can slip through. [emoji] sits before the label as a playful accent.
 */
@Composable
fun ChunkyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Primary,
    size: ButtonSize = ButtonSize.Regular,
    enabled: Boolean = true,
    loading: Boolean = false,
    emoji: String? = null,
    icon: ImageVector? = null,
    onClickLabel: String? = null,
    debounceMs: Int = MotionDurations.TapDebounce,
    press: PressFeedback = rememberPressFeedback(),
) {
    val colors = buttonColors(style, enabled)
    val type = SplitSmartTheme.type
    TactileSurface(
        onClick = onClick,
        colors = colors,
        shape = Radii.mediumShape,
        depth = if (style == ButtonStyle.Ghost) Depth.none else Depth.button,
        enabled = enabled,
        loading = loading,
        debounceMs = debounceMs,
        press = press,
        onClickLabel = onClickLabel,
        modifier = modifier.defaultMinSize(minWidth = Sizes.touchTarget),
    ) {
        Box(Modifier.heightIn(min = size.height).padding(horizontal = size.horizontalPadding), contentAlignment = Alignment.Center) {
            Row(
                Modifier.graphicsLayer { alpha = if (loading) 0f else 1f },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (emoji != null) {
                    BasicText(emoji, style = type.button.copy(color = colors.content))
                    Spacer(Modifier.width(Spacing.xs))
                } else if (icon != null) {
                    Image(rememberVectorPainter(icon), contentDescription = null, colorFilter = ColorFilter.tint(colors.content), modifier = Modifier.size(Sizes.icon))
                    Spacer(Modifier.width(Spacing.xs))
                }
                BasicText(text, style = type.button.copy(color = colors.content), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (loading) LoadingDots(color = colors.content)
        }
    }
}

enum class IconButtonSize(val diameter: Dp, val iconSize: Dp) {
    Regular(Sizes.touchTarget, Sizes.icon),
    Fab(Sizes.fab, Sizes.iconLarge),
}

/** A round chunky button around one icon. [contentDescription] is required: an icon alone says nothing to TalkBack. */
@Composable
fun ChunkyIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Secondary,
    size: IconButtonSize = IconButtonSize.Regular,
    enabled: Boolean = true,
    debounceMs: Int = MotionDurations.TapDebounce,
    press: PressFeedback = rememberPressFeedback(),
) {
    val colors = buttonColors(style, enabled).let { if (style == ButtonStyle.Ghost) it.copy(border = null) else it }
    TactileSurface(
        onClick = onClick,
        colors = colors,
        shape = Radii.circleShape,
        depth = when {
            style == ButtonStyle.Ghost -> Depth.none
            size == IconButtonSize.Fab -> Depth.fab
            else -> Depth.chip
        },
        enabled = enabled,
        loading = false,
        debounceMs = debounceMs,
        press = press,
        onClickLabel = null,
        modifier = modifier.semantics { this.contentDescription = contentDescription },
    ) {
        Box(Modifier.size(size.diameter), contentAlignment = Alignment.Center) {
            Image(rememberVectorPainter(icon), contentDescription = null, colorFilter = ColorFilter.tint(colors.content), modifier = Modifier.size(size.iconSize))
        }
    }
}
