package com.darshan.splitsmart.ui.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.darshan.splitsmart.ui.designsystem.effects.LocalHaptics
import com.darshan.splitsmart.ui.designsystem.effects.tick
import com.darshan.splitsmart.ui.designsystem.motion.LocalMotion
import com.darshan.splitsmart.ui.designsystem.motion.MotionToken
import com.darshan.splitsmart.ui.designsystem.tokens.Sizes
import com.darshan.splitsmart.ui.designsystem.tokens.Spacing
import com.darshan.splitsmart.ui.designsystem.tokens.Strokes
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import kotlin.math.abs

private val TrackWidth = 52.dp
private val TrackHeight = 32.dp

/** How much the thumb stretches along the track per unit of speed, and its limit. */
private const val THUMB_STRETCH = 0.04f
private const val THUMB_STRETCH_MAX = 0.35f

/**
 * A settings row with a chunky switch: the whole row is the touch target and one TalkBack switch. The thumb springs across on the
 * Bouncy spring and stretches with its speed; the track fills marigold as it goes. A tick haptic per change.
 */
@Composable
fun SsSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, description: String? = null) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val motion = LocalMotion.current
    val haptics = LocalHaptics.current
    val currentOnChange by rememberUpdatedState(onCheckedChange)
    val position = remember { Animatable(if (checked) 1f else 0f) }
    LaunchedEffect(checked, motion) { position.animateTo(if (checked) 1f else 0f, motion.spec(MotionToken.Bounce)) }
    val offTrack = colors.raised
    val onTrack = colors.primary
    val border = colors.outline
    val offThumb = colors.textSecondary
    val onThumb = colors.onPrimary
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .toggleable(checked, remember { MutableInteractionSource() }, indication = null, role = Role.Switch) {
                haptics.tick()
                currentOnChange(it)
            }
            .padding(horizontal = Spacing.screen, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(label, style = type.bodyStrong.copy(color = colors.text))
            if (description != null) BasicText(description, style = type.label.copy(color = colors.textSecondary))
        }
        Spacer(
            Modifier.size(TrackWidth, TrackHeight).drawWithCache {
                val stroke = Stroke(Strokes.outline.toPx())
                val inset = Spacing.xxs.toPx()
                val thumb = size.height - inset * 2
                val travel = size.width - inset * 2 - thumb
                val radius = CornerRadius(size.height / 2)
                onDrawBehind {
                    val p = position.value
                    val fill = p.coerceIn(0f, 1f)
                    drawRoundRect(lerp(offTrack, onTrack, fill), cornerRadius = radius)
                    if (fill < 1f) drawRoundRect(border, cornerRadius = radius, style = stroke, alpha = 1f - fill)
                    val stretch = (abs(position.velocity) * THUMB_STRETCH).coerceAtMost(THUMB_STRETCH_MAX)
                    val width = thumb * (1f + stretch)
                    val left = inset + travel * p - (width - thumb) * fill
                    drawRoundRect(lerp(offThumb, onThumb, fill), topLeft = Offset(left, inset), size = Size(width, thumb), cornerRadius = CornerRadius(thumb / 2))
                }
            },
        )
    }
}
