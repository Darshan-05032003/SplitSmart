package com.darshan.splitsmart.ui.onboarding

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OnboardingLogicTest {
    @Test
    fun `three pages, next stops at the last, back walks home then leaves`() {
        assertThat(OnboardingPages.COUNT).isEqualTo(3)
        assertThat(OnboardingPages.next(0)).isEqualTo(1)
        assertThat(OnboardingPages.next(1)).isEqualTo(2)
        assertThat(OnboardingPages.next(2)).isEqualTo(2)
        assertThat(OnboardingPages.isLast(2)).isTrue()
        assertThat(OnboardingPages.isLast(1)).isFalse()
        assertThat(OnboardingPages.backTarget(2)).isEqualTo(1)
        assertThat(OnboardingPages.backTarget(1)).isEqualTo(0)
        assertThat(OnboardingPages.backTarget(0)).isNull()
    }

    @Test
    fun `out-of-range pages are clamped, never crash`() {
        assertThat(OnboardingPages.next(-5)).isEqualTo(1)
        assertThat(OnboardingPages.next(99)).isEqualTo(2)
        assertThat(OnboardingPages.backTarget(99)).isEqualTo(1)
        assertThat(OnboardingPages.isLast(99)).isTrue()
    }

    @Test
    fun `the indicator rests as one dot-sized pill on each page`() {
        for (page in 0 until OnboardingPages.COUNT) {
            assertThat(IndicatorLogic.pill(page.toFloat(), OnboardingPages.COUNT)).isEqualTo(IndicatorPill(page.toFloat(), page.toFloat()))
        }
    }

    @Test
    fun `moving forward, the head stretches first and the tail catches up`() {
        assertThat(IndicatorLogic.pill(0.25f, 3)).isEqualTo(IndicatorPill(0f, 0.5f))
        assertThat(IndicatorLogic.pill(0.5f, 3)).isEqualTo(IndicatorPill(0f, 1f))
        assertThat(IndicatorLogic.pill(0.75f, 3)).isEqualTo(IndicatorPill(0.5f, 1f))
    }

    @Test
    fun `moving back, the left edge leads, the same worm in reverse`() {
        // From page 2 back to 1 the position falls from 2.0 through 1.75: the left edge has already left.
        assertThat(IndicatorLogic.pill(1.75f, 3)).isEqualTo(IndicatorPill(1.5f, 2f))
        assertThat(IndicatorLogic.pill(1.25f, 3)).isEqualTo(IndicatorPill(1f, 1.5f))
    }

    @Test
    fun `the pill never leaves the dots, even when the pager overscrolls`() {
        assertThat(IndicatorLogic.pill(-0.4f, 3)).isEqualTo(IndicatorPill(0f, 0f))
        assertThat(IndicatorLogic.pill(2.6f, 3)).isEqualTo(IndicatorPill(2f, 2f))
        assertThat(IndicatorLogic.pill(Float.NaN, 3)).isEqualTo(IndicatorPill(0f, 0f))
    }

    @Test
    fun `page offset is how far a page is from the centre, signed`() {
        assertThat(ParallaxLogic.pageOffset(currentPage = 1, fraction = 0f, page = 1)).isEqualTo(0f)
        assertThat(ParallaxLogic.pageOffset(currentPage = 1, fraction = 0.25f, page = 1)).isEqualTo(0.25f)
        assertThat(ParallaxLogic.pageOffset(currentPage = 1, fraction = 0.25f, page = 2)).isEqualTo(-0.75f)
    }

    @Test
    fun `deeper layers lag the page, the front layer runs ahead`() {
        val width = 1000f
        val back = ParallaxLogic.shift(0.5f, width, ArtLayer.Back.depth, reduced = false)
        val mid = ParallaxLogic.shift(0.5f, width, ArtLayer.Mid.depth, reduced = false)
        val front = ParallaxLogic.shift(0.5f, width, ArtLayer.Front.depth, reduced = false)
        // The page itself moves by -500 px; a positive shift holds a layer back.
        assertThat(back).isGreaterThan(mid)
        assertThat(mid).isGreaterThan(0f)
        assertThat(front).isLessThan(0f)
        assertThat(ParallaxLogic.shift(0f, width, ArtLayer.Back.depth, reduced = false)).isEqualTo(0f)
    }

    @Test
    fun `reduced motion flattens the parallax`() {
        for (layer in ArtLayer.entries) assertThat(ParallaxLogic.shift(0.5f, 1000f, layer.depth, reduced = true)).isEqualTo(0f)
    }

    @Test
    fun `text fades with distance and is fully there at rest`() {
        assertThat(ParallaxLogic.fade(0f)).isEqualTo(1f)
        assertThat(ParallaxLogic.fade(0.5f)).isEqualTo(0.5f)
        assertThat(ParallaxLogic.fade(-1.5f)).isEqualTo(0f)
    }

    @Test
    fun `the call to action belongs to the last page only`() {
        assertThat(OnboardingPages.ctaShown(targetPage = 0)).isFalse()
        assertThat(OnboardingPages.ctaShown(targetPage = 1)).isFalse()
        assertThat(OnboardingPages.ctaShown(targetPage = 2)).isTrue()
    }
}
