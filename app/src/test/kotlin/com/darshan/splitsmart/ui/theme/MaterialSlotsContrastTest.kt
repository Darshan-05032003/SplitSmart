package com.darshan.splitsmart.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.darshan.splitsmart.ui.designsystem.tokens.ContrastPair
import com.darshan.splitsmart.ui.designsystem.tokens.MarigoldDark
import com.darshan.splitsmart.ui.designsystem.tokens.MarigoldLight
import com.darshan.splitsmart.ui.designsystem.tokens.contrastRatio
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * The screens that are not rebuilt yet draw with Material widgets and `SplitSmartTheme.legacy`. Since the shell switched the app
 * to Marigold, those read Marigold colours: every pair they put text on must still pass WCAG AA in both themes.
 */
class MaterialSlotsContrastTest {
    private val themes = listOf("light" to MarigoldLight, "dark" to MarigoldDark)

    private fun ColorScheme.textPairs(): List<ContrastPair> = buildList {
        val pages = listOf(
            "background" to background, "surface" to surface, "surfaceContainerLowest" to surfaceContainerLowest,
            "surfaceContainerLow" to surfaceContainerLow, "surfaceContainer" to surfaceContainer,
            "surfaceContainerHigh" to surfaceContainerHigh, "surfaceContainerHighest" to surfaceContainerHighest,
            "surfaceVariant" to surfaceVariant,
        )
        for ((name, page) in pages) {
            add(ContrastPair("onSurface on $name", onSurface, page))
            add(ContrastPair("onSurfaceVariant on $name", onSurfaceVariant, page))
            // Text buttons, focused labels and links draw in primary.
            add(ContrastPair("primary on $name", primary, page))
            add(ContrastPair("secondary on $name", secondary, page))
            add(ContrastPair("error on $name", error, page))
        }
        add(ContrastPair("onBackground on background", onBackground, background))
        add(ContrastPair("onPrimary on primary", onPrimary, primary))
        add(ContrastPair("onPrimaryContainer on primaryContainer", onPrimaryContainer, primaryContainer))
        add(ContrastPair("onSecondary on secondary", onSecondary, secondary))
        add(ContrastPair("onSecondaryContainer on secondaryContainer", onSecondaryContainer, secondaryContainer))
        add(ContrastPair("onTertiary on tertiary", onTertiary, tertiary))
        add(ContrastPair("onTertiaryContainer on tertiaryContainer", onTertiaryContainer, tertiaryContainer))
        add(ContrastPair("tertiary on surface", tertiary, surface))
        add(ContrastPair("onError on error", onError, error))
        add(ContrastPair("onErrorContainer on errorContainer", onErrorContainer, errorContainer))
        add(ContrastPair("inverseOnSurface on inverseSurface", inverseOnSurface, inverseSurface))
    }

    @Test
    fun `every Material text pair passes AA in both themes`() {
        for ((mode, colors) in themes) {
            for (pair in colors.toColorScheme().textPairs()) {
                assertWithMessage("$mode ${pair.name}: ${"%.2f".format(pair.ratio)}:1").that(pair.ratio).isAtLeast(ContrastPair.TEXT_MINIMUM)
            }
        }
    }

    @Test
    fun `old hero cards stay readable on the aubergine hero, including their 90 percent labels and the name prompt`() {
        for ((mode, colors) in themes) {
            val legacy = colors.toLegacyColors()
            for (stop in legacy.heroStops) {
                val label = legacy.onHero.copy(alpha = 0.9f).compositeOver(stop)
                val prompt = legacy.onHero.copy(alpha = 0.18f).compositeOver(stop)
                assertWithMessage("$mode onHero on $stop").that(contrastRatio(legacy.onHero, stop)).isAtLeast(ContrastPair.TEXT_MINIMUM)
                assertWithMessage("$mode 90 percent label on $stop").that(contrastRatio(label, stop)).isAtLeast(ContrastPair.TEXT_MINIMUM)
                assertWithMessage("$mode name prompt on $stop").that(contrastRatio(legacy.onHero, prompt)).isAtLeast(ContrastPair.TEXT_MINIMUM)
            }
            assertWithMessage("$mode hero button").that(contrastRatio(legacy.heroAction, legacy.onHero)).isAtLeast(ContrastPair.TEXT_MINIMUM)
            for (page in listOf(colors.bg, colors.surface)) {
                assertWithMessage("$mode owed on page").that(contrastRatio(legacy.owed.content, page)).isAtLeast(ContrastPair.TEXT_MINIMUM)
                assertWithMessage("$mode owe on page").that(contrastRatio(legacy.owe.content, page)).isAtLeast(ContrastPair.TEXT_MINIMUM)
            }
            assertWithMessage("$mode owed chip").that(contrastRatio(legacy.owed.content, legacy.owed.container)).isAtLeast(ContrastPair.TEXT_MINIMUM)
            assertWithMessage("$mode owe chip").that(contrastRatio(legacy.owe.content, legacy.owe.container)).isAtLeast(ContrastPair.TEXT_MINIMUM)
        }
    }

    @Test
    fun `the old coral is gone`() {
        val coral = Color(0xFFB5401F)
        for ((_, colors) in themes) {
            val scheme = colors.toColorScheme()
            assertWithMessage("primary").that(scheme.primary).isNotEqualTo(coral)
            assertWithMessage("hero").that(colors.toLegacyColors().heroStops).doesNotContain(coral)
        }
    }
}
