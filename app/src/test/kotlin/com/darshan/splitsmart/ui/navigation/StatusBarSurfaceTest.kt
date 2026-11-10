package com.darshan.splitsmart.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.ui.designsystem.commitState
import com.darshan.splitsmart.ui.designsystem.setDs
import com.darshan.splitsmart.ui.designsystem.tokens.MarigoldLight
import com.darshan.splitsmart.ui.designsystem.motion.LocalMotion
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The status bar is handed to a new surface only once it has finished arriving (the PayFlow device bug, checked on the JVM). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class StatusBarSurfaceTest {
    @get:Rule val rule = createComposeRule()

    private val controller = StatusBarController()
    private var screen by mutableIntStateOf(0)

    private fun show(reduceMotion: Boolean) {
        rule.mainClock.autoAdvance = false
        rule.setDs(reduceMotion = reduceMotion) {
            val motion = LocalMotion.current
            CompositionLocalProvider(LocalStatusBarController provides controller) {
                AnimatedContent(screen, transitionSpec = { motion.fadeThrough() }, label = "t") { target ->
                    // Screen 0 is the cream page, screen 1 an aubergine full-bleed surface (a camera, say).
                    StatusBarSurface(if (target == 0) MarigoldLight.bg else MarigoldLight.bar, settled = hasArrived())
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
    }

    private fun darkIcons() = rule.runOnIdle { controller.darkIcons(themeDark = false) }

    @Test
    fun `the icons flip only when the new surface has arrived, not when it starts`() {
        show(reduceMotion = false)
        assertThat(darkIcons()).isTrue()
        commitState { screen = 1 }
        rule.mainClock.advanceTimeBy(48)
        assertThat(darkIcons()).isTrue()
        rule.mainClock.advanceTimeBy(1_000)
        assertThat(darkIcons()).isFalse()
        // And back: the cream page takes the bar again once it is back.
        commitState { screen = 0 }
        rule.mainClock.advanceTimeBy(48)
        assertThat(darkIcons()).isFalse()
        rule.mainClock.advanceTimeBy(1_000)
        assertThat(darkIcons()).isTrue()
    }

    @Test
    fun `under reduced motion the hand-over still waits for the short fade`() {
        show(reduceMotion = true)
        commitState { screen = 1 }
        rule.mainClock.advanceTimeBy(32)
        assertThat(darkIcons()).isTrue()
        rule.mainClock.advanceTimeBy(1_000)
        assertThat(darkIcons()).isFalse()
    }

    @Test
    fun `surfaces that leave let go of the bar`() {
        show(reduceMotion = false)
        commitState { screen = 1 }
        rule.mainClock.advanceTimeBy(2_000)
        assertThat(rule.runOnIdle { controller.size }).isEqualTo(1)
    }
}
