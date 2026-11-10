package com.darshan.splitsmart.ui.navigation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darshan.splitsmart.R
import com.darshan.splitsmart.ui.designsystem.components.BottomBarItem
import com.darshan.splitsmart.ui.designsystem.components.SsBottomBar
import com.darshan.splitsmart.ui.designsystem.motion.LocalMotion
import com.darshan.splitsmart.ui.designsystem.tokens.Depth
import com.darshan.splitsmart.ui.designsystem.tokens.Sizes
import com.darshan.splitsmart.ui.designsystem.tokens.Spacing
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import kotlinx.coroutines.flow.Flow

/**
 * Height the floating bar takes above the system navigation area. A tab screen keeps this much free at the end of its scroll
 * (plus the navigation bar inset) and lifts anything anchored to the bottom by it. Zero outside the shell.
 */
val LocalBottomBarInset = staticCompositionLocalOf { 0.dp }

/** The bar's own height plus its margins: what `SsBottomBar` occupies above the navigation bar. */
val BottomBarInset: Dp = Sizes.bottomBarHeight + Spacing.xs * 2 + Depth.card

/** The shell's bar state; null outside the shell. */
val LocalShellBar = staticCompositionLocalOf<ShellBarState?> { null }

/** While [active], the floating bar steps aside: for a sheet or menu drawn inside a tab screen. No-op outside the shell. */
@Composable
fun HideShellBarWhile(active: Boolean) {
    val bar = LocalShellBar.current ?: return
    DisposableEffect(bar, active) {
        if (active) bar.push()
        onDispose { if (active) bar.pop() }
    }
}

/** Carries "the current tab was tapped again". */
val LocalTabReselects = staticCompositionLocalOf<Flow<ShellRoute>?> { null }

/** Things the shell asks a screen to do, e.g. Scan with no groups asks the groups list to open "New group". */
enum class ShellRequest { NewGroup }

val LocalShellRequests = staticCompositionLocalOf<Flow<ShellRequest>?> { null }

/** Runs [onReselect] when the bar's item for [tab] is tapped while already there: the convention for scrolling to the top. */
@Composable
fun TabReselectEffect(tab: ShellRoute, onReselect: suspend () -> Unit) {
    val reselects = LocalTabReselects.current ?: return
    val action by rememberUpdatedState(onReselect)
    LaunchedEffect(reselects, tab) { reselects.collect { if (it == tab) action() } }
}

@Composable
fun ScrollToTopOnReselect(tab: ShellRoute, state: ScrollState) {
    val reduced = LocalMotion.current.reduced
    TabReselectEffect(tab) { if (reduced) state.scrollTo(0) else state.animateScrollTo(0) }
}

@Composable
fun ScrollToTopOnReselect(tab: ShellRoute, state: LazyListState) {
    val reduced = LocalMotion.current.reduced
    TabReselectEffect(tab) {
        // Past a screenful of rows an animated scroll would compose every row on the way: jump close, then glide.
        if (!reduced && state.firstVisibleItemIndex > SCROLL_ANIMATION_LIMIT) state.scrollToItem(SCROLL_ANIMATION_LIMIT)
        if (reduced) state.scrollToItem(0) else state.animateScrollToItem(0)
    }
}

/** Runs [onRequest] for each shell request of [kind] (no-op outside the shell). */
@Composable
fun ShellRequestEffect(kind: ShellRequest, onRequest: () -> Unit) {
    val requests = LocalShellRequests.current ?: return
    val action by rememberUpdatedState(onRequest)
    LaunchedEffect(requests, kind) { requests.collect { if (it == kind) action() } }
}

private const val SCROLL_ANIMATION_LIMIT = 12

/** The bar's three items, in [ShellLogic.BAR] order. */
@Composable
fun rememberBarItems(): List<BottomBarItem> {
    val groups = stringResource(R.string.nav_groups)
    val scan = stringResource(R.string.nav_scan)
    val me = stringResource(R.string.nav_me)
    val groupsIcon = ImageVector.vectorResource(R.drawable.ic_group)
    val scanIcon = ImageVector.vectorResource(R.drawable.ic_camera)
    return remember(groups, scan, me, groupsIcon, scanIcon) {
        listOf(BottomBarItem(groups, groupsIcon), BottomBarItem(scan, scanIcon), BottomBarItem(me, Icons.Filled.Person))
    }
}

/**
 * The app's bar: Marigold's floating aubergine pill with the bouncing blob. Content fades out just above it and everything from
 * its top edge down is page colour, so no half-readable text shows beside or below the pill.
 */
@Composable
fun ShellBottomBar(selectedIndex: Int, onSelect: (Int) -> Unit, onReselect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val background = SplitSmartTheme.colors.bg
    SsBottomBar(
        items = rememberBarItems(),
        selectedIndex = selectedIndex,
        onSelect = onSelect,
        onReselect = onReselect,
        modifier = modifier.drawWithCache {
            val rise = Spacing.xl.toPx()
            val solidFrom = Spacing.xs.toPx()
            val fade = Brush.verticalGradient(listOf(background.copy(alpha = 0f), background), startY = -rise, endY = solidFrom)
            onDrawBehind {
                drawRect(fade, topLeft = Offset(0f, -rise), size = Size(size.width, rise + solidFrom))
                drawRect(background, topLeft = Offset(0f, solidFrom), size = Size(size.width, size.height - solidFrom))
            }
        },
    )
}
