package com.darshan.splitsmart.ui.onboarding

import kotlin.math.abs
import kotlin.math.floor

/** The intro's page rules. Pure, so the pager, back gesture and call to action agree without a device. */
object OnboardingPages {
    const val COUNT = 3
    private const val LAST = COUNT - 1

    fun next(page: Int): Int = (page.coerceIn(0, LAST) + 1).coerceAtMost(LAST)

    fun isLast(page: Int): Boolean = page >= LAST

    /** Back walks to the previous page; on the first page it leaves the app, as back does on any first screen. */
    fun backTarget(page: Int): Int? = page.coerceIn(0, LAST).takeIf { it > 0 }?.minus(1)

    /** The big "let's go" button rises in only once the last page is where the pager is heading. */
    fun ctaShown(targetPage: Int): Boolean = isLast(targetPage)
}

/** The active pill of the page indicator, as positions on the dot row: 0 is the first dot, `count - 1` the last. */
data class IndicatorPill(val start: Float, val end: Float)

object IndicatorLogic {
    /**
     * The pill for a continuous pager [position] (page + offset fraction). Between two dots its leading edge travels over the
     * first half of the swipe and its trailing edge over the second, so it stretches into a bar and contracts again: a worm.
     * Going backwards the same formula makes the left edge lead.
     */
    fun pill(position: Float, count: Int): IndicatorPill {
        val last = (count - 1).coerceAtLeast(0).toFloat()
        val p = if (position.isNaN()) 0f else position.coerceIn(0f, last)
        val base = floor(p)
        val f = p - base
        val head = base + (2f * f).coerceAtMost(1f)
        val tail = base + (2f * f - 1f).coerceAtLeast(0f)
        return IndicatorPill(tail, head)
    }
}

/** The three layers of each intro illustration. A positive depth holds the layer back as the page moves; negative runs ahead. */
enum class ArtLayer(val depth: Float) {
    Back(0.45f),
    Mid(0.18f),
    Front(-0.22f),
    Text(0.08f),
}

object ParallaxLogic {
    /** How far [page] is from the centre of the pager: 0 on screen, 1 one page to the left (already passed), -1 the next. */
    fun pageOffset(currentPage: Int, fraction: Float, page: Int): Float = (currentPage - page) + fraction

    /** Sideways shift of one layer, in px, added on top of the page's own movement. Flat under reduced motion. */
    fun shift(pageOffset: Float, widthPx: Float, depth: Float, reduced: Boolean): Float =
        if (reduced) 0f else pageOffset * widthPx * depth

    /** Text fades as its page slides away. */
    fun fade(pageOffset: Float): Float = (1f - abs(pageOffset)).coerceIn(0f, 1f)
}
