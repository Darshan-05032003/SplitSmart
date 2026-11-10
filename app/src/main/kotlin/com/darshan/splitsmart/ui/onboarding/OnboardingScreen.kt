package com.darshan.splitsmart.ui.onboarding

import androidx.activity.compose.PredictiveBackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.darshan.splitsmart.R
import com.darshan.splitsmart.ui.designsystem.components.ButtonStyle
import com.darshan.splitsmart.ui.designsystem.components.ChunkyButton
import com.darshan.splitsmart.ui.designsystem.components.ChunkyIconButton
import com.darshan.splitsmart.ui.designsystem.components.TextAction
import com.darshan.splitsmart.ui.designsystem.effects.LocalHaptics
import com.darshan.splitsmart.ui.designsystem.effects.tick
import com.darshan.splitsmart.ui.designsystem.motion.LocalMotion
import com.darshan.splitsmart.ui.designsystem.motion.MotionToken
import com.darshan.splitsmart.ui.designsystem.tokens.MarigoldColors
import com.darshan.splitsmart.ui.designsystem.tokens.Radii
import com.darshan.splitsmart.ui.designsystem.tokens.Sizes
import com.darshan.splitsmart.ui.designsystem.tokens.Spacing
import com.darshan.splitsmart.ui.designsystem.tokens.Tone
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

object OnboardingTags {
    const val PAGER = "onboarding_pager"
    const val SKIP = "onboarding_skip"
    const val NEXT = "onboarding_next"
    const val CTA = "onboarding_cta"
    const val INDICATOR = "onboarding_indicator"
    fun page(index: Int) = "onboarding_page_$index"
}

/** One intro page: its art, words and the tone of the stage the art stands on. */
private class IntroPage(val art: ArtKind, val emoji: String, @StringRes val title: Int, @StringRes val body: Int, val stage: (MarigoldColors) -> Tone)

private val Pages = listOf(
    IntroPage(ArtKind.Snap, "🧾", R.string.onboarding_1_title, R.string.onboarding_1_body) { it.pending },
    IntroPage(ArtKind.Split, "🍕", R.string.onboarding_2_title, R.string.onboarding_2_body) { it.success },
    IntroPage(ArtKind.Settle, "💸", R.string.onboarding_3_title, R.string.onboarding_3_body) { it.info },
)

private val StageShape = RoundedCornerShape(bottomStart = Radii.large, bottomEnd = Radii.large)

/** How far the previous page peeks in while a back gesture is held, as a fraction of a page. */
private const val BACK_PEEK = 0.3f

/** How far the call to action travels up as it arrives, in button heights. */
private const val CTA_RISE = 1.2f

/**
 * The three-page intro, shown once. Each page's illustration is three layers moving at different speeds; the page indicator is
 * a pill that stretches between dots like a worm; on the last page the "Let's split a bill" button rises in on the Bouncy spring.
 * Skip and that button both call [onFinish] once. Back walks to the previous page (a predictive gesture lets it peek in first);
 * on the first page back leaves the app. Reduced motion: no parallax, no worm, the pager jumps and the button fades in.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit, modifier: Modifier = Modifier, initialPage: Int = 0) {
    val colors = SplitSmartTheme.colors
    val motion = LocalMotion.current
    val haptics = LocalHaptics.current
    val pager = rememberPagerState(initialPage.coerceIn(0, OnboardingPages.COUNT - 1)) { OnboardingPages.COUNT }
    val scope = rememberCoroutineScope()
    val currentOnFinish by rememberUpdatedState(onFinish)
    var finished by remember { mutableStateOf(false) }
    val finish = {
        if (!finished) {
            finished = true
            currentOnFinish()
        }
    }
    val goTo: (Int) -> Unit = { page ->
        scope.launch { if (motion.reduced) pager.scrollToPage(page) else pager.animateScrollToPage(page, animationSpec = motion.spec(MotionToken.Settle)) }
    }

    LaunchedEffect(pager, haptics) { snapshotFlow { pager.settledPage }.drop(1).collect { haptics.tick() } }

    PredictiveBackHandler(enabled = OnboardingPages.backTarget(pager.currentPage) != null) { progress ->
        val from = pager.currentPage
        val target = OnboardingPages.backTarget(from) ?: return@PredictiveBackHandler
        try {
            progress.collect { event -> if (!motion.reduced) pager.scrollToPage(from, -event.progress * BACK_PEEK) }
            goTo(target)
        } catch (cancelled: CancellationException) {
            goTo(from)
            throw cancelled
        }
    }

    Column(modifier.fillMaxSize().background(colors.bg)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth().testTag(OnboardingTags.PAGER),
            beyondViewportPageCount = 1,
            key = { it },
        ) { page ->
            IntroPageView(Pages[page], page, pager)
        }
        Controls(pager, onNext = { goTo(OnboardingPages.next(pager.currentPage)) }, onFinish = finish)
    }
}

@Composable
private fun IntroPageView(page: IntroPage, index: Int, pager: PagerState) {
    val colors = SplitSmartTheme.colors
    val reduced = LocalMotion.current.reduced
    val offset = { ParallaxLogic.pageOffset(pager.currentPage, pager.currentPageOffsetFraction, index) }
    val stage = page.stage(colors)
    // Each page clips its own layers and words: parallax shifts must never cross into the neighbouring page.
    Column(Modifier.fillMaxSize().clipToBounds().testTag(OnboardingTags.page(index))) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(StageShape)
                .background(stage.container)
                .statusBarsPadding()
                .padding(Spacing.xl),
            contentAlignment = Alignment.Center,
        ) {
            OnboardingArt(page.art, offset, Modifier.aspectRatio(1f, matchHeightConstraintsFirst = true))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val o = offset()
                    translationX = ParallaxLogic.shift(o, size.width, ArtLayer.Text.depth, reduced)
                    alpha = ParallaxLogic.fade(o)
                }
                .padding(horizontal = Spacing.screen)
                .padding(top = Spacing.xl, bottom = Spacing.md),
        ) {
            // Every page reserves room for the longest words, so all stages are the same height at any text size and the
            // titles stay level as pages slide past each other.
            for (other in Pages) if (other !== page) PageWords(other, Modifier.alpha(0f).clearAndSetSemantics { })
            PageWords(page)
        }
    }
}

@Composable
private fun PageWords(page: IntroPage, modifier: Modifier = Modifier) {
    val colors = SplitSmartTheme.colors
    val type = SplitSmartTheme.type
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(Modifier.semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically) {
            BasicText(page.emoji, style = type.display.copy(color = colors.text))
            Spacer(Modifier.width(Spacing.xs))
            BasicText(stringResource(page.title), style = type.display.copy(color = colors.text))
        }
        BasicText(stringResource(page.body), style = type.body.copy(color = colors.textSecondary))
    }
}

@Composable
private fun Controls(pager: PagerState, onNext: () -> Unit, onFinish: () -> Unit) {
    val motion = LocalMotion.current
    val ctaShown = OnboardingPages.ctaShown(pager.targetPage)
    val rise = remember { Animatable(if (ctaShown) 1f else 0f) }
    LaunchedEffect(ctaShown, motion) { rise.animateTo(if (ctaShown) 1f else 0f, motion.spec(MotionToken.Bounce)) }
    val reduced = motion.reduced
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.screen)
            .padding(bottom = Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        PageIndicator(pager)
        Box(Modifier.fillMaxWidth().heightIn(min = Sizes.buttonHeight + Spacing.xs), contentAlignment = Alignment.Center) {
            if (!ctaShown || rise.isRunning) {
                Row(
                    Modifier.fillMaxWidth().graphicsLayer { alpha = (1f - rise.value).coerceIn(0f, 1f) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextAction(stringResource(R.string.onboarding_skip), onFinish, Modifier.testTag(OnboardingTags.SKIP))
                    Spacer(Modifier.weight(1f))
                    ChunkyIconButton(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        stringResource(R.string.onboarding_next),
                        onNext,
                        Modifier.testTag(OnboardingTags.NEXT),
                        style = ButtonStyle.Primary,
                    )
                }
            }
            if (ctaShown || rise.isRunning) {
                ChunkyButton(
                    stringResource(R.string.onboarding_start),
                    onFinish,
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = rise.value.coerceIn(0f, 1f)
                            translationY = if (reduced) 0f else (1f - rise.value) * size.height * CTA_RISE
                        }
                        .testTag(OnboardingTags.CTA),
                    emoji = "🎉",
                )
            }
        }
    }
}

/** Dots with an accent pill that stretches from one to the next as the pager moves. Drawn, not composed, per frame. */
@Composable
private fun PageIndicator(pager: PagerState) {
    val colors = SplitSmartTheme.colors
    val reduced = LocalMotion.current.reduced
    val count = OnboardingPages.COUNT
    val description = stringResource(R.string.onboarding_page, pager.currentPage + 1, count)
    val dot = Sizes.statusDot
    val gap = Spacing.xl
    val extra = Sizes.statusDot
    Spacer(
        Modifier
            .height(dot)
            .width(gap * (count - 1) + dot + extra * 2)
            .testTag(OnboardingTags.INDICATOR)
            .semantics { contentDescription = description }
            .drawWithCache {
                val d = dot.toPx()
                val g = gap.toPx()
                val e = extra.toPx()
                val radius = CornerRadius(d / 2f)
                fun x(slot: Float) = e + d / 2f + slot * g
                onDrawBehind {
                    for (i in 0 until count) drawCircle(colors.outline, d / 2f, Offset(x(i.toFloat()), d / 2f))
                    val raw = pager.currentPage + pager.currentPageOffsetFraction
                    val position = if (reduced) pager.currentPage.toFloat() else raw
                    val pill = IndicatorLogic.pill(position, count)
                    val left = x(pill.start) - d / 2f - e
                    val right = x(pill.end) + d / 2f + e
                    drawRoundRect(colors.accent, Offset(left, 0f), Size(right - left, d), radius)
                }
            },
    )
}
