package com.darshan.splitsmart.ui.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import com.darshan.splitsmart.ui.designsystem.effects.LocalHaptics
import com.darshan.splitsmart.ui.designsystem.effects.tick
import com.darshan.splitsmart.ui.designsystem.modifiers.Squash
import com.darshan.splitsmart.ui.designsystem.modifiers.SquashMath
import com.darshan.splitsmart.ui.designsystem.modifiers.rememberPressFeedback
import com.darshan.splitsmart.ui.designsystem.modifiers.squashAndSink
import com.darshan.splitsmart.ui.designsystem.modifiers.trackPress
import com.darshan.splitsmart.ui.designsystem.motion.LocalMotion
import com.darshan.splitsmart.ui.designsystem.motion.MotionToken
import com.darshan.splitsmart.ui.designsystem.tokens.Depth
import com.darshan.splitsmart.ui.designsystem.tokens.Radii
import com.darshan.splitsmart.ui.designsystem.tokens.Sizes
import com.darshan.splitsmart.ui.designsystem.tokens.Spacing
import com.darshan.splitsmart.ui.designsystem.tokens.Strokes
import com.darshan.splitsmart.ui.designsystem.tokens.Tone
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** How far the emoji swells when a chip is picked. */
private const val EMOJI_POP = 0.45f

/**
 * A pill chip with an optional emoji, for categories, split modes and filters. Selected = marigold, sitting on its own base like
 * a button; the emoji pops on selection. [exclusive] makes it a radio option (split mode: exactly one), otherwise a checkbox
 * (filters: any number). The 36dp pill sits inside a 48dp touch target.
 */
@Composable
fun EmojiChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    enabled: Boolean = true,
    exclusive: Boolean = false,
) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    val motion = LocalMotion.current
    val haptics = LocalHaptics.current
    val press = rememberPressFeedback()
    val currentOnClick by rememberUpdatedState(onClick)
    val pop = remember { Animatable(0f) }
    val first = remember { booleanArrayOf(true) }
    LaunchedEffect(selected) {
        if (first[0]) {
            first[0] = false
            return@LaunchedEffect
        }
        if (selected && !motion.reduced) {
            pop.snapTo(1f)
            pop.animateTo(0f, motion.spec(MotionToken.Bump))
        }
    }
    val container = when {
        !enabled -> colors.disabledContainer
        selected -> colors.primary
        else -> colors.surface
    }
    val content = when {
        !enabled -> colors.textDisabled
        selected -> colors.onPrimary
        else -> colors.text
    }
    val depthColor = if (selected) colors.primaryDepth else colors.shadow
    val border = if (selected || !enabled) Color.Transparent else colors.hairline
    val shape = Radii.pillShape
    val interaction = remember { MutableInteractionSource() }
    val click = {
        haptics.tick()
        currentOnClick()
    }
    val selection = if (exclusive) {
        Modifier.selectable(selected, interaction, indication = null, enabled = enabled, role = Role.RadioButton, onClick = click)
    } else {
        Modifier.toggleable(selected, interaction, indication = null, enabled = enabled, role = Role.Checkbox, onValueChange = { click() })
    }
    Box(modifier.heightIn(min = Sizes.touchTarget).trackPress(press, enabled).then(selection), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .padding(bottom = Depth.chip)
                .graphicsLayer { squashAndSink(press.progress, Squash.Chunky, Depth.chip.toPx()) }
                .drawWithCache {
                    val outline = shape.createOutline(size, layoutDirection, this)
                    onDrawBehind {
                        val lip = Depth.chip.toPx() * (1f - SquashMath.depthTravel(press.progress))
                        if (enabled && lip > 0f) translate(top = lip) { drawOutline(outline, depthColor) }
                    }
                }
                .background(container, shape)
                .border(Strokes.outline, border, shape)
                .heightIn(min = Sizes.chipHeight)
                .padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (emoji != null) {
                BasicText(
                    emoji,
                    Modifier.graphicsLayer {
                        val s = 1f + EMOJI_POP * pop.value
                        scaleX = s
                        scaleY = s
                        rotationZ = -12f * pop.value
                    },
                    style = type.bodyStrong.copy(color = content),
                )
            }
            BasicText(label, style = type.bodyStrong.copy(color = content), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Each status has its own colour and its own small drawn mark. */
enum class StatusKind {
    /** Money coming to you. Mint, an up-right arrow. */
    Owed,

    /** Money you owe. Chilli, a down-right arrow. */
    Owe,

    /** All square. Mint, a check. */
    Settled,

    /** AI still working. Amber, a dot that breathes. */
    Refining,

    /** Made by AI. Plum, a four-point sparkle that twinkles. */
    Ai,

    /** Anything else. */
    Neutral,
}

@Composable
private fun StatusKind.tone(): Tone {
    val c = SplitSmartTheme.colors
    return when (this) {
        StatusKind.Owed, StatusKind.Settled -> c.success
        StatusKind.Owe -> c.failed
        StatusKind.Refining -> c.pending
        StatusKind.Ai -> c.info
        StatusKind.Neutral -> c.neutral
    }
}

/**
 * A small status pill. The mark is drawn, not an icon font. Refining breathes and AI twinkles (decorative: still under reduced
 * motion, and pass [animate] false inside long lists). TalkBack hears [label].
 */
@Composable
fun StatusPill(kind: StatusKind, label: String, modifier: Modifier = Modifier, animate: Boolean = true) {
    val tone = kind.tone()
    val phase = if (animate && (kind == StatusKind.Refining || kind == StatusKind.Ai)) rememberAmbientPhase() else null
    Row(
        modifier
            .heightIn(min = Sizes.pillHeight)
            .background(tone.container, Radii.pillShape)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
            .clearAndSetSemantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Spacer(
            Modifier.size(Sizes.iconSmall).drawWithCache {
                val w = size.width
                val strokeWidth = w * 0.16f
                val sparkle = sparklePath(Offset(w / 2f, size.height / 2f), w * 0.48f)
                onDrawBehind {
                    val p = phase?.value ?: 0f
                    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
                        drawLine(tone.content, Offset(w * x1, w * y1), Offset(w * x2, w * y2), strokeWidth, StrokeCap.Round)
                    when (kind) {
                        StatusKind.Owed -> {
                            line(0.25f, 0.75f, 0.75f, 0.25f)
                            line(0.4f, 0.25f, 0.75f, 0.25f)
                            line(0.75f, 0.25f, 0.75f, 0.6f)
                        }
                        StatusKind.Owe -> {
                            line(0.25f, 0.25f, 0.75f, 0.75f)
                            line(0.4f, 0.75f, 0.75f, 0.75f)
                            line(0.75f, 0.75f, 0.75f, 0.4f)
                        }
                        StatusKind.Settled -> {
                            line(0.2f, 0.52f, 0.42f, 0.74f)
                            line(0.42f, 0.74f, 0.8f, 0.3f)
                        }
                        StatusKind.Refining -> {
                            val breath = 0.5f + 0.5f * sin(p * 2f * PI.toFloat())
                            drawCircle(tone.content, radius = w * (0.3f + 0.2f * breath), alpha = HALO_ALPHA)
                            drawCircle(tone.content, radius = w * 0.24f)
                        }
                        StatusKind.Ai -> scale(0.8f + 0.2f * cos(p * 2f * PI.toFloat())) { drawPath(sparkle, tone.content) }
                        StatusKind.Neutral -> drawCircle(tone.content, radius = w * 0.22f)
                    }
                }
            },
        )
        BasicText(label, style = SplitSmartTheme.type.labelStrong.copy(color = tone.content), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private const val HALO_ALPHA = 0.25f

/** A four-point sparkle, the AI mark. Build it once (in `drawWithCache`) and reuse it every frame. */
internal fun sparklePath(center: Offset, radius: Float): Path {
    val waist = radius * 0.28f
    return Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x + waist * 0.4f, center.y - waist * 0.4f, center.x + radius, center.y)
        quadraticTo(center.x + waist * 0.4f, center.y + waist * 0.4f, center.x, center.y + radius)
        quadraticTo(center.x - waist * 0.4f, center.y + waist * 0.4f, center.x - radius, center.y)
        quadraticTo(center.x - waist * 0.4f, center.y - waist * 0.4f, center.x, center.y - radius)
        close()
    }
}
