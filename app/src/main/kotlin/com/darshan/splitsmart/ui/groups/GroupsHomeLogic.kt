package com.darshan.splitsmart.ui.groups

import androidx.compose.runtime.Immutable
import com.darshan.splitsmart.ui.designsystem.components.AmountFormat
import kotlin.math.abs

/** Where the home hero says you stand, which also decides its colour. */
enum class HeroMood { Owed, Owe, Square, ToSettle, Nothing }

/**
 * The home hero's content. With your name it is your net balance over every group (archived ones included: money does not
 * disappear when a group is put away) and a breakdown when money flows both ways. Without it, what is left to settle overall.
 */
@Immutable
data class HeroBalance(
    val mood: HeroMood,
    val paise: Long,
    val showBreakdown: Boolean = false,
    val askForName: Boolean = false,
    val nameNotFound: Boolean = false,
)

/** What a group card's pill says about you in that group. */
enum class CardBalanceKind { Owed, Owe, YouSquare, ToSettle, NoBills, Settled }

@Immutable
data class CardBalance(val kind: CardBalanceKind, val paise: Long)

enum class SwipeOutcome { Archive, Return }

/** Pure rules behind the groups home, so the screen only draws. */
object GroupsHomeLogic {
    /** The odometer's ceiling (₹99,99,99,999.99); a larger sum would only be clipped by it anyway. */
    const val MAX_SHOWN_PAISE = AmountFormat.MAX_DISPLAY_PAISE

    fun hero(overview: HomeOverview): HeroBalance {
        val knowsYou = overview.yourName != null && overview.groupsWithYou > 0
        if (!knowsYou) {
            val outstanding = overview.outstandingPaise.coerceIn(0, MAX_SHOWN_PAISE)
            return HeroBalance(
                mood = if (outstanding > 0) HeroMood.ToSettle else HeroMood.Nothing,
                paise = outstanding,
                askForName = true,
                nameNotFound = overview.yourName != null,
            )
        }
        val owed = overview.youAreOwedPaise.coerceAtLeast(0)
        val owe = overview.youOwePaise.coerceAtLeast(0)
        val net = owed - owe
        return HeroBalance(
            mood = when {
                net > 0 -> HeroMood.Owed
                net < 0 -> HeroMood.Owe
                else -> HeroMood.Square
            },
            paise = abs(net).coerceAtMost(MAX_SHOWN_PAISE),
            showBreakdown = owed > 0 && owe > 0,
        )
    }

    fun cardBalance(card: GroupCard): CardBalance {
        val yours = card.yourBalancePaise
        return when {
            card.summary.billCount == 0 -> CardBalance(CardBalanceKind.NoBills, 0)
            yours != null && yours > 0 -> CardBalance(CardBalanceKind.Owed, yours)
            yours != null && yours < 0 -> CardBalance(CardBalanceKind.Owe, -yours)
            yours != null && card.outstandingPaise > 0 -> CardBalance(CardBalanceKind.YouSquare, 0)
            card.outstandingPaise > 0 -> CardBalance(CardBalanceKind.ToSettle, card.outstandingPaise)
            else -> CardBalance(CardBalanceKind.Settled, 0)
        }
    }
}

/**
 * Decides which cards pop in. On the first load the first screenful arrives staggered and the rest is simply there; a card that
 * joins the list later (just created, unarchived, an undone archive) pops in at once. A card plays at most once per arrival, so
 * scrolling it out and back, or returning from a group, replays nothing ([staggerFirstLoad] false when the list is coming back
 * rather than opening).
 */
class EntranceTracker(private val staggerFirstLoad: Boolean, private val maxStaggered: Int = MAX_STAGGERED) {
    private val pending = HashMap<String, Int>()
    private var present: Set<String> = emptySet()
    private var loaded = false

    fun update(ids: List<String>) {
        if (!loaded) {
            loaded = true
            if (staggerFirstLoad) for (index in 0 until minOf(maxStaggered, ids.size)) pending[ids[index]] = index
        } else {
            for (id in ids) if (id !in present) pending[id] = 0
        }
        present = ids.toHashSet()
    }

    /** The stagger position this card should pop in at, or null when it is already in place. Consumes it. */
    fun take(id: String): Int? = pending.remove(id)

    companion object {
        /** About a phone screenful of cards. */
        const val MAX_STAGGERED = 8
    }
}

/** The FAB menu opening: items leave from the button outwards, each starting a beat after the one nearer the button. */
object FabMenuLogic {
    private const val STAGGER = 0.15f

    /** Item [index] (0 = nearest the button) at overall menu progress [progress]; overshoot passes through. */
    fun itemProgress(progress: Float, index: Int, count: Int): Float {
        if (count <= 1) return progress.coerceAtLeast(0f)
        val span = 1f - (count - 1) * STAGGER
        return ((progress - index * STAGGER) / span).coerceAtLeast(0f)
    }
}

/**
 * Swipe a group card sideways to archive it. Past [THRESHOLD] of the width it archives on release; a fling in the drag's direction
 * archives from [MIN_FLING_FRACTION] on, a fling back always cancels. Crossing the threshold either way is one haptic tick.
 */
object SwipeToArchiveLogic {
    const val THRESHOLD = 0.4f
    const val MIN_FLING_FRACTION = 0.1f

    /** Release speed that counts as a fling, in dp per second (times the screen density). */
    const val FLING_DP_PER_SECOND = 1_000f

    /** How far a card leans as it is dragged, in degrees at a full width. */
    const val TILT_DEGREES = 6f

    fun settle(offset: Float, width: Float, velocity: Float, flingVelocity: Float): SwipeOutcome {
        if (width <= 0f || offset == 0f) return SwipeOutcome.Return
        val distance = abs(offset) / width
        val flung = abs(velocity) >= flingVelocity
        return when {
            flung && velocity * offset < 0f -> SwipeOutcome.Return
            flung && distance >= MIN_FLING_FRACTION -> SwipeOutcome.Archive
            distance >= THRESHOLD -> SwipeOutcome.Archive
            else -> SwipeOutcome.Return
        }
    }

    fun armed(offset: Float, width: Float): Boolean = width > 0f && abs(offset) >= width * THRESHOLD

    fun crossed(previous: Float, current: Float, width: Float): Boolean = armed(previous, width) != armed(current, width)

    /** 0 at rest, 1 once the threshold is reached: how much of the archive mark shows. */
    fun reveal(offset: Float, width: Float): Float = if (width <= 0f) 0f else (abs(offset) / (width * THRESHOLD)).coerceIn(0f, 1f)

    fun clamp(offset: Float, width: Float): Float = offset.coerceIn(-width, width)
}
