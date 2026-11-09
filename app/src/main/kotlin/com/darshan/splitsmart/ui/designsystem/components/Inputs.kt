package com.darshan.splitsmart.ui.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import com.darshan.splitsmart.ui.designsystem.effects.LocalHaptics
import com.darshan.splitsmart.ui.designsystem.effects.reject
import com.darshan.splitsmart.ui.designsystem.modifiers.rememberShakeState
import com.darshan.splitsmart.ui.designsystem.modifiers.shake
import com.darshan.splitsmart.ui.designsystem.motion.LocalMotion
import com.darshan.splitsmart.ui.designsystem.motion.MotionToken
import com.darshan.splitsmart.ui.designsystem.tokens.Radii
import com.darshan.splitsmart.ui.designsystem.tokens.Sizes
import com.darshan.splitsmart.ui.designsystem.tokens.Spacing
import com.darshan.splitsmart.ui.designsystem.tokens.Strokes
import com.darshan.splitsmart.ui.theme.SplitSmartTheme

/**
 * A text field: the label sits above a chunky rounded box. Focus draws an accent ring with a soft glow; an error turns the ring
 * chilli, wiggles the field once and plays the reject haptic (only when the error appears, not when a screen restores with
 * one). The label, value and error are one TalkBack node. [emoji] decorates the start of the box.
 */
@Composable
fun SsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    supporting: String? = null,
    emoji: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val motion = LocalMotion.current
    val haptics = LocalHaptics.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val focusState = animateFloatAsState(if (focused) 1f else 0f, motion.spec(MotionToken.Snap), label = "focus")
    val shake = rememberShakeState()
    val lastError = remember { arrayOfNulls<String>(1).also { it[0] = error } }
    LaunchedEffect(error) {
        if (error != null && lastError[0] == null) {
            shake.shake()
            haptics.reject()
        }
        lastError[0] = error
    }
    val ring = when {
        error != null -> colors.failed.content
        focused -> colors.accent
        else -> colors.outline
    }
    val glow = if (error != null) colors.failed.content else colors.accent
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().semantics { if (error != null) error(error) },
        enabled = enabled,
        singleLine = singleLine,
        textStyle = type.body.copy(color = if (enabled) colors.text else colors.textDisabled),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interaction,
        decorationBox = { inner ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                BasicText(label, style = type.labelStrong.copy(color = if (error != null) colors.failed.content else colors.textSecondary))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .shake(shake)
                        .heightIn(min = Sizes.fieldHeight)
                        .drawWithCache {
                            val radius = Radii.small.toPx()
                            val strokeWidth = if (focused || error != null) Strokes.focus.toPx() else Strokes.outline.toPx()
                            val stroke = Stroke(strokeWidth)
                            val fill = if (enabled) colors.surface else colors.disabledContainer
                            onDrawBehind {
                                val focus = focusState.value
                                val glowWidth = Strokes.focusGlow.toPx() * focus
                                if (glowWidth > 0f) {
                                    drawRoundRect(
                                        glow,
                                        topLeft = Offset(-glowWidth, -glowWidth),
                                        size = Size(size.width + glowWidth * 2, size.height + glowWidth * 2),
                                        cornerRadius = CornerRadius(radius + glowWidth),
                                        alpha = GLOW_ALPHA * focus,
                                    )
                                }
                                drawRoundRect(fill, cornerRadius = CornerRadius(radius))
                                drawRoundRect(
                                    ring,
                                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                                    cornerRadius = CornerRadius(radius - strokeWidth / 2),
                                    style = stroke,
                                )
                            }
                        }
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    if (emoji != null) BasicText(emoji, style = type.body.copy(color = colors.textSecondary))
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) BasicText(placeholder, style = type.body.copy(color = colors.textSecondary), maxLines = 1)
                        inner()
                    }
                }
                when {
                    error != null -> BasicText(error, style = type.label.copy(color = colors.failed.content))
                    supporting != null -> BasicText(supporting, style = type.label.copy(color = colors.textSecondary))
                }
            }
        },
    )
}

private const val GLOW_ALPHA = 0.22f
