package com.darshan.splitsmart.ui.scan

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.darshan.splitsmart.ui.designsystem.tokens.ContrastPair
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * The camera draws over a live picture it does not control. Its marks must read over the worst case: a white receipt in full
 * light. Outside the window the picture is dimmed by the scrim; inside, text outlines sit on a night halo.
 */
class ScanContrastTest {
    private val p = CameraPalette

    @Test
    fun `marks over the picture stay visible on a white receipt`() {
        val dimmedWhite = p.scrim.compositeOver(Color.White)
        val pairs = listOf(
            ContrastPair("corners on the dimmed picture", p.primary, dimmedWhite, ContrastPair.UI_MINIMUM),
            ContrastPair("outline on its halo", p.primary, p.bg, ContrastPair.UI_MINIMUM),
            ContrastPair("halo on white paper", p.bg, Color.White, ContrastPair.UI_MINIMUM),
            ContrastPair("hint text on its pill", p.text, p.surface),
            ContrastPair("found hint on marigold", p.onPrimary, p.primary),
            ContrastPair("reading title on the dimmed picture", p.text, p.scrim.compositeOver(Color.White)),
            ContrastPair("gallery caption on the dimmed picture", p.textSecondary, p.scrim.compositeOver(Color.Black)),
        )
        for (pair in pairs) {
            assertWithMessage("${pair.name}: ${"%.2f".format(pair.ratio)}:1").that(pair.ratio).isAtLeast(pair.minimum)
        }
    }
}
