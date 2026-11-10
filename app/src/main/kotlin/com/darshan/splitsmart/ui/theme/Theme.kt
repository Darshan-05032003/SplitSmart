package com.darshan.splitsmart.ui.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.darshan.splitsmart.ui.designsystem.effects.AndroidHaptics
import com.darshan.splitsmart.ui.designsystem.effects.GuardedHaptics
import com.darshan.splitsmart.ui.designsystem.effects.GuardedUiSound
import com.darshan.splitsmart.ui.designsystem.effects.Haptics
import com.darshan.splitsmart.ui.designsystem.effects.LocalHaptics
import com.darshan.splitsmart.ui.designsystem.effects.LocalUiSound
import com.darshan.splitsmart.ui.designsystem.effects.NoOpUiSound
import com.darshan.splitsmart.ui.designsystem.effects.ThemeMode
import com.darshan.splitsmart.ui.designsystem.effects.UiSettings
import com.darshan.splitsmart.ui.designsystem.effects.UiSound
import com.darshan.splitsmart.ui.designsystem.motion.LocalMotion
import com.darshan.splitsmart.ui.designsystem.motion.SplitSmartMotion
import com.darshan.splitsmart.ui.designsystem.motion.resolveMotionLevel
import com.darshan.splitsmart.ui.designsystem.tokens.DefaultSplitSmartType
import com.darshan.splitsmart.ui.designsystem.tokens.MarigoldColors
import com.darshan.splitsmart.ui.designsystem.tokens.MarigoldDark
import com.darshan.splitsmart.ui.designsystem.tokens.MarigoldLight
import com.darshan.splitsmart.ui.designsystem.tokens.Radii
import com.darshan.splitsmart.ui.designsystem.tokens.SplitSmartType
import com.darshan.splitsmart.ui.designsystem.tokens.toMaterialTypography

private val MarigoldShapes = Shapes(
    extraSmall = Radii.smallShape,
    small = Radii.smallShape,
    medium = Radii.mediumShape,
    large = Radii.mediumShape,
    extraLarge = Radii.largeShape,
)

val LocalMarigoldColors = staticCompositionLocalOf { MarigoldLight }
val LocalSplitSmartType = staticCompositionLocalOf { DefaultSplitSmartType }

/** Resolves the theme setting against the system, for the theme and for the system bars. */
@Composable
fun UiSettings.isDark(): Boolean = when (theme) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

/** App entry point: everything comes from the stored UI settings. */
@Composable
fun SplitSmartTheme(
    settings: UiSettings,
    haptics: Haptics? = null,
    sound: UiSound = NoOpUiSound,
    content: @Composable () -> Unit,
) {
    SplitSmartTheme(
        darkTheme = settings.isDark(),
        reduceMotion = settings.reduceMotion,
        hapticsEnabled = settings.hapticsEnabled,
        soundEnabled = settings.soundEnabled,
        haptics = haptics,
        sound = sound,
        content = content,
    )
}

/**
 * The one theme. Dynamic colour is deliberately off so SplitSmart looks the same on every phone.
 *
 * Provides the Marigold Mela tokens ([SplitSmartTheme.colors], [SplitSmartTheme.type]), [LocalMotion] (reduced when the
 * in-app setting is on or the system animator scale is 0), [LocalHaptics] and [LocalUiSound] behind the in-app switches, and
 * the old semantic roles as [SplitSmartTheme.legacy] (derived from Marigold). Material slots carry Marigold too, so screens that
 * still use Material widgets match the palette. Tests pass a fake [haptics] and set [reduceMotion] directly.
 */
@Composable
fun SplitSmartTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reduceMotion: Boolean = false,
    hapticsEnabled: Boolean = true,
    soundEnabled: Boolean = false,
    haptics: Haptics? = null,
    sound: UiSound = NoOpUiSound,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) MarigoldDark else MarigoldLight
    val animatorScale = rememberSystemAnimatorScale()
    val level = resolveMotionLevel(reduceMotion, animatorScale)
    val motion = remember(level) { SplitSmartMotion(level) }
    val view = LocalView.current
    val hapticsOn by rememberUpdatedState(hapticsEnabled)
    val soundOn by rememberUpdatedState(soundEnabled)
    val guardedHaptics = remember(view, haptics) { GuardedHaptics(haptics ?: AndroidHaptics(view)) { hapticsOn } }
    val guardedSound = remember(sound) { GuardedUiSound(sound) { soundOn } }
    val colorScheme = remember(colors) { colors.toColorScheme() }
    val legacy = remember(colors) { colors.toLegacyColors() }
    val typography = remember { DefaultSplitSmartType.toMaterialTypography() }

    CompositionLocalProvider(
        LocalMarigoldColors provides colors,
        LocalSplitSmartType provides DefaultSplitSmartType,
        LocalSplitSmartColors provides legacy,
        LocalMotion provides motion,
        LocalHaptics provides guardedHaptics,
        LocalUiSound provides guardedSound,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = MarigoldShapes,
            content = content,
        )
    }
}

/**
 * Marigold Mela onto Material slots, for Material widgets in screens not rebuilt on the design system yet.
 *
 * Material draws `primary` as text and focus colour (text buttons, focused field labels, cursors), and marigold on cream is only
 * 1.9:1. So `primary` is the accent (aubergine in light, marigold in dark: the colour the design system uses for text actions) and
 * marigold moves to `primaryContainer`, which filled FABs and tonal surfaces use with aubergine on top. Every text pair is
 * checked in `MaterialSlotsContrastTest`.
 */
internal fun MarigoldColors.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = primary,
        onPrimaryContainer = onPrimary,
        inversePrimary = accent,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = info.container,
        onSecondaryContainer = info.content,
        tertiary = success.content,
        onTertiary = surface,
        tertiaryContainer = success.container,
        onTertiaryContainer = success.content,
        error = failed.content,
        onError = surface,
        errorContainer = failed.container,
        onErrorContainer = failed.content,
        background = bg,
        onBackground = text,
        surface = bg,
        onSurface = text,
        surfaceVariant = raised,
        onSurfaceVariant = textSecondary,
        surfaceTint = Color.Transparent,
        inverseSurface = snackbar,
        inverseOnSurface = onSnackbar,
        outline = outline,
        outlineVariant = hairline,
        scrim = scrim,
        surfaceBright = surface,
        surfaceDim = bg,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = raised,
        surfaceContainerHighest = raised,
    )
}

/** Reads the system animator scale and follows changes while the theme is on screen. */
@Composable
private fun rememberSystemAnimatorScale(): Float {
    val resolver = LocalContext.current.applicationContext.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    var scale by remember(resolver) { mutableFloatStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scale = read()
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return scale
}

/** Theme-dependent tokens. Static ones live in `SplitSmartTokens` (`Spacing`, `Radii`, `Depth`, `Sizes`). */
object SplitSmartTheme {
    /** Marigold Mela colours, e.g. `SplitSmartTheme.colors.primary`. */
    val colors: MarigoldColors
        @Composable @ReadOnlyComposable get() = LocalMarigoldColors.current

    val type: SplitSmartType
        @Composable @ReadOnlyComposable get() = LocalSplitSmartType.current

    val motion: SplitSmartMotion
        @Composable @ReadOnlyComposable get() = LocalMotion.current

    val haptics: Haptics
        @Composable @ReadOnlyComposable get() = LocalHaptics.current

    /** Old semantic roles (hero card, owed/owe) for screens not rebuilt yet, now drawn from Marigold. Removed once rows 10-13 are done. */
    val legacy: SplitSmartColors
        @Composable @ReadOnlyComposable get() = LocalSplitSmartColors.current
}

/** Standard screen padding of the pre-overhaul screens. New code uses `Spacing.screen`. */
val ScreenPadding = 16.dp
