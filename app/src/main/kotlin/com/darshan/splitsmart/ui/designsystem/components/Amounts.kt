package com.darshan.splitsmart.ui.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import com.darshan.splitsmart.ui.designsystem.motion.LocalMotion
import com.darshan.splitsmart.ui.designsystem.motion.MotionDurations
import com.darshan.splitsmart.ui.designsystem.motion.MotionToken
import com.darshan.splitsmart.ui.designsystem.tokens.Figtree
import com.darshan.splitsmart.ui.designsystem.tokens.Radii
import com.darshan.splitsmart.ui.designsystem.tokens.Sizes
import com.darshan.splitsmart.ui.designsystem.tokens.Spacing
import com.darshan.splitsmart.ui.designsystem.tokens.Tone
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The sign of an amount always comes from the UI font, at the amount's size. */
private fun signSpan(style: TextStyle) = SpanStyle(fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = style.fontSize)

internal fun amountString(paise: Long, sign: AmountSign, decimals: AmountDecimals, style: TextStyle): AnnotatedString = buildAnnotatedString {
    val signText = AmountFormat.signText(paise, sign)
    if (signText.isNotEmpty()) withStyle(signSpan(style)) { append(signText) }
    append(AmountFormat.unsigned(paise, decimals))
}

/** A static amount: ₹ with Indian grouping, the sign in the UI font. TalkBack hears "minus" spelled out. */
@Composable
fun AmountText(
    paise: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = SplitSmartTheme.type.amount,
    color: Color = SplitSmartTheme.colors.text,
    sign: AmountSign = AmountSign.NegativeOnly,
    decimals: AmountDecimals = AmountDecimals.Always,
) {
    BasicText(
        amountString(paise, sign, decimals, style),
        modifier.clearAndSetSemantics { contentDescription = AmountFormat.spoken(if (sign == AmountSign.None) kotlin.math.abs(paise) else paise, decimals) },
        style = style.copy(color = color),
        maxLines = 1,
        softWrap = false,
    )
}

/** Which way money flows, for the pill's colour and sign. */
enum class AmountTone { Owed, Owe, Neutral }

@Composable
private fun AmountTone.colors(): Tone = when (this) {
    AmountTone.Owed -> SplitSmartTheme.colors.success
    AmountTone.Owe -> SplitSmartTheme.colors.failed
    AmountTone.Neutral -> SplitSmartTheme.colors.neutral
}

/**
 * A money pill: mint "+₹678.00" for money coming to you, chilli "−₹600.00" for money you owe. [paise] is the size of the amount;
 * the sign comes from [tone], so a caller never has to negate. An optional [label] ("you get") reads before the amount.
 */
@Composable
fun AmountPill(paise: Long, tone: AmountTone, modifier: Modifier = Modifier, label: String? = null) {
    val colors = tone.colors()
    val type = SplitSmartTheme.type
    val signed = when (tone) {
        AmountTone.Owed -> kotlin.math.abs(paise)
        AmountTone.Owe -> -kotlin.math.abs(paise)
        AmountTone.Neutral -> kotlin.math.abs(paise)
    }
    val spoken = (if (label != null) "$label " else "") + AmountFormat.spoken(signed)
    Row(
        modifier
            .heightIn(min = Sizes.pillHeight)
            .background(colors.container, Radii.pillShape)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
            .clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        if (label != null) BasicText(label, style = type.labelStrong.copy(color = colors.content), maxLines = 1)
        val sign = if (tone == AmountTone.Neutral) AmountSign.None else AmountSign.Always
        BasicText(amountString(signed, sign, AmountDecimals.Always, type.amountSmall), style = type.amountSmall.copy(color = colors.content), maxLines = 1, softWrap = false)
    }
}

/**
 * An amount that rolls: when [paise] changes, each digit column slides to its new digit in the direction the value moved, the
 * columns rippling [MotionDurations.DigitStagger] apart and landing with a small bounce. Columns are keyed by place value, so
 * ₹999 → ₹1,000 grows a column instead of reshuffling. Huge amounts (up to ₹99,99,99,999.99) shrink in steps to fit the width,
 * never below [minScale]. Under reduced motion digits cross-fade. TalkBack hears the final value at once.
 */
@Composable
fun RollingAmount(
    paise: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = SplitSmartTheme.type.hero,
    color: Color = SplitSmartTheme.colors.text,
    sign: AmountSign = AmountSign.NegativeOnly,
    decimals: AmountDecimals = AmountDecimals.Always,
    minScale: Float = MIN_FIT_SCALE,
) {
    val tracker = remember { RollTracker(paise) }
    tracker.update(paise)
    val direction = tracker.direction
    val glyphs = remember(paise, sign, decimals) { RollingAmountLogic.glyphs(paise, sign, decimals) }
    val measurer = rememberTextMeasurer()
    val spoken = AmountFormat.spoken(paise, decimals)
    BoxWithConstraints(modifier.clearAndSetSemantics { contentDescription = spoken }) {
        val text = remember(paise, sign, decimals) { amountString(paise, sign, decimals, style) }
        val natural = remember(text, style) { measurer.measure(text, style, softWrap = false, maxLines = 1).size.width.toFloat() }
        val available = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else Float.MAX_VALUE
        val scale = RollingAmountLogic.fitScale(natural, available, minScale)
        val fitted = if (scale < 1f) style.copy(fontSize = style.fontSize * scale, lineHeight = style.lineHeight * scale) else style
        Row(verticalAlignment = Alignment.CenterVertically) {
            var digitIndex = 0
            for (glyph in glyphs) {
                key(glyph.key) {
                    when (glyph) {
                        is AmountGlyph.Digit -> RollingDigit(glyph.value, fitted, color, direction, digitIndex++ * MotionDurations.DigitStagger)
                        is AmountGlyph.Sign -> BasicText(glyph.char.toString(), style = fitted.merge(signSpan(fitted).toTextStyle()).copy(color = color))
                        else -> BasicText(glyph.char.toString(), style = fitted.copy(color = color))
                    }
                }
            }
        }
    }
}

/** Remembers the last value to know which way the digits roll. Plain fields: updating it never triggers a recomposition. */
private class RollTracker(private var last: Long) {
    var direction: RollDirection = RollDirection.Up
        private set

    fun update(value: Long) {
        if (value == last) return
        direction = RollingAmountLogic.direction(last, value)
        last = value
    }
}

private fun SpanStyle.toTextStyle() = TextStyle(fontFamily = fontFamily, fontWeight = fontWeight, fontSize = fontSize)

private const val MIN_FIT_SCALE = 0.5f

/** One digit column: the old digit leaves and the new one arrives from the side the value moved. Layer-only animation. */
@Composable
private fun RollingDigit(value: Int, style: TextStyle, color: Color, direction: RollDirection, delayMs: Int) {
    val motion = LocalMotion.current
    var shown by remember { mutableIntStateOf(value) }
    var leaving by remember { mutableIntStateOf(value) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(value) {
        if (value == shown) return@LaunchedEffect
        leaving = shown
        shown = value
        progress.snapTo(0f)
        if (!motion.reduced && delayMs > 0) delay(delayMs.toLong())
        progress.animateTo(1f, motion.spec(MotionToken.Bounce))
    }
    val sign = if (direction == RollDirection.Up) 1f else -1f
    val reduced = motion.reduced
    Box(Modifier.clipToBounds()) {
        BasicText(
            ('0' + leaving).toString(),
            Modifier.graphicsLayer {
                val p = progress.value
                alpha = (1f - p).coerceIn(0f, 1f)
                if (!reduced) translationY = -sign * p * size.height
            },
            style = style.copy(color = color),
        )
        BasicText(
            ('0' + shown).toString(),
            Modifier.graphicsLayer {
                val p = progress.value
                alpha = p.coerceIn(0f, 1f)
                if (!reduced) translationY = sign * (1f - p) * size.height
            },
            style = style.copy(color = color),
        )
    }
}

/**
 * A count that bumps when it changes, like a cart counter: the number slides in from the direction of change while the whole
 * counter swells and settles on the Bouncy spring. Shows "99+" past 99. Under reduced motion the number cross-fades.
 */
@Composable
fun BumpCounter(
    count: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = SplitSmartTheme.type.numeric,
    color: Color = SplitSmartTheme.colors.text,
    contentDescription: String? = null,
) {
    val motion = LocalMotion.current
    var shown by remember { mutableIntStateOf(count) }
    var leaving by remember { mutableIntStateOf(count) }
    var direction by remember { mutableStateOf(RollDirection.Up) }
    val slide = remember { Animatable(1f) }
    val bump = remember { Animatable(0f) }
    LaunchedEffect(count) {
        if (count == shown) return@LaunchedEffect
        direction = CounterLogic.direction(shown, count)
        leaving = shown
        shown = count
        slide.snapTo(0f)
        if (!motion.reduced) bump.snapTo(1f)
        coroutineScope {
            launch { slide.animateTo(1f, motion.spec(MotionToken.Bump)) }
            if (!motion.reduced) launch { bump.animateTo(0f, motion.spec(MotionToken.Bump)) }
        }
    }
    val sign = if (direction == RollDirection.Up) 1f else -1f
    val reduced = motion.reduced
    Box(
        modifier
            .graphicsLayer {
                val s = 1f + BUMP_SCALE * bump.value
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin.Center
            }
            .clipToBounds()
            .clearAndSetSemantics { this.contentDescription = contentDescription ?: CounterLogic.label(count) },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            CounterLogic.label(leaving),
            Modifier.graphicsLayer {
                val p = slide.value
                alpha = (1f - p).coerceIn(0f, 1f)
                if (!reduced) translationY = -sign * p * size.height
            },
            style = style.copy(color = color),
        )
        BasicText(
            CounterLogic.label(shown),
            Modifier.graphicsLayer {
                val p = slide.value
                alpha = p.coerceIn(0f, 1f)
                if (!reduced) translationY = sign * (1f - p) * size.height
            },
            style = style.copy(color = color),
        )
    }
}

private const val BUMP_SCALE = 0.25f

/**
 * A marigold count bubble for an avatar or a tab: pops in when the count leaves 0, bumps on every change, pops out at 0.
 * [contentDescription] should say what is counted ("3 items").
 */
@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val colors = SplitSmartTheme.colors
    val motion = LocalMotion.current
    val minSize = Sizes.counterBadge
    AnimatedVisibility(visible = count > 0, modifier = modifier, enter = motion.popIn(), exit = motion.popOut()) {
        var last by remember { mutableIntStateOf(count) }
        if (count > 0) last = count
        Box(
            Modifier
                .defaultMinSize(minWidth = minSize, minHeight = minSize)
                .background(colors.primary, Radii.pillShape)
                .padding(horizontal = Spacing.xxs),
            contentAlignment = Alignment.Center,
        ) {
            BumpCounter(last, style = SplitSmartTheme.type.labelStrong, color = colors.onPrimary, contentDescription = contentDescription ?: "$last")
        }
    }
}
