package com.darshan.splitsmart.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.darshan.splitsmart.ui.designsystem.tokens.ContrastPair
import com.darshan.splitsmart.ui.designsystem.tokens.MarigoldDark
import com.darshan.splitsmart.ui.designsystem.tokens.MarigoldLight
import com.darshan.splitsmart.ui.designsystem.tokens.contrastRatio
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * Every screen is on the design system now, but Material pieces still read the Material slots (text selection, IME handles, any
 * future Material widget), and the aubergine heroes that replaced the old hero card write in the night palette. Every pair they
 * put text on must pass WCAG AA in both themes.
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
    fun `heroes that replaced the old hero card stay readable on the aubergine, including their secondary labels and the stamp`() {
        val ink = com.darshan.splitsmart.ui.groups.HeroPalette
        for ((mode, colors) in themes) {
            val hero = colors.bar
            assertWithMessage("$mode text on hero").that(contrastRatio(ink.text, hero)).isAtLeast(ContrastPair.TEXT_MINIMUM)
            assertWithMessage("$mode secondary on hero").that(contrastRatio(ink.textSecondary, hero)).isAtLeast(ContrastPair.TEXT_MINIMUM)
            assertWithMessage("$mode amount on hero").that(contrastRatio(ink.primary, hero)).isAtLeast(ContrastPair.TEXT_MINIMUM)
            assertWithMessage("$mode stamp on hero").that(contrastRatio(ink.success.content, hero)).isAtLeast(ContrastPair.TEXT_MINIMUM)
            assertWithMessage("$mode hero button").that(contrastRatio(ink.onPrimary, ink.primary)).isAtLeast(ContrastPair.TEXT_MINIMUM)
            for (page in listOf(colors.bg, colors.surface)) {
                assertWithMessage("$mode owed on page").that(contrastRatio(colors.success.content, page)).isAtLeast(ContrastPair.TEXT_MINIMUM)
                assertWithMessage("$mode owe on page").that(contrastRatio(colors.failed.content, page)).isAtLeast(ContrastPair.TEXT_MINIMUM)
            }
            assertWithMessage("$mode owed chip").that(contrastRatio(colors.success.content, colors.success.container)).isAtLeast(ContrastPair.TEXT_MINIMUM)
            assertWithMessage("$mode owe chip").that(contrastRatio(colors.failed.content, colors.failed.container)).isAtLeast(ContrastPair.TEXT_MINIMUM)
        }
    }

    @Test
    fun `the old coral is gone`() {
        val coral = Color(0xFFB5401F)
        for ((_, colors) in themes) {
            val scheme = colors.toColorScheme()
            assertWithMessage("primary").that(scheme.primary).isNotEqualTo(coral)
            assertWithMessage("hero").that(colors.bar).isNotEqualTo(coral)
        }
    }
}
