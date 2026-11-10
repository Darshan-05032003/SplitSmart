package com.darshan.splitsmart.ui.navigation

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.darshan.splitsmart.domain.model.Group
import com.darshan.splitsmart.ui.designsystem.tokens.contrastRatio

/** Every place the app can be. The shell's rules are written against this, so they are tested without a NavController. */
enum class ShellRoute { Groups, Me, GroupDetail, BillDetail, Scan, Review, Split, SettleUp }

/** How one screen replaces another. One vocabulary for the whole app; see docs/DECISIONS.md. */
enum class ScreenTransition {
    /** Tab to tab: no spatial relationship, so nothing slides. */
    FadeThrough,

    /** Forward and back along a flow: a small sideways move with a fade. */
    SharedAxis,

    /** The camera is a mode rather than a page: it rises over the app and sinks back. */
    Rise,

    /** A group card growing into the group screen. */
    ContainerTransform,
}

object ShellLogic {
    /** Bar order: Groups, Scan, Me. Scan is the hero flow, so it sits in the middle. */
    val BAR: List<ShellRoute> = listOf(ShellRoute.Groups, ShellRoute.Scan, ShellRoute.Me)

    private val tabRoots = setOf(ShellRoute.Groups, ShellRoute.Me)

    fun isTabRoot(route: ShellRoute?): Boolean = route in tabRoots

    /** Scan is in the bar but is not a tab: it opens the camera for a group over the current tab, and back returns there. */
    fun isAction(route: ShellRoute?): Boolean = route == ShellRoute.Scan

    /**
     * The bar belongs to the tab roots. A task inside a flow gets the whole screen, and so does anything modal on a tab (a sheet,
     * the FAB menu): drawn inside the screen, it would otherwise sit under the floating bar.
     */
    fun barVisible(route: ShellRoute?, modalOpen: Boolean = false): Boolean = isTabRoot(route) && !modalOpen

    /** Where the blob sits: on the current tab, or on the tab the user came from. */
    fun selectedIndex(route: ShellRoute?, lastTab: ShellRoute): Int =
        BAR.indexOf(if (isTabRoot(route)) route else lastTab).coerceAtLeast(0)

    /** The same answer in both directions, so going back always undoes the way in. */
    fun transition(from: ShellRoute?, to: ShellRoute?): ScreenTransition {
        val pair = setOf(from, to)
        return when {
            from == null || to == null -> ScreenTransition.FadeThrough
            isTabRoot(from) && isTabRoot(to) -> ScreenTransition.FadeThrough
            pair == setOf(ShellRoute.Groups, ShellRoute.GroupDetail) -> ScreenTransition.ContainerTransform
            // A scanned receipt moves forward into review; only entering and leaving the camera itself is the mode change.
            pair == setOf(ShellRoute.Scan, ShellRoute.Review) -> ScreenTransition.SharedAxis
            ShellRoute.Scan in pair -> ScreenTransition.Rise
            else -> ScreenTransition.SharedAxis
        }
    }
}

/** Counts the modal surfaces open on the current tab screen; the bar hides while there is any. */
@androidx.compose.runtime.Stable
class ShellBarState {
    private var open by androidx.compose.runtime.mutableIntStateOf(0)
    val modalOpen: Boolean get() = open > 0

    fun push() {
        open++
    }

    fun pop() {
        open = (open - 1).coerceAtLeast(0)
    }
}

/** A group the bar's Scan can open the camera for. */
@Immutable
data class ScanChoice(val groupId: String, val name: String)

/**
 * The groups a receipt can be scanned into: the active ones, so an old trip never stands between Scan and the camera. When every
 * group is archived they are all offered, rather than claiming there is no group.
 */
fun scanChoicesOf(groups: List<Group>): List<ScanChoice> {
    val active = groups.filterNot { it.isArchived }
    return (active.ifEmpty { groups }).map { ScanChoice(it.id, it.name) }
}

/** What Scan in the bar does, given the groups (null while they load). */
sealed interface ScanTarget {
    /** Groups not loaded yet: the tap is ignored rather than guessed. */
    data object Waiting : ScanTarget

    /** A receipt is split between the people in a group, so one has to exist first. */
    data object NeedsGroup : ScanTarget

    data class Direct(val groupId: String) : ScanTarget

    data class Pick(val choices: List<ScanChoice>) : ScanTarget

    companion object {
        fun resolve(groups: List<ScanChoice>?): ScanTarget = when {
            groups == null -> Waiting
            groups.isEmpty() -> NeedsGroup
            groups.size == 1 -> Direct(groups.single().groupId)
            else -> Pick(groups)
        }
    }
}

/** One surface that wants the status bar: [settled] once it has finished arriving and really covers the top of the screen. */
data class StatusBarEntry(val id: Long, val surface: Color, val settled: Boolean)

/**
 * Status-bar icon colour. Icons follow the surface under them, and a surface only takes the bar over once it has finished
 * arriving: mid-transition the screen that still covers the bar keeps it (on PayFlow's device check the icons flipped too early
 * during a reveal). With nothing registered they follow the app's theme, which can differ from the system's.
 */
object StatusBarLogic {
    // token-audit: allow (luminance references for the system's icon colours, never drawn)
    private val Dark = Color(0xFF000000)
    // token-audit: allow (luminance references for the system's icon colours, never drawn)
    private val Light = Color(0xFFFFFFFF)

    fun darkIconsOn(surface: Color): Boolean = contrastRatio(Dark, surface) >= contrastRatio(Light, surface)

    fun darkIcons(entries: List<StatusBarEntry>, themeDark: Boolean): Boolean {
        val owner = entries.lastOrNull { it.settled } ?: return !themeDark
        return darkIconsOn(owner.surface)
    }
}
