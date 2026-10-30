package com.darshan.splitsmart.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * SplitSmart brand: a warm coral primary (friendly, social, "let's eat"), a deep indigo secondary used for anything
 * AI-made, and a saffron tertiary accent. Money direction has its own semantic pair (green = owed to you, red = you
 * owe) so balances never depend on the brand colour. Hand-tuned Material 3 roles for light and dark; dynamic colour
 * is off so the brand looks the same on every phone.
 */
internal object Palette {
    val Coral10 = Color(0xFF3B0A00)
    val Coral20 = Color(0xFF5E1600)
    val Coral30 = Color(0xFF8C2A0D)
    val Coral40 = Color(0xFFB5401F)
    val Coral50 = Color(0xFFD9592F)
    val Coral80 = Color(0xFFFFB59F)
    val Coral90 = Color(0xFFFFDBD0)

    val Indigo10 = Color(0xFF021945)
    val Indigo20 = Color(0xFF1C2E55)
    val Indigo30 = Color(0xFF33446D)
    val Indigo40 = Color(0xFF4A5A86)
    val Indigo80 = Color(0xFFB4C5F4)
    val Indigo90 = Color(0xFFDBE1FF)

    val Saffron10 = Color(0xFF271900)
    val Saffron20 = Color(0xFF412D00)
    val Saffron30 = Color(0xFF5E4200)
    val Saffron40 = Color(0xFF7C5800)
    val Saffron60 = Color(0xFFE8933A)
    val Saffron80 = Color(0xFFF5BE48)
    val Saffron90 = Color(0xFFFFDEA6)

    val Red10 = Color(0xFF410002)
    val Red20 = Color(0xFF690005)
    val Red30 = Color(0xFF93000A)
    val Red40 = Color(0xFFBA1A1A)
    val Red80 = Color(0xFFFFB4AB)
    val Red90 = Color(0xFFFFDAD6)

    val Green30 = Color(0xFF00522B)
    val Green40 = Color(0xFF136C3E)
    val Green80 = Color(0xFF8DD9A9)
    val Green90 = Color(0xFFC7F0D4)
}

internal val LightColors = lightColorScheme(
    primary = Palette.Coral40,
    onPrimary = Color.White,
    primaryContainer = Palette.Coral90,
    onPrimaryContainer = Palette.Coral10,
    inversePrimary = Palette.Coral80,
    secondary = Palette.Indigo40,
    onSecondary = Color.White,
    secondaryContainer = Palette.Indigo90,
    onSecondaryContainer = Palette.Indigo10,
    tertiary = Palette.Saffron40,
    onTertiary = Color.White,
    tertiaryContainer = Palette.Saffron90,
    onTertiaryContainer = Palette.Saffron10,
    error = Palette.Red40,
    onError = Color.White,
    errorContainer = Palette.Red90,
    onErrorContainer = Palette.Red10,
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF231917),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF231917),
    surfaceVariant = Color(0xFFF5DED8),
    onSurfaceVariant = Color(0xFF53433F),
    surfaceTint = Palette.Coral40,
    inverseSurface = Color(0xFF392E2B),
    inverseOnSurface = Color(0xFFFFEDE8),
    outline = Color(0xFF85736E),
    outlineVariant = Color(0xFFD8C2BC),
    scrim = Color.Black,
    surfaceBright = Color(0xFFFFF8F6),
    surfaceDim = Color(0xFFE8D6D2),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF1ED),
    surfaceContainer = Color(0xFFFCEAE5),
    surfaceContainerHigh = Color(0xFFF6E4DF),
    surfaceContainerHighest = Color(0xFFF1DFDA),
)

internal val DarkColors = darkColorScheme(
    primary = Palette.Coral80,
    onPrimary = Palette.Coral20,
    primaryContainer = Palette.Coral30,
    onPrimaryContainer = Palette.Coral90,
    inversePrimary = Palette.Coral40,
    secondary = Palette.Indigo80,
    onSecondary = Palette.Indigo20,
    secondaryContainer = Palette.Indigo30,
    onSecondaryContainer = Palette.Indigo90,
    tertiary = Palette.Saffron80,
    onTertiary = Palette.Saffron20,
    tertiaryContainer = Palette.Saffron30,
    onTertiaryContainer = Palette.Saffron90,
    error = Palette.Red80,
    onError = Palette.Red20,
    errorContainer = Palette.Red30,
    onErrorContainer = Palette.Red90,
    background = Color(0xFF1A110F),
    onBackground = Color(0xFFF1DFDA),
    surface = Color(0xFF1A110F),
    onSurface = Color(0xFFF1DFDA),
    surfaceVariant = Color(0xFF53433F),
    onSurfaceVariant = Color(0xFFD8C2BC),
    surfaceTint = Palette.Coral80,
    inverseSurface = Color(0xFFF1DFDA),
    inverseOnSurface = Color(0xFF392E2B),
    outline = Color(0xFFA08C87),
    outlineVariant = Color(0xFF53433F),
    scrim = Color.Black,
    surfaceBright = Color(0xFF423734),
    surfaceDim = Color(0xFF1A110F),
    surfaceContainerLowest = Color(0xFF140C0A),
    surfaceContainerLow = Color(0xFF231917),
    surfaceContainer = Color(0xFF271D1B),
    surfaceContainerHigh = Color(0xFF322825),
    surfaceContainerHighest = Color(0xFF3D3230),
)

/** A foreground/background pair for one semantic meaning. */
@Immutable
data class ToneColors(val content: Color, val container: Color)

/** Semantic colours that are not Material roles: money direction and the hero gradient. */
@Immutable
data class SplitSmartColors(
    val owed: ToneColors,
    val owe: ToneColors,
    val heroGradient: Brush,
    val onHero: Color,
)

internal val LightSplitSmartColors = SplitSmartColors(
    owed = ToneColors(Palette.Green40, Palette.Green90),
    owe = ToneColors(Palette.Red40, Palette.Red90),
    heroGradient = Brush.linearGradient(listOf(Palette.Coral40, Palette.Coral50, Palette.Saffron60)),
    onHero = Color.White,
)

internal val DarkSplitSmartColors = SplitSmartColors(
    owed = ToneColors(Palette.Green80, Palette.Green30),
    owe = ToneColors(Palette.Red80, Palette.Red30),
    heroGradient = Brush.linearGradient(listOf(Palette.Coral30, Color(0xFFA8461A), Color(0xFF9A5A12))),
    onHero = Color.White,
)

internal val LocalSplitSmartColors = staticCompositionLocalOf { LightSplitSmartColors }
