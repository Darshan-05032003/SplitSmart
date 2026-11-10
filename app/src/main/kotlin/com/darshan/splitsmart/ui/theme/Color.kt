package com.darshan.splitsmart.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.darshan.splitsmart.ui.designsystem.tokens.MarigoldColors
import com.darshan.splitsmart.ui.designsystem.tokens.MarigoldLight

/** A foreground/background pair for one semantic meaning. */
@Immutable
data class ToneColors(val content: Color, val container: Color)

/**
 * The semantic colours the pre-overhaul screens read through `SplitSmartTheme.legacy`: money direction and the hero card. Since
 * the shell switched the app to Marigold Mela they come from the Marigold tokens ([toLegacyColors]), so a screen that has not
 * been rebuilt yet still matches the palette and stays readable. Removed when the last of those screens is rebuilt.
 */
@Immutable
data class SplitSmartColors(
    val owed: ToneColors,
    val owe: ToneColors,
    /** Background of the old hero card: aubergine, like the bottom bar. */
    val heroGradient: Brush,
    val heroStops: List<Color>,
    val onHero: Color,
    /** Text/icon colour of the light action button on a hero card. */
    val heroAction: Color,
)

/** Marigold onto the old semantic roles. Every pair stays above WCAG AA (`MaterialSlotsContrastTest`). */
fun MarigoldColors.toLegacyColors(): SplitSmartColors {
    val stops = listOf(bar, if (isDark) illustration.plum else info.content)
    return SplitSmartColors(
        owed = ToneColors(success.content, success.container),
        owe = ToneColors(failed.content, failed.container),
        heroGradient = Brush.linearGradient(stops),
        heroStops = stops,
        onHero = onBar,
        heroAction = bar,
    )
}

internal val LocalSplitSmartColors = staticCompositionLocalOf { MarigoldLight.toLegacyColors() }
