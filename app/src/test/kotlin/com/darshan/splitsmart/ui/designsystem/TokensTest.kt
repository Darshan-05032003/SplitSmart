package com.darshan.splitsmart.ui.designsystem

import android.view.HapticFeedbackConstants
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darshan.splitsmart.ui.designsystem.effects.GuardedHaptics
import com.darshan.splitsmart.ui.designsystem.effects.GuardedUiSound
import com.darshan.splitsmart.ui.designsystem.effects.HapticEvent
import com.darshan.splitsmart.ui.designsystem.effects.UiEffect
import com.darshan.splitsmart.ui.designsystem.effects.UiEffectChannel
import com.darshan.splitsmart.ui.designsystem.effects.UiSoundEvent
import com.darshan.splitsmart.ui.designsystem.effects.dispatchTo
import com.darshan.splitsmart.ui.designsystem.effects.feedbackConstantFor
import com.darshan.splitsmart.ui.designsystem.motion.MotionDurations
import com.darshan.splitsmart.ui.designsystem.motion.MotionLevel
import com.darshan.splitsmart.ui.designsystem.motion.MotionPlan
import com.darshan.splitsmart.ui.designsystem.motion.MotionRole
import com.darshan.splitsmart.ui.designsystem.motion.MotionSprings
import com.darshan.splitsmart.ui.designsystem.motion.MotionToken
import com.darshan.splitsmart.ui.designsystem.motion.SplitSmartMotion
import com.darshan.splitsmart.ui.designsystem.motion.resolveMotionLevel
import com.darshan.splitsmart.ui.designsystem.tokens.ContrastPair
import com.darshan.splitsmart.ui.designsystem.tokens.MarigoldDark
import com.darshan.splitsmart.ui.designsystem.tokens.MarigoldLight
import com.darshan.splitsmart.ui.designsystem.tokens.Radii
import com.darshan.splitsmart.ui.designsystem.tokens.Sizes
import com.darshan.splitsmart.ui.designsystem.tokens.Spacing
import com.darshan.splitsmart.ui.designsystem.tokens.contrastRatio
import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ContrastTest {
    @Test
    fun `the WCAG formula matches known reference ratios`() {
        assertThat(contrastRatio(Color.Black, Color.White)).isWithin(0.01).of(21.0)
        assertThat(contrastRatio(Color.White, Color.White)).isWithin(0.001).of(1.0)
        // From the design direction's table.
        assertThat(contrastRatio(MarigoldLight.text, MarigoldLight.bg)).isWithin(0.01).of(15.92)
        assertThat(contrastRatio(MarigoldDark.onPrimary, MarigoldDark.primary)).isWithin(0.01).of(10.33)
    }

    @Test
    fun `every shipped text pair passes WCAG AA in both themes`() {
        for (colors in listOf(MarigoldLight, MarigoldDark)) {
            val pairs = colors.textPairs()
            assertThat(pairs.size).isAtLeast(30)
            for (pair in pairs) {
                assertWithMessage("${if (colors.isDark) "dark" else "light"}: ${pair.name} = ${"%.2f".format(pair.ratio)}")
                    .that(pair.ratio).isAtLeast(ContrastPair.TEXT_MINIMUM)
            }
        }
    }

    @Test
    fun `control boundaries and the selection blob pass 3 to 1`() {
        for (colors in listOf(MarigoldLight, MarigoldDark)) {
            for (pair in colors.uiPairs()) {
                assertWithMessage("${pair.name} = ${"%.2f".format(pair.ratio)}").that(pair.ratio).isAtLeast(ContrastPair.UI_MINIMUM)
            }
        }
    }

    @Test
    fun `the palette is the chosen Marigold Mela direction`() {
        assertThat(MarigoldLight.primary).isEqualTo(Color(0xFFFFB627))
        assertThat(MarigoldLight.text).isEqualTo(Color(0xFF2B1238))
        assertThat(MarigoldLight.bg).isEqualTo(Color(0xFFFFF8E8))
        assertThat(MarigoldDark.bg).isEqualTo(Color(0xFF1C0F26))
        assertThat(MarigoldDark.primary).isEqualTo(Color(0xFFFFC043))
        assertThat(MarigoldLight.success.content).isEqualTo(Color(0xFF0B7A5F))
        assertThat(MarigoldLight.failed.content).isEqualTo(Color(0xFFC22F27))
        assertThat(MarigoldDark.info.content).isEqualTo(Color(0xFFE0B3F2))
    }
}

class TokensTest {
    @Test
    fun `radii are the three from the direction`() {
        assertThat(listOf(Radii.small, Radii.medium, Radii.large)).containsExactly(16.dp, 24.dp, 32.dp).inOrder()
    }

    @Test
    fun `spacing sits on the 4dp grid and grows`() {
        val steps = listOf(Spacing.xxs, Spacing.xs, Spacing.sm, Spacing.md, Spacing.lg, Spacing.xl, Spacing.xxl, Spacing.xxxl, Spacing.huge, Spacing.giant)
        steps.forEach { assertWithMessage("$it").that(it.value % 4f).isEqualTo(0f) }
        assertThat(steps).isInStrictOrder(compareBy<Dp> { it.value })
        assertThat(Spacing.screen.value % 8f).isEqualTo(0f)
    }

    @Test
    fun `everything tappable is at least 48dp`() {
        for (size in listOf(Sizes.touchTarget, Sizes.buttonHeight, Sizes.buttonHeightCompact, Sizes.fieldHeight, Sizes.fab, Sizes.listRowCompact, Sizes.bottomBarHeight)) {
            assertThat(size.value).isAtLeast(48f)
        }
    }

    @Test
    fun `motion durations stay inside the brief's ranges`() {
        assertThat(MotionDurations.Press).isIn(90..150)
        assertThat(MotionDurations.Micro).isIn(90..150)
        assertThat(MotionDurations.Small).isIn(200..300)
        assertThat(MotionDurations.Screen).isIn(350..500)
        assertThat(MotionDurations.Stagger).isIn(30..50)
    }

    @Test
    fun `springs match the direction's personality`() {
        assertThat(MotionSprings.Snappy.stiffness).isEqualTo(1400f)
        assertThat(MotionSprings.Snappy.dampingRatio).isEqualTo(0.85f)
        assertThat(MotionSprings.Bouncy.stiffness).isEqualTo(600f)
        assertThat(MotionSprings.Bouncy.dampingRatio).isEqualTo(0.55f)
        assertThat(MotionSprings.Gentle.stiffness).isEqualTo(260f)
        assertThat(MotionSprings.Gentle.dampingRatio).isEqualTo(0.8f)
    }
}

class MotionSchemeTest {
    @Test
    fun `reduced when the setting is on or the animator scale is zero or broken`() {
        assertThat(resolveMotionLevel(false, 1f)).isEqualTo(MotionLevel.Full)
        assertThat(resolveMotionLevel(false, 3f)).isEqualTo(MotionLevel.Full)
        assertThat(resolveMotionLevel(true, 1f)).isEqualTo(MotionLevel.Reduced)
        assertThat(resolveMotionLevel(false, 0f)).isEqualTo(MotionLevel.Reduced)
        assertThat(resolveMotionLevel(false, Float.NaN)).isEqualTo(MotionLevel.Reduced)
        assertThat(resolveMotionLevel(false, -1f)).isEqualTo(MotionLevel.Reduced)
    }

    @Test
    fun `full motion plays every token as designed`() {
        val motion = SplitSmartMotion(MotionLevel.Full)
        MotionToken.entries.forEach { assertThat(motion.plan(it)).isEqualTo(it.fullPlan) }
        assertThat(motion.decorativeEnabled).isTrue()
    }

    @Test
    fun `reduced motion drops decoration, shortens feedback and fades the rest`() {
        val motion = SplitSmartMotion(MotionLevel.Reduced)
        assertThat(motion.decorativeEnabled).isFalse()
        for (token in MotionToken.entries) {
            val plan = motion.plan(token)
            when (token.role) {
                MotionRole.Decorative -> assertThat(plan).isEqualTo(MotionPlan.None)
                MotionRole.Feedback -> assertThat((plan as MotionPlan.Tween).durationMs).isAtMost(MotionDurations.Press)
                MotionRole.Essential -> assertThat(plan).isEqualTo(MotionPlan.Fade(MotionDurations.ReducedFade))
            }
            assertWithMessage("$token never springs when reduced").that(plan).isNotInstanceOf(MotionPlan.Spring::class.java)
        }
    }

    @Test
    fun `stagger grows per item, caps, and vanishes when reduced`() {
        val full = SplitSmartMotion(MotionLevel.Full)
        assertThat(full.staggerDelayMs(0)).isEqualTo(0)
        assertThat(full.staggerDelayMs(2)).isEqualTo(2 * MotionDurations.Stagger)
        assertThat(full.staggerDelayMs(10_000)).isEqualTo(MotionDurations.StaggerMax)
        assertThat(full.staggerDelayMs(-4)).isEqualTo(0)
        assertThat(SplitSmartMotion(MotionLevel.Reduced).staggerDelayMs(5)).isEqualTo(0)
    }

    @Test
    fun `the bounce tokens overshoot and the press token barely does`() {
        val bounce = (MotionToken.Bounce.fullPlan as MotionPlan.Spring).spring
        val press = (MotionToken.Press.fullPlan as MotionPlan.Spring).spring
        assertThat(bounce.dampingRatio).isLessThan(0.7f)
        assertThat(press.dampingRatio).isGreaterThan(0.8f)
        assertThat(MotionToken.Celebrate.role).isEqualTo(MotionRole.Decorative)
        assertThat(MotionToken.Bump.role).isEqualTo(MotionRole.Essential)
    }
}

class EffectsTest {
    @Test
    fun `haptic constants per event and API level`() {
        assertThat(feedbackConstantFor(HapticEvent.Tick, 35)).isEqualTo(HapticFeedbackConstants.CLOCK_TICK)
        assertThat(feedbackConstantFor(HapticEvent.Light, 35)).isEqualTo(HapticFeedbackConstants.KEYBOARD_TAP)
        assertThat(feedbackConstantFor(HapticEvent.Confirm, 30)).isEqualTo(HapticFeedbackConstants.CONFIRM)
        assertThat(feedbackConstantFor(HapticEvent.Confirm, 29)).isEqualTo(HapticFeedbackConstants.VIRTUAL_KEY)
        assertThat(feedbackConstantFor(HapticEvent.Reject, 30)).isEqualTo(HapticFeedbackConstants.REJECT)
        assertThat(feedbackConstantFor(HapticEvent.Reject, 26)).isEqualTo(HapticFeedbackConstants.LONG_PRESS)
        assertThat(feedbackConstantFor(HapticEvent.Heavy, 35)).isEqualTo(HapticFeedbackConstants.LONG_PRESS)
    }

    @Test
    fun `the haptics switch is read on every event`() {
        val fake = FakeHaptics()
        var on = true
        val guarded = GuardedHaptics(fake) { on }
        guarded.perform(HapticEvent.Confirm)
        on = false
        guarded.perform(HapticEvent.Reject)
        on = true
        guarded.perform(HapticEvent.Tick)
        assertThat(fake.events).containsExactly(HapticEvent.Confirm, HapticEvent.Tick).inOrder()
    }

    @Test
    fun `sound stays off unless enabled`() {
        val fake = FakeUiSound()
        var on = false
        val guarded = GuardedUiSound(fake) { on }
        guarded.play(UiSoundEvent.Settled)
        on = true
        guarded.play(UiSoundEvent.Settled)
        assertThat(fake.events).containsExactly(UiSoundEvent.Settled)
    }

    @Test
    fun `effects route to the right channel`() {
        val haptics = FakeHaptics()
        val sound = FakeUiSound()
        val messages = mutableListOf<UiEffect.Message>()
        val others = mutableListOf<UiEffect>()
        val navigate = object : UiEffect {}
        listOf(UiEffect.Haptic(HapticEvent.Confirm), UiEffect.Sound(UiSoundEvent.Settled), UiEffect.Message("Group archived", "📦", undoable = true), navigate)
            .forEach { it.dispatchTo(haptics, sound, onMessage = { m -> messages += m }, onOther = { o -> others += o }) }
        assertThat(haptics.events).containsExactly(HapticEvent.Confirm)
        assertThat(sound.events).containsExactly(UiSoundEvent.Settled)
        assertThat(messages.single().undoable).isTrue()
        assertThat(others).containsExactly(navigate)
    }

    @Test
    fun `the channel keeps effects for a late collector and drops the oldest in a flood`() = runTest {
        val channel = UiEffectChannel(capacity = 4)
        channel.send(UiEffect.Haptic(HapticEvent.Light))
        assertThat(channel.effects.first()).isEqualTo(UiEffect.Haptic(HapticEvent.Light))
        repeat(1_000) { channel.send(UiEffect.Message("m$it")) }
        val kept = channel.effects.take(4).toList().map { (it as UiEffect.Message).text }
        assertThat(kept).containsExactly("m996", "m997", "m998", "m999").inOrder()
    }
}
