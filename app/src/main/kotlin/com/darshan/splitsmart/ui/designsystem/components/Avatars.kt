package com.darshan.splitsmart.ui.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darshan.splitsmart.ui.designsystem.effects.LocalHaptics
import com.darshan.splitsmart.ui.designsystem.effects.tick
import com.darshan.splitsmart.ui.designsystem.modifiers.Squash
import com.darshan.splitsmart.ui.designsystem.modifiers.rememberPressFeedback
import com.darshan.splitsmart.ui.designsystem.modifiers.squash
import com.darshan.splitsmart.ui.designsystem.modifiers.trackPress
import com.darshan.splitsmart.ui.designsystem.motion.LocalMotion
import com.darshan.splitsmart.ui.designsystem.motion.MotionToken
import com.darshan.splitsmart.ui.designsystem.tokens.BricolageText
import com.darshan.splitsmart.ui.designsystem.tokens.Radii
import com.darshan.splitsmart.ui.designsystem.tokens.Sizes
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.roundToInt

/** Initials size relative to the circle: fixed to the avatar, not the font scale, so 1.3× text never bursts a face. */
private const val INITIALS_RATIO = 0.4f
private const val OVERFLOW_RATIO = 0.3f

/** How much neighbouring faces overlap in a resting stack. */
private const val STACK_OVERLAP = 0.32f

/** Fan-out wobble: degrees of tilt per step from the middle face, and the hop height as a fraction of the face. */
private const val FAN_TILT_DEGREES = 7f
private const val FAN_HOP = 0.22f

@Composable
private fun initialsStyle(size: Dp, ratio: Float): TextStyle {
    val fontSize = with(LocalDensity.current) { (size * ratio).toSp() }
    return TextStyle(fontFamily = BricolageText, fontWeight = FontWeight.ExtraBold, fontSize = fontSize, textAlign = TextAlign.Center)
}

/**
 * A person: initials on a pastel that is stable per name (Asha is the same colour on every screen). [ring] draws a thin ring in
 * [ringColor] so overlapping faces stay separate. The name is the TalkBack label unless [contentDescription] says otherwise.
 */
@Composable
fun MemberAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = Sizes.avatar,
    ring: Boolean = false,
    ringColor: Color = SplitSmartTheme.colors.surface,
    contentDescription: String? = DisplayText.safe(name),
) {
    val tones = SplitSmartTheme.colors.avatars
    val tone = tones[AvatarLogic.toneIndex(name, tones.size)]
    val label = remember(name) { AvatarLogic.initials(name) }
    Box(
        modifier
            .size(size)
            .then(if (ring) Modifier.border(Sizes.avatarRing, ringColor, CircleShapeRef) else Modifier)
            .padding(if (ring) Sizes.avatarRing else 0.dp)
            .background(tone.background, CircleShapeRef)
            .clearAndSetSemantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = initialsStyle(size, INITIALS_RATIO).copy(color = tone.content), maxLines = 1)
    }
}

private val CircleShapeRef = Radii.circleShape

/** The "+196" face at the end of a long stack. */
@Composable
private fun OverflowFace(overflow: Int, size: Dp, ringColor: Color) {
    val colors = SplitSmartTheme.colors
    Box(
        Modifier
            .size(size)
            .border(Sizes.avatarRing, ringColor, CircleShapeRef)
            .background(colors.raised, CircleShapeRef),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(AvatarLogic.overflowLabel(overflow), style = initialsStyle(size, OVERFLOW_RATIO).copy(color = colors.text), maxLines = 1)
    }
}

/** Stateful stack: remembers whether it is fanned out across rotation. */
@Composable
fun AvatarStack(
    names: List<String>,
    modifier: Modifier = Modifier,
    maxVisible: Int = 4,
    size: Dp = Sizes.avatar,
    ringColor: Color = SplitSmartTheme.colors.surface,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    AvatarStack(names, expanded, { expanded = it }, modifier, maxVisible, size, ringColor)
}

/**
 * Overlapping faces that fan out on tap with a hop and a wobble on the Bouncy spring, and stack back on a second tap. Shows at
 * most [maxVisible] faces plus a "+N" face, so 200 members cost five circles. TalkBack hears one sentence ("Asha, Ravi, Meera,
 * Kabir and 196 more") and a single tap action. Animated values are read only in layout placement.
 */
@Composable
fun AvatarStack(
    names: List<String>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    maxVisible: Int = 4,
    size: Dp = Sizes.avatar,
    ringColor: Color = SplitSmartTheme.colors.surface,
) {
    val motion = LocalMotion.current
    val haptics = LocalHaptics.current
    val layout = AvatarLogic.layout(names.size, maxVisible)
    val progress = remember { Animatable(if (expanded) 1f else 0f) }
    LaunchedEffect(expanded, motion) { progress.animateTo(if (expanded) 1f else 0f, motion.spec(MotionToken.Bounce)) }
    val description = remember(names) { AvatarLogic.description(names) }
    val press = rememberPressFeedback()
    val toggle = {
        haptics.tick()
        onExpandedChange(!expanded)
    }
    val faces = layout.visible + if (layout.overflow > 0) 1 else 0
    Layout(
        content = {
            for (i in 0 until layout.visible) MemberAvatar(names[i], size = size, ring = true, ringColor = ringColor, contentDescription = null)
            if (layout.overflow > 0) OverflowFace(layout.overflow, size, ringColor)
        },
        modifier = modifier
            .trackPress(press, enabled = faces > 0)
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = faces > 0, onClick = toggle)
            .clearAndSetSemantics {
                contentDescription = description
                if (faces > 0) {
                    role = Role.Button
                    stateDescription = if (expanded) "Spread out" else "Stacked"
                    onClick(if (expanded) "Stack faces" else "Spread out faces") {
                        toggle()
                        true
                    }
                }
            }
            .squash(press, Squash.Subtle),
    ) { measurables, constraints ->
        val sizePx = size.roundToPx()
        val gapPx = Sizes.avatarFanGap.toPx()
        val placeables = measurables.map { it.measure(Constraints.fixed(sizePx, sizePx)) }
        val p = progress.value
        val width = AvatarLogic.width(placeables.size, p, sizePx.toFloat(), STACK_OVERLAP, gapPx).roundToInt()
        val height = maxOf(sizePx, Sizes.touchTarget.roundToPx())
        layout(width.coerceIn(constraints.minWidth, constraints.maxWidth), height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            val top = (height - sizePx) / 2
            val hop = AvatarLogic.hop(p) * sizePx * FAN_HOP
            val middle = (placeables.size - 1) / 2f
            placeables.forEachIndexed { index, placeable ->
                val x = AvatarLogic.offsetX(index, p, sizePx.toFloat(), STACK_OVERLAP, gapPx).roundToInt()
                // Earlier faces sit on top, so the first name is fully shown; the "+N" face sits above all so its count reads.
                val isOverflow = layout.overflow > 0 && index == placeables.lastIndex
                val z = if (isOverflow) placeables.size + 1f else (placeables.size - index).toFloat()
                placeable.placeWithLayer(x, top, zIndex = z) {
                    translationY = -hop
                    rotationZ = (index - middle) * FAN_TILT_DEGREES * AvatarLogic.hop(p)
                }
            }
        }
    }
}
