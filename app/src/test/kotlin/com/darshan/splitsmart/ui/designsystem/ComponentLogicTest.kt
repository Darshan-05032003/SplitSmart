package com.darshan.splitsmart.ui.designsystem

import com.darshan.splitsmart.ui.designsystem.components.AmountDecimals
import com.darshan.splitsmart.ui.designsystem.components.AmountFormat
import com.darshan.splitsmart.ui.designsystem.components.AmountGlyph
import com.darshan.splitsmart.ui.designsystem.components.AmountSign
import com.darshan.splitsmart.ui.designsystem.components.AvatarLogic
import com.darshan.splitsmart.ui.designsystem.components.BlobLogic
import com.darshan.splitsmart.ui.designsystem.components.ClickGuard
import com.darshan.splitsmart.ui.designsystem.components.ConfettiPhysics
import com.darshan.splitsmart.ui.designsystem.components.CounterLogic
import com.darshan.splitsmart.ui.designsystem.components.DisplayText
import com.darshan.splitsmart.ui.designsystem.components.RollDirection
import com.darshan.splitsmart.ui.designsystem.components.RollingAmountLogic
import com.darshan.splitsmart.ui.designsystem.components.SheetPhysics
import com.darshan.splitsmart.ui.designsystem.components.SheetTarget
import com.darshan.splitsmart.ui.designsystem.components.StackLayout
import com.darshan.splitsmart.ui.designsystem.modifiers.ShakeMath
import com.darshan.splitsmart.ui.designsystem.modifiers.Squash
import com.darshan.splitsmart.ui.designsystem.modifiers.SquashMath
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.darshan.splitsmart.ui.designsystem.components.BottomBarLayout
import com.darshan.splitsmart.ui.designsystem.tokens.Spacing
import androidx.compose.ui.unit.dp
import org.junit.Test

class ClickGuardTest {
    @Test
    fun `one tap per window, then the next window opens`() {
        val guard = ClickGuard(windowMs = 500)
        assertThat(guard.tryAccept(1_000)).isTrue()
        assertThat(guard.tryAccept(1_000)).isFalse()
        assertThat(guard.tryAccept(1_499)).isFalse()
        assertThat(guard.tryAccept(1_500)).isTrue()
    }

    @Test
    fun `a thousand taps in the same instant are one action`() {
        val guard = ClickGuard()
        assertThat((1..1_000).count { guard.tryAccept(42) }).isEqualTo(1)
    }

    @Test
    fun `the first tap is accepted even at time zero`() {
        assertThat(ClickGuard().tryAccept(0)).isTrue()
    }

    @Test
    fun `a clock that goes backwards does not lock the button`() {
        val guard = ClickGuard(windowMs = 500)
        assertThat(guard.tryAccept(10_000)).isTrue()
        assertThat(guard.tryAccept(2_000)).isTrue()
    }
}

class DisplayTextTest {
    @Test
    fun `strips bidi overrides and control characters`() {
        assertThat(DisplayText.safe("Ra‮vi\u0000 ⁦Asha⁩")).isEqualTo("Ravi Asha")
    }

    @Test
    fun `collapses whitespace and trims`() {
        assertThat(DisplayText.safe("  Goa \n\t trip  ")).isEqualTo("Goa trip")
    }

    @Test
    fun `caps the length without splitting an emoji`() {
        val long = "🌼".repeat(200)
        val safe = DisplayText.safe(long)
        assertThat(safe.codePointCount(0, safe.length)).isAtMost(DisplayText.MAX_CHARS + 1)
        assertThat(safe.last()).isEqualTo('…')
        assertThat(Character.isHighSurrogate(safe[safe.length - 2])).isFalse()
    }

    @Test
    fun `keeps Devanagari and emoji`() {
        assertThat(DisplayText.safe("राहुल 🎉")).isEqualTo("राहुल 🎉")
    }
}

class AmountFormatTest {
    @Test
    fun `formats paise with the Indian grouping`() {
        assertThat(AmountFormat.format(123_456_789L)).isEqualTo("₹12,34,567.89")
        assertThat(AmountFormat.format(0)).isEqualTo("₹0.00")
        assertThat(AmountFormat.format(5)).isEqualTo("₹0.05")
        assertThat(AmountFormat.format(100_000)).isEqualTo("₹1,000.00")
    }

    @Test
    fun `the largest displayable amount is 99,99,99,999 rupees 99 paise`() {
        assertThat(AmountFormat.format(AmountFormat.MAX_DISPLAY_PAISE)).isEqualTo("₹99,99,99,999.99")
        assertThat(AmountFormat.format(Long.MAX_VALUE)).isEqualTo("₹99,99,99,999.99")
        assertThat(AmountFormat.format(Long.MIN_VALUE)).isEqualTo("−₹99,99,99,999.99")
    }

    @Test
    fun `signs use a real minus and plus only when asked`() {
        assertThat(AmountFormat.format(-60_000)).isEqualTo("−₹600.00")
        assertThat(AmountFormat.format(67_800, AmountSign.Always)).isEqualTo("+₹678.00")
        assertThat(AmountFormat.format(0, AmountSign.Always)).isEqualTo("₹0.00")
        assertThat(AmountFormat.format(-60_000, AmountSign.None)).isEqualTo("₹600.00")
        assertThat(AmountFormat.signText(-1, AmountSign.NegativeOnly)).isEqualTo("−")
        assertThat(AmountFormat.signText(1, AmountSign.NegativeOnly)).isEmpty()
    }

    @Test
    fun `decimals can be dropped for round amounts only`() {
        assertThat(AmountFormat.format(60_000, decimals = AmountDecimals.WhenNonZero)).isEqualTo("₹600")
        assertThat(AmountFormat.format(60_050, decimals = AmountDecimals.WhenNonZero)).isEqualTo("₹600.50")
    }

    @Test
    fun `spoken form spells out the minus`() {
        assertThat(AmountFormat.spoken(-60_000)).isEqualTo("minus ₹600.00")
        assertThat(AmountFormat.spoken(44_070)).isEqualTo("₹440.70")
    }
}

class RollingAmountLogicTest {
    @Test
    fun `glyph keys follow place value so columns keep their identity`() {
        val small = RollingAmountLogic.glyphs(98_765, AmountSign.NegativeOnly, AmountDecimals.Always)
        val big = RollingAmountLogic.glyphs(1_23_45_678_90, AmountSign.NegativeOnly, AmountDecimals.Always)
        assertThat(small.joinToString("") { it.char.toString() }).isEqualTo("₹987.65")
        assertThat(big.joinToString("") { it.char.toString() }).isEqualTo("₹1,23,45,678.90")
        val units = small.first { it is AmountGlyph.Digit && it.place == 0 }
        assertThat(big.first { it.key == units.key }.char).isEqualTo('8')
        assertThat(big.map { it.key }).containsNoDuplicates()
    }

    @Test
    fun `negative amounts get a sign glyph`() {
        val glyphs = RollingAmountLogic.glyphs(-100, AmountSign.NegativeOnly, AmountDecimals.Always)
        assertThat(glyphs.first()).isEqualTo(AmountGlyph.Sign(AmountFormat.MINUS))
    }

    @Test
    fun `glyph text matches the formatter for awkward values`() {
        for (paise in listOf(0L, 1L, 99L, 100L, 99_999L, 1_00_000L, -5_55_55L, AmountFormat.MAX_DISPLAY_PAISE, Long.MAX_VALUE)) {
            val text = RollingAmountLogic.glyphs(paise, AmountSign.NegativeOnly, AmountDecimals.Always).joinToString("") { it.char.toString() }
            assertWithMessage("$paise").that(text).isEqualTo(AmountFormat.format(paise))
        }
    }

    @Test
    fun `direction follows the value`() {
        assertThat(RollingAmountLogic.direction(100, 200)).isEqualTo(RollDirection.Up)
        assertThat(RollingAmountLogic.direction(200, 100)).isEqualTo(RollDirection.Down)
        assertThat(RollingAmountLogic.direction(100, 100)).isEqualTo(RollDirection.Up)
    }

    @Test
    fun `fit scale shrinks in stable steps and never below the floor`() {
        assertThat(RollingAmountLogic.fitScale(100f, 200f, 0.5f)).isEqualTo(1f)
        assertThat(RollingAmountLogic.fitScale(200f, 150f, 0.5f)).isWithin(1e-4f).of(0.75f)
        assertThat(RollingAmountLogic.fitScale(1_000f, 100f, 0.5f)).isEqualTo(0.5f)
        assertThat(RollingAmountLogic.fitScale(200f, 0f, 0.5f)).isEqualTo(0.5f)
        val scale = RollingAmountLogic.fitScale(333f, 300f, 0.4f)
        assertThat(333f * scale).isAtMost(300f)
    }
}

class CounterLogicTest {
    @Test
    fun `counts cap at 99 plus and never go negative`() {
        assertThat(CounterLogic.label(0)).isEqualTo("0")
        assertThat(CounterLogic.label(7)).isEqualTo("7")
        assertThat(CounterLogic.label(99)).isEqualTo("99")
        assertThat(CounterLogic.label(100)).isEqualTo("99+")
        assertThat(CounterLogic.label(-3)).isEqualTo("0")
    }

    @Test
    fun `direction says which way the digits slide`() {
        assertThat(CounterLogic.direction(1, 2)).isEqualTo(RollDirection.Up)
        assertThat(CounterLogic.direction(2, 1)).isEqualTo(RollDirection.Down)
    }
}

class AvatarLogicTest {
    @Test
    fun `initials come from the first two words`() {
        assertThat(AvatarLogic.initials("asha rao")).isEqualTo("AR")
        assertThat(AvatarLogic.initials("Ravi")).isEqualTo("R")
        assertThat(AvatarLogic.initials("  meera  k  iyer ")).isEqualTo("MK")
        assertThat(AvatarLogic.initials("राहुल शर्मा")).isEqualTo("रश")
    }

    @Test
    fun `initials skip leading symbols and fall back sensibly`() {
        assertThat(AvatarLogic.initials("🙂 Ravi")).isEqualTo("R")
        assertThat(AvatarLogic.initials("🌼")).isEqualTo("🌼")
        assertThat(AvatarLogic.initials("")).isEqualTo("?")
        assertThat(AvatarLogic.initials("‮")).isEqualTo("?")
    }

    @Test
    fun `tone is stable per name and ignores case and spacing`() {
        val a = AvatarLogic.toneIndex("Asha", 6)
        assertThat(AvatarLogic.toneIndex(" asha ", 6)).isEqualTo(a)
        assertThat(a).isIn(0..5)
        val spread = listOf("Asha", "Ravi", "Meera", "Darshan", "Kabir", "Isha", "Neel", "Tara").map { AvatarLogic.toneIndex(it, 6) }.toSet()
        assertThat(spread.size).isAtLeast(4)
    }

    @Test
    fun `two hundred members show four faces and plus 196`() {
        assertThat(AvatarLogic.layout(200, 4)).isEqualTo(StackLayout(visible = 4, overflow = 196))
        assertThat(AvatarLogic.overflowLabel(196)).isEqualTo("+196")
        assertThat(AvatarLogic.layout(3, 4)).isEqualTo(StackLayout(visible = 3, overflow = 0))
        assertThat(AvatarLogic.layout(5, 4)).isEqualTo(StackLayout(visible = 5, overflow = 0))
        assertThat(AvatarLogic.layout(6, 4)).isEqualTo(StackLayout(visible = 4, overflow = 2))
        assertThat(AvatarLogic.layout(0, 4)).isEqualTo(StackLayout(visible = 0, overflow = 0))
        assertThat(AvatarLogic.overflowLabel(5_000)).isEqualTo("999+")
    }

    @Test
    fun `description names a few and counts the rest`() {
        assertThat(AvatarLogic.description(emptyList())).isEqualTo("No members yet")
        assertThat(AvatarLogic.description(listOf("Asha"))).isEqualTo("Asha")
        assertThat(AvatarLogic.description(listOf("Asha", "Ravi"))).isEqualTo("Asha and Ravi")
        assertThat(AvatarLogic.description(listOf("Asha", "Ravi", "Meera"))).isEqualTo("Asha, Ravi and Meera")
        val many = (1..200).map { "Friend $it" }
        assertThat(AvatarLogic.description(many)).isEqualTo("Friend 1, Friend 2, Friend 3, Friend 4 and 196 more")
    }

    @Test
    fun `stack overlaps at rest and spreads with a gap when fanned out`() {
        val size = 100f
        assertThat(AvatarLogic.offsetX(2, 0f, size, 0.3f, 10f)).isWithin(1e-3f).of(140f)
        assertThat(AvatarLogic.offsetX(2, 1f, size, 0.3f, 10f)).isWithin(1e-3f).of(220f)
        assertThat(AvatarLogic.width(4, 0f, size, 0.3f, 10f)).isWithin(1e-3f).of(310f)
        assertThat(AvatarLogic.width(4, 1f, size, 0.3f, 10f)).isWithin(1e-3f).of(430f)
        assertThat(AvatarLogic.width(0, 1f, size, 0.3f, 10f)).isEqualTo(0f)
    }

    @Test
    fun `the hop peaks mid-fan and is zero at both ends`() {
        assertThat(AvatarLogic.hop(0f)).isWithin(1e-4f).of(0f)
        assertThat(AvatarLogic.hop(1f)).isWithin(1e-4f).of(0f)
        assertThat(AvatarLogic.hop(0.5f)).isWithin(1e-4f).of(1f)
        assertThat(AvatarLogic.hop(1.2f)).isEqualTo(0f)
    }
}

class SheetPhysicsTest {
    @Test
    fun `fast downward fling dismisses from anywhere, upward fling keeps it`() {
        assertThat(SheetPhysics.settle(10f, 1_000f, 2_000f, dismissible = true)).isEqualTo(SheetTarget.Dismissed)
        assertThat(SheetPhysics.settle(900f, 1_000f, -2_000f, dismissible = true)).isEqualTo(SheetTarget.Expanded)
    }

    @Test
    fun `slow release dismisses past the threshold only`() {
        assertThat(SheetPhysics.settle(300f, 1_000f, 0f, dismissible = true)).isEqualTo(SheetTarget.Expanded)
        assertThat(SheetPhysics.settle(400f, 1_000f, 0f, dismissible = true)).isEqualTo(SheetTarget.Dismissed)
    }

    @Test
    fun `a locked sheet always springs back`() {
        assertThat(SheetPhysics.settle(900f, 1_000f, 5_000f, dismissible = false)).isEqualTo(SheetTarget.Expanded)
    }

    @Test
    fun `dragging up past the top meets resistance`() {
        assertThat(SheetPhysics.applyDrag(0f, -100f)).isWithin(1e-3f).of(-20f)
        assertThat(SheetPhysics.applyDrag(50f, -100f)).isWithin(1e-3f).of(-10f)
        assertThat(SheetPhysics.applyDrag(50f, 30f)).isWithin(1e-3f).of(80f)
    }

    @Test
    fun `scrim fades with the drag`() {
        assertThat(SheetPhysics.scrimAlpha(0f, 1_000f)).isEqualTo(1f)
        assertThat(SheetPhysics.scrimAlpha(500f, 1_000f)).isWithin(1e-4f).of(0.5f)
        assertThat(SheetPhysics.scrimAlpha(2_000f, 1_000f)).isEqualTo(0f)
        assertThat(SheetPhysics.scrimAlpha(0f, 0f)).isEqualTo(0f)
    }
}

class BlobLogicTest {
    @Test
    fun `at rest the blob is centred on its tab at its base size`() {
        val shape = BlobLogic.shape(position = 2f, velocity = 0f, itemWidth = 100f, baseWidth = 64f, baseHeight = 40f)
        assertThat(shape.centerX).isWithin(1e-3f).of(250f)
        assertThat(shape.width).isWithin(1e-3f).of(64f)
        assertThat(shape.height).isWithin(1e-3f).of(40f)
    }

    @Test
    fun `moving fast stretches it wide and squashes it flat, within limits`() {
        val moving = BlobLogic.shape(1.5f, velocity = 3f, itemWidth = 100f, baseWidth = 64f, baseHeight = 40f)
        assertThat(moving.width).isGreaterThan(64f)
        assertThat(moving.height).isLessThan(40f)
        val wild = BlobLogic.shape(1.5f, velocity = -500f, itemWidth = 100f, baseWidth = 64f, baseHeight = 40f)
        assertThat(wild.width).isWithin(1e-3f).of(64f * (1 + BlobLogic.MAX_STRETCH))
        assertThat(wild.height).isWithin(1e-3f).of(40f * (1 - BlobLogic.MAX_STRETCH * BlobLogic.SQUASH_RATIO))
    }
}

class ConfettiPhysicsTest {
    @Test
    fun `the same seed gives the same burst`() {
        assertThat(ConfettiPhysics.burst(7, 40, 5, 3)).isEqualTo(ConfettiPhysics.burst(7, 40, 5, 3))
        assertThat(ConfettiPhysics.burst(7, 40, 5, 3)).isNotEqualTo(ConfettiPhysics.burst(8, 40, 5, 3))
    }

    @Test
    fun `particle count is capped and indexes stay in range`() {
        val burst = ConfettiPhysics.burst(1, 10_000, colors = 5, emojis = 2)
        assertThat(burst).hasSize(ConfettiPhysics.MAX_PARTICLES)
        assertThat(burst.all { it.colorIndex in 0 until 5 }).isTrue()
        assertThat(burst.all { it.emojiIndex in -1 until 2 }).isTrue()
        assertThat(burst.count { it.emojiIndex >= 0 }).isGreaterThan(0)
        assertThat(ConfettiPhysics.burst(1, 10, colors = 5, emojis = 0).all { it.emojiIndex == -1 }).isTrue()
    }

    @Test
    fun `pieces fly up first, fall under gravity and fade out by the end`() {
        val out = FloatArray(4)
        val particle = ConfettiPhysics.burst(3, 1, 5, 0).single()
        ConfettiPhysics.place(particle, 0f, 1.6f, out)
        assertThat(out[0]).isEqualTo(0f)
        assertThat(out[1]).isEqualTo(0f)
        assertThat(out[3]).isEqualTo(1f)
        ConfettiPhysics.place(particle, 0.15f, 1.6f, out)
        assertThat(out[1]).isLessThan(0f)
        val early = out[1]
        ConfettiPhysics.place(particle, 1.5f, 1.6f, out)
        assertThat(out[1]).isGreaterThan(early)
        assertThat(out[3]).isLessThan(0.5f)
        ConfettiPhysics.place(particle, 1.6f, 1.6f, out)
        assertThat(out[3]).isEqualTo(0f)
    }
}

class MotionMathTest {
    @Test
    fun `squash widens and flattens when pressed, stretches on the rebound`() {
        assertThat(SquashMath.scaleX(0f, Squash.Chunky)).isEqualTo(1f)
        assertThat(SquashMath.scaleY(0f, Squash.Chunky)).isEqualTo(1f)
        assertThat(SquashMath.scaleX(1f, Squash.Chunky)).isGreaterThan(1f)
        assertThat(SquashMath.scaleY(1f, Squash.Chunky)).isLessThan(1f)
        assertThat(SquashMath.scaleY(-0.1f, Squash.Chunky)).isGreaterThan(1f)
        assertThat(SquashMath.scaleX(-0.1f, Squash.Chunky)).isLessThan(1f)
        assertThat(SquashMath.scaleY(-5f, Squash.Chunky)).isEqualTo(SquashMath.scaleY(-0.4f, Squash.Chunky))
        assertThat(SquashMath.scaleY(1f, Squash.Subtle)).isGreaterThan(SquashMath.scaleY(1f, Squash.Chunky))
        assertThat(SquashMath.scaleX(1f, Squash.Chunky, widthPx = 1_000f, maxWidenPx = 20f)).isWithin(1e-5f).of(1.02f)
        assertThat(SquashMath.scaleX(1f, Squash.Chunky, widthPx = 100f, maxWidenPx = 20f)).isWithin(1e-5f).of(1f + Squash.Chunky.widen)
        assertThat(SquashMath.depthTravel(-0.2f)).isEqualTo(0f)
        assertThat(SquashMath.depthTravel(1.3f)).isEqualTo(1f)
    }

    @Test
    fun `shake rests at both ends and decays`() {
        assertThat(ShakeMath.offsetFraction(0f)).isWithin(1e-4f).of(0f)
        assertThat(ShakeMath.offsetFraction(1f)).isWithin(1e-4f).of(0f)
        val first = (1..20).maxOf { kotlin.math.abs(ShakeMath.offsetFraction(it / 100f)) }
        val last = (80..99).maxOf { kotlin.math.abs(ShakeMath.offsetFraction(it / 100f)) }
        assertThat(last).isLessThan(first)
    }

class BottomBarLayoutTest {
    @Test
    fun `the bar keeps its 68dp until a tab's icon and label need more, then grows to hold them`() {
        assertThat(BottomBarLayout.height(16.dp)).isEqualTo(68.dp)
        // 2x text: a 32dp label line under the 24dp icon.
        val tall = BottomBarLayout.height(32.dp)
        assertThat(tall).isGreaterThan(68.dp)
        assertThat(BottomBarLayout.faceHeight(32.dp) + Spacing.xs).isAtMost(tall - Spacing.xs * 2)
        assertThat(BottomBarLayout.height(24.dp)).isAtLeast(68.dp)
    }
}
}
