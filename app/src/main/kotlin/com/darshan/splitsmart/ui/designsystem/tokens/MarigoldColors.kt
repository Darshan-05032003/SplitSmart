package com.darshan.splitsmart.ui.designsystem.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/** A semantic pair: text or icon [content] on a tinted [container]. */
@Immutable
data class Tone(val content: Color, val container: Color)

/** One avatar colourway. Avatars keep the same pastel in both themes, so a friend is the same colour everywhere. */
@Immutable
data class AvatarTone(val background: Color, val content: Color)

/** Colours of the code-drawn illustrations. */
@Immutable
data class IllustrationColors(
    val petal: Color,
    val petalDeep: Color,
    val leaf: Color,
    val paper: Color,
    val ink: Color,
    val chilli: Color,
    val mint: Color,
    val plum: Color,
)

/**
 * Marigold Mela: marigold hero colour on warm cream, deep aubergine ink, mint for money coming in, chilli for money going out.
 * Read it with `SplitSmartTheme.colors`. Every text pair the components draw is listed in [textPairs] and checked against
 * WCAG AA (4.5:1) by `ContrastTest`; [uiPairs] are non-text boundaries checked at 3:1.
 */
@Immutable
data class MarigoldColors(
    val isDark: Boolean,
    /** Page background. */
    val bg: Color,
    /** Cards, rows, fields. */
    val surface: Color,
    /** Tinted surface one step up: secondary buttons, selected rows, sheet in dark. */
    val raised: Color,
    val text: Color,
    val textSecondary: Color,
    /** Disabled labels. Exempt from contrast rules, but kept legible. */
    val textDisabled: Color,
    /** Marigold: the one primary action, the bottom bar blob, selected chips. */
    val primary: Color,
    val onPrimary: Color,
    /** The solid base under a primary button that it presses down into. */
    val primaryDepth: Color,
    val secondaryDepth: Color,
    /** Focus rings, links, the AI accent. Aubergine in light, marigold in dark. */
    val accent: Color,
    val onAccent: Color,
    /** Destructive actions. */
    val danger: Color,
    val onDanger: Color,
    val dangerDepth: Color,
    /** Money owed to you; settled. */
    val success: Tone,
    /** AI still refining; waiting. */
    val pending: Tone,
    /** Money you owe; errors. */
    val failed: Tone,
    /** Made by AI. */
    val info: Tone,
    val neutral: Tone,
    /** Boundaries of controls (fields, ghost buttons): 3:1 against the page. */
    val outline: Color,
    /** Decorative dividers. */
    val hairline: Color,
    /** Soft solid offset shadow under cards. */
    val shadow: Color,
    val scrim: Color,
    /** The floating bottom bar: aubergine in both themes so the marigold blob always reads. */
    val bar: Color,
    val onBar: Color,
    val onBarSecondary: Color,
    val snackbar: Color,
    val onSnackbar: Color,
    val snackbarAction: Color,
    val skeleton: Color,
    val skeletonHighlight: Color,
    val disabledContainer: Color,
    val avatars: List<AvatarTone>,
    val confetti: List<Color>,
    val illustration: IllustrationColors,
) {
    /** Every foreground/background pair that carries text in the design system. */
    fun textPairs(): List<ContrastPair> = buildList {
        for ((name, background) in listOf("bg" to bg, "surface" to surface, "raised" to raised)) {
            add(ContrastPair("text on $name", text, background))
            add(ContrastPair("textSecondary on $name", textSecondary, background))
            add(ContrastPair("accent on $name", accent, background))
            add(ContrastPair("success on $name", success.content, background))
            add(ContrastPair("pending on $name", pending.content, background))
            add(ContrastPair("failed on $name", failed.content, background))
            add(ContrastPair("info on $name", info.content, background))
        }
        add(ContrastPair("onPrimary on primary", onPrimary, primary))
        add(ContrastPair("onAccent on accent", onAccent, accent))
        add(ContrastPair("onDanger on danger", onDanger, danger))
        add(ContrastPair("success on its container", success.content, success.container))
        add(ContrastPair("pending on its container", pending.content, pending.container))
        add(ContrastPair("failed on its container", failed.content, failed.container))
        add(ContrastPair("info on its container", info.content, info.container))
        add(ContrastPair("neutral on its container", neutral.content, neutral.container))
        add(ContrastPair("onBar on bar", onBar, bar))
        add(ContrastPair("onBarSecondary on bar", onBarSecondary, bar))
        add(ContrastPair("onSnackbar on snackbar", onSnackbar, snackbar))
        add(ContrastPair("snackbarAction on snackbar", snackbarAction, snackbar))
        add(ContrastPair("text on disabledContainer", textSecondary, disabledContainer))
        avatars.forEachIndexed { index, tone -> add(ContrastPair("avatar $index", tone.content, tone.background)) }
    }

    /** Non-text boundaries (WCAG 1.4.11, 3:1). */
    fun uiPairs(): List<ContrastPair> = listOf(
        ContrastPair("outline on bg", outline, bg, ContrastPair.UI_MINIMUM),
        ContrastPair("outline on surface", outline, surface, ContrastPair.UI_MINIMUM),
        ContrastPair("accent focus ring on surface", accent, surface, ContrastPair.UI_MINIMUM),
        ContrastPair("selection blob on bar", primary, bar, ContrastPair.UI_MINIMUM),
    )
}

@Immutable
data class ContrastPair(val name: String, val foreground: Color, val background: Color, val minimum: Double = TEXT_MINIMUM) {
    val ratio: Double get() = contrastRatio(foreground, background)

    companion object {
        const val TEXT_MINIMUM = 4.5
        const val UI_MINIMUM = 3.0
    }
}

/** WCAG 2.x relative luminance of an opaque sRGB colour. */
fun relativeLuminance(color: Color): Double {
    fun channel(c: Float): Double = if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
}

fun contrastRatio(a: Color, b: Color): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

private val Aubergine = Color(0xFF2B1238)
private val Cream = Color(0xFFFFF6E3)

private val AvatarTones = listOf(
    AvatarTone(Color(0xFFFFD77A), Aubergine),
    AvatarTone(Color(0xFFBFF0DF), Color(0xFF0A3D30)),
    AvatarTone(Color(0xFFFFC9C2), Color(0xFF5A120D)),
    AvatarTone(Color(0xFFE9D3F2), Color(0xFF3D1150)),
    AvatarTone(Color(0xFFFFD9B8), Color(0xFF4A2A00)),
    AvatarTone(Color(0xFFD9EDB0), Color(0xFF2E3B00)),
)

val MarigoldLight = MarigoldColors(
    isDark = false,
    bg = Color(0xFFFFF8E8),
    surface = Color(0xFFFFFFFF),
    raised = Color(0xFFFFEFC7),
    text = Aubergine,
    textSecondary = Color(0xFF665069),
    textDisabled = Color(0xFF9C8C9E),
    primary = Color(0xFFFFB627),
    onPrimary = Aubergine,
    primaryDepth = Color(0xFFD98A00),
    secondaryDepth = Color(0xFFEBCB85),
    accent = Color(0xFF6B2A86),
    onAccent = Color(0xFFFFFFFF),
    danger = Color(0xFFC22F27),
    onDanger = Color(0xFFFFFFFF),
    dangerDepth = Color(0xFF8A1C16),
    success = Tone(Color(0xFF0B7A5F), Color(0xFFDDF5EC)),
    pending = Tone(Color(0xFF7E5900), Color(0xFFFFEBB5)),
    failed = Tone(Color(0xFFC22F27), Color(0xFFFFE1DC)),
    info = Tone(Color(0xFF6B2A86), Color(0xFFF1E2F7)),
    neutral = Tone(Color(0xFF665069), Color(0xFFF4ECDD)),
    outline = Color(0xFF8E7A92),
    hairline = Color(0xFFEFE3CB),
    shadow = Color(0x242B1238),
    scrim = Color(0x8C2B1238),
    bar = Aubergine,
    onBar = Cream,
    onBarSecondary = Color(0xFFCDBDD0),
    snackbar = Aubergine,
    onSnackbar = Cream,
    snackbarAction = Color(0xFFFFC043),
    skeleton = Color(0xFFF6E7C8),
    skeletonHighlight = Color(0xFFFFF8E8),
    disabledContainer = Color(0xFFF1E8D8),
    avatars = AvatarTones,
    confetti = listOf(Color(0xFFFFB627), Color(0xFF16B88F), Color(0xFFE5483F), Color(0xFF8E3FB0), Color(0xFFFF8A3D)),
    illustration = IllustrationColors(
        petal = Color(0xFFFFB627),
        petalDeep = Color(0xFFF08A00),
        leaf = Color(0xFF16A37F),
        paper = Color(0xFFFFFFFF),
        ink = Aubergine,
        chilli = Color(0xFFE5483F),
        mint = Color(0xFFBFF0DF),
        plum = Color(0xFFE9D3F2),
    ),
)

val MarigoldDark = MarigoldColors(
    isDark = true,
    bg = Color(0xFF1C0F26),
    surface = Color(0xFF2A1938),
    raised = Color(0xFF382347),
    text = Cream,
    textSecondary = Color(0xFFCDBDD0),
    textDisabled = Color(0xFF7D6A84),
    primary = Color(0xFFFFC043),
    onPrimary = Aubergine,
    primaryDepth = Color(0xFFB57A00),
    secondaryDepth = Color(0xFF150A1D),
    accent = Color(0xFFFFC043),
    onAccent = Aubergine,
    danger = Color(0xFFFF9A8F),
    onDanger = Aubergine,
    dangerDepth = Color(0xFFB4554B),
    success = Tone(Color(0xFF3FE0B8), Color(0xFF123B33)),
    pending = Tone(Color(0xFFFFD479), Color(0xFF3E2F12)),
    failed = Tone(Color(0xFFFF9A8F), Color(0xFF4A1D2A)),
    info = Tone(Color(0xFFE0B3F2), Color(0xFF3D2853)),
    neutral = Tone(Color(0xFFCDBDD0), Color(0xFF33213F)),
    outline = Color(0xFF9A86A3),
    hairline = Color(0xFF3A2849),
    shadow = Color(0x66000000),
    scrim = Color(0xA6090410),
    bar = Color(0xFF382347),
    onBar = Cream,
    onBarSecondary = Color(0xFFCDBDD0),
    snackbar = Cream,
    onSnackbar = Aubergine,
    snackbarAction = Color(0xFF6B2A86),
    skeleton = Color(0xFF33213F),
    skeletonHighlight = Color(0xFF45305A),
    disabledContainer = Color(0xFF33213F),
    avatars = AvatarTones,
    confetti = listOf(Color(0xFFFFC043), Color(0xFF3FE0B8), Color(0xFFFF9A8F), Color(0xFFE0B3F2), Color(0xFFFF9E57)),
    illustration = IllustrationColors(
        petal = Color(0xFFFFC043),
        petalDeep = Color(0xFFF59E0B),
        leaf = Color(0xFF3FE0B8),
        paper = Color(0xFF503A66),
        ink = Cream,
        chilli = Color(0xFFFF9A8F),
        mint = Color(0xFF1E5A4B),
        plum = Color(0xFF5A3A72),
    ),
)
