package com.darshan.splitsmart.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

internal val SplitSmartShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Brand theme. `dynamicColor` is deliberately not supported: the coral brand must look the same on every device. */
@Composable
fun SplitSmartTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSplitSmartColors provides if (darkTheme) DarkSplitSmartColors else LightSplitSmartColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = SplitSmartTypography,
            shapes = SplitSmartShapes,
            content = content,
        )
    }
}

/** Semantic brand colours, e.g. `SplitSmartTheme.colors.owed.content`. */
object SplitSmartTheme {
    val colors: SplitSmartColors
        @Composable @ReadOnlyComposable get() = LocalSplitSmartColors.current
}

/** Standard screen padding. */
val ScreenPadding = 16.dp
