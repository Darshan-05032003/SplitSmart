package com.darshan.splitsmart.ui.groups

import com.darshan.splitsmart.ui.designsystem.tokens.ContrastPair
import com.darshan.splitsmart.ui.designsystem.tokens.MarigoldDark
import com.darshan.splitsmart.ui.designsystem.tokens.MarigoldLight
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** The hero sits on the bar colour in both themes and writes in the night palette: every pair it draws must pass WCAG AA. */
class GroupsHeroContrastTest {
    @Test
    fun `every text and money colour on the hero passes AA in both themes`() {
        for (page in listOf(MarigoldLight, MarigoldDark)) {
            val surface = page.bar
            val ink = HeroPalette
            val pairs = listOf(
                ContrastPair("text", ink.text, surface),
                ContrastPair("textSecondary", ink.textSecondary, surface),
                ContrastPair("accent (name prompt)", ink.accent, surface),
            ) + HeroMood.entries.map { ContrastPair("amount $it", heroTint(it), surface) } + listOf(
                ContrastPair("owed pill", ink.success.content, ink.success.container),
                ContrastPair("owe pill", ink.failed.content, ink.failed.container),
            )
            for (pair in pairs) {
                assertWithMessage("${if (page.isDark) "dark" else "light"} ${pair.name}: ${"%.2f".format(pair.ratio)}:1")
                    .that(pair.ratio).isAtLeast(ContrastPair.TEXT_MINIMUM)
            }
        }
    }
}
