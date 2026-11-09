package com.darshan.splitsmart.ui.designsystem.catalog

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.ui.designsystem.captureNode
import com.darshan.splitsmart.ui.theme.MaterialLook
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * One baseline per catalog section per theme, plus 1.3× font and reduced-motion variants of the sections where text size or
 * motion changes the result. A new component is covered by adding it to a section. The clock is paused before content is set,
 * so infinite ambient motion (loader, shimmer, bobbing art) is frozen at a repeatable frame.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h2600dp-xhdpi")
class DesignCatalogScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun capture(key: String, dark: Boolean, fontScale: Float = 1f, reduceMotion: Boolean = false) {
        val section = CatalogSections.first { it.key == key }
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                SplitSmartTheme(darkTheme = dark, reduceMotion = reduceMotion, materialLook = MaterialLook.Marigold) { CatalogSectionView(section) }
            }
        }
        rule.mainClock.advanceTimeBy(SETTLE_MS)
        val suffix = buildString {
            if (fontScale != 1f) append("_large_font")
            if (reduceMotion) append("_reduced")
        }
        rule.onNodeWithTag("catalog:$key").captureNode("ds_${key}_${if (dark) "dark" else "light"}$suffix")
    }

    @Test fun colors_light() = capture("colors", false)
    @Test fun colors_dark() = capture("colors", true)
    @Test fun type_light() = capture("type", false)
    @Test fun type_dark() = capture("type", true)
    @Test fun shape_light() = capture("shape", false)
    @Test fun shape_dark() = capture("shape", true)
    @Test fun motion_light() = capture("motion", false)
    @Test fun motion_dark() = capture("motion", true)
    @Test fun buttons_light() = capture("buttons", false)
    @Test fun buttons_dark() = capture("buttons", true)
    @Test fun icon_buttons_light() = capture("icon_buttons", false)
    @Test fun icon_buttons_dark() = capture("icon_buttons", true)
    @Test fun inputs_light() = capture("inputs", false)
    @Test fun inputs_dark() = capture("inputs", true)
    @Test fun avatars_light() = capture("avatars", false)
    @Test fun avatars_dark() = capture("avatars", true)
    @Test fun amounts_light() = capture("amounts", false)
    @Test fun amounts_dark() = capture("amounts", true)
    @Test fun counters_light() = capture("counters", false)
    @Test fun counters_dark() = capture("counters", true)
    @Test fun chips_light() = capture("chips", false)
    @Test fun chips_dark() = capture("chips", true)
    @Test fun status_light() = capture("status", false)
    @Test fun status_dark() = capture("status", true)
    @Test fun structure_light() = capture("structure", false)
    @Test fun structure_dark() = capture("structure", true)
    @Test fun states_light() = capture("states", false)
    @Test fun states_dark() = capture("states", true)
    @Test fun illustrations_light() = capture("illustrations", false)
    @Test fun illustrations_dark() = capture("illustrations", true)
    @Test fun skeleton_light() = capture("skeleton", false)
    @Test fun skeleton_dark() = capture("skeleton", true)
    @Test fun loaders_light() = capture("loaders", false)
    @Test fun loaders_dark() = capture("loaders", true)
    @Test fun sheet_light() = capture("sheet", false)
    @Test fun sheet_dark() = capture("sheet", true)
    @Test fun snackbar_light() = capture("snackbar", false)
    @Test fun snackbar_dark() = capture("snackbar", true)
    @Test fun bottom_bar_light() = capture("bottom_bar", false)
    @Test fun bottom_bar_dark() = capture("bottom_bar", true)

    @Test fun buttons_large_font() = capture("buttons", false, LARGE)
    @Test fun inputs_large_font() = capture("inputs", true, LARGE)
    @Test fun amounts_large_font() = capture("amounts", false, LARGE)
    @Test fun chips_large_font() = capture("chips", true, LARGE)
    @Test fun structure_large_font() = capture("structure", false, LARGE)
    @Test fun states_large_font() = capture("states", true, LARGE)
    @Test fun bottom_bar_large_font() = capture("bottom_bar", false, LARGE)
    @Test fun snackbar_large_font() = capture("snackbar", true, LARGE)

    @Test fun loaders_reduced() = capture("loaders", false, reduceMotion = true)
    @Test fun skeleton_reduced() = capture("skeleton", true, reduceMotion = true)

    private companion object {
        const val SETTLE_MS = 3_000L
        const val LARGE = 1.3f
    }
}
