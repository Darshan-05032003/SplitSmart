package com.darshan.splitsmart.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.darshan.splitsmart.ui.bill.BillDetailRoute
import com.darshan.splitsmart.ui.designsystem.components.SsBottomSheet
import com.darshan.splitsmart.ui.designsystem.motion.LocalMotion
import com.darshan.splitsmart.ui.groupdetail.GroupDetailRoute
import com.darshan.splitsmart.ui.groups.GroupsRoute
import com.darshan.splitsmart.ui.me.MeRoute
import com.darshan.splitsmart.ui.review.ReviewRoute
import com.darshan.splitsmart.ui.scan.ScanRoute
import com.darshan.splitsmart.ui.settleup.SettleUpRoute
import com.darshan.splitsmart.ui.history.HistoryRoute
import com.darshan.splitsmart.ui.split.SplitRoute
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.Serializable

@Serializable data object GroupsDestination
@Serializable data object MeDestination
/** [addBill] opens the group with its add-bill sheet up (the home's "Add an expense"). */
@Serializable data class GroupDetailDestination(val groupId: String, val addBill: Boolean = false)
@Serializable data class BillDetailDestination(val billId: String)
@Serializable data class ScanDestination(val groupId: String, val gallery: Boolean = false)
@Serializable data class ReviewDestination(val draftId: String)
@Serializable data class SplitDestination(val billId: String)
@Serializable data class SettleUpDestination(val groupId: String)
@Serializable data class HistoryDestination(val groupId: String)

/** The destination as the shell's rules see it. */
fun NavDestination.shellRoute(): ShellRoute? = when {
    hasRoute<GroupsDestination>() -> ShellRoute.Groups
    hasRoute<MeDestination>() -> ShellRoute.Me
    hasRoute<GroupDetailDestination>() -> ShellRoute.GroupDetail
    hasRoute<BillDetailDestination>() -> ShellRoute.BillDetail
    hasRoute<ScanDestination>() -> ShellRoute.Scan
    hasRoute<ReviewDestination>() -> ShellRoute.Review
    hasRoute<SplitDestination>() -> ShellRoute.Split
    hasRoute<SettleUpDestination>() -> ShellRoute.SettleUp
    hasRoute<HistoryDestination>() -> ShellRoute.History
    else -> null
}

/** Opens a screen on top of the current one. Single top, so a double tap on a card or a button pushes it once. */
private fun NavController.push(destination: Any) = navigate(destination) { launchSingleTop = true }

/** Switches tab: one copy of each tab, each keeping its own state, and back from Me goes to Groups. */
internal fun NavController.openTab(destination: Any) = navigate(destination) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/** The sheet the bar's Scan can open. */
private enum class ScanSheet { None, Pick, NeedsGroup }

/**
 * The shell: one Scaffold-free NavHost under a floating [ShellBottomBar] (Groups, Scan, Me) that shows on the two tab roots.
 * Every screen change uses [ShellTransitions]; the group card grows into the group screen ([containerTransform]); predictive back
 * scrubs the pop animation (manifest `enableOnBackInvokedCallback`). Each screen tells [StatusBarSurface] what is under the
 * status bar once it has arrived. [onReplayIntro] comes from the root (Me → "Show the intro again").
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SplitSmartNavHost(
    onReplayIntro: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
    shellViewModel: ShellViewModel = hiltViewModel(),
) {
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.shellRoute()
    var lastTab by rememberSaveable { mutableStateOf(ShellRoute.Groups) }
    LaunchedEffect(route) { if (route != null && ShellLogic.isTabRoot(route)) lastTab = route }
    val reselects = remember { MutableSharedFlow<ShellRoute>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST) }
    // A channel, not a shared flow: "open New group" is sent before the groups list is back on screen, and must wait for it,
    // then be handled exactly once.
    val requests = remember { Channel<ShellRequest>(Channel.CONFLATED) }
    val requestFlow = remember(requests) { requests.receiveAsFlow() }
    val scanChoices by shellViewModel.scanChoices.collectAsStateWithLifecycle()
    var scanSheet by rememberSaveable { mutableStateOf(ScanSheet.None) }

    val motion = LocalMotion.current
    val transitions = remember(motion) { ShellTransitions(motion) }
    val barState = remember { ShellBarState() }

    val openScan: () -> Unit = {
        when (val target = ScanTarget.resolve(scanChoices)) {
            ScanTarget.Waiting -> Unit
            ScanTarget.NeedsGroup -> scanSheet = ScanSheet.NeedsGroup
            is ScanTarget.Direct -> navController.push(ScanDestination(target.groupId))
            is ScanTarget.Pick -> scanSheet = ScanSheet.Pick
        }
    }

    CompositionLocalProvider(
        LocalBottomBarInset provides BottomBarInset,
        LocalTabReselects provides reselects,
        LocalShellRequests provides requestFlow,
        LocalShellBar provides barState,
    ) {
        SharedTransitionLayout(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                Box(Modifier.fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = GroupsDestination,
                        enterTransition = { transitions.enter(initialState.destination.shellRoute(), targetState.destination.shellRoute()) },
                        exitTransition = { transitions.exit(initialState.destination.shellRoute(), targetState.destination.shellRoute()) },
                        popEnterTransition = { transitions.popEnter(initialState.destination.shellRoute(), targetState.destination.shellRoute()) },
                        popExitTransition = { transitions.popExit(initialState.destination.shellRoute(), targetState.destination.shellRoute()) },
                        sizeTransform = null,
                    ) {
                        screen<GroupsDestination> {
                            // Draws under the floating bar; the list's end and the FAB keep clear of it (LocalBottomBarInset).
                            GroupsRoute(
                                onOpenGroup = { navController.push(GroupDetailDestination(it)) },
                                onScan = openScan,
                                onAddExpense = { navController.push(GroupDetailDestination(it, addBill = true)) },
                            )
                        }
                        screen<MeDestination> {
                            MeRoute(onReplayIntro = onReplayIntro)
                        }
                        screen<GroupDetailDestination> { backStackEntry ->
                            val groupId = backStackEntry.toRoute<GroupDetailDestination>().groupId
                            Box(Modifier.fillMaxSize().containerTransform(groupContainerKey(groupId))) {
                                GroupDetailRoute(
                                    onBack = { navController.popBackStack() },
                                    onOpenBill = { navController.push(BillDetailDestination(it)) },
                                    onScanReceipt = { navController.push(ScanDestination(it)) },
                                    onSettleUp = { navController.push(SettleUpDestination(it)) },
                                    onHistory = { navController.push(HistoryDestination(it)) },
                                    onScanFromGallery = { navController.push(ScanDestination(it, gallery = true)) },
                                )
                            }
                        }
                        screen<BillDetailDestination> { backStackEntry ->
                            val billId = backStackEntry.toRoute<BillDetailDestination>().billId
                            Box(Modifier.fillMaxSize().containerTransform(billContainerKey(billId))) {
                                BillDetailRoute(onBack = { navController.popBackStack() }, onSplit = { navController.push(SplitDestination(it)) })
                            }
                        }
                        screen<SettleUpDestination> {
                            SettleUpRoute(
                                onBack = { navController.popBackStack() },
                                onOpenBill = { navController.push(BillDetailDestination(it)) },
                                onHistory = { navController.push(HistoryDestination(it)) },
                            )
                        }
                        screen<HistoryDestination> {
                            HistoryRoute(onBack = { navController.popBackStack() }, onOpenBill = { navController.push(BillDetailDestination(it)) })
                        }
                        screen<SplitDestination> {
                            SplitRoute(onBack = { navController.popBackStack() }, onSaved = { navController.popBackStack() })
                        }
                        screen<ScanDestination> { backStackEntry ->
                            ScanRoute(
                                onBack = { navController.popBackStack() },
                                onContinue = { navController.push(ReviewDestination(it)) },
                                startWithGallery = backStackEntry.toRoute<ScanDestination>().gallery,
                            )
                        }
                        screen<ReviewDestination> {
                            ReviewRoute(
                                onBack = { navController.popBackStack() },
                                onSaved = { billId -> navController.navigate(BillDetailDestination(billId)) { popUpTo<GroupDetailDestination>() } },
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = ShellLogic.barVisible(route, modalOpen = barState.modalOpen),
                        modifier = Modifier.align(Alignment.BottomCenter),
                        enter = motion.enter(),
                        exit = motion.exit(),
                    ) {
                        ShellBottomBar(
                            selectedIndex = ShellLogic.selectedIndex(route, lastTab),
                            onSelect = { index ->
                                when (ShellLogic.BAR[index]) {
                                    ShellRoute.Scan -> openScan()
                                    ShellRoute.Me -> navController.openTab(MeDestination)
                                    else -> navController.openTab(GroupsDestination)
                                }
                            },
                            onReselect = { index -> reselects.tryEmit(ShellLogic.BAR[index]) },
                            modifier = Modifier.testTag(ShellTags.BAR),
                        )
                    }
                    SsBottomSheet(visible = scanSheet != ScanSheet.None, onDismissRequest = { scanSheet = ScanSheet.None }) {
                        when (scanSheet) {
                            ScanSheet.Pick -> ScanPickerContent(scanChoices.orEmpty()) { groupId ->
                                scanSheet = ScanSheet.None
                                navController.push(ScanDestination(groupId))
                            }
                            ScanSheet.NeedsGroup -> ScanNeedsGroupContent(onCreateGroup = {
                                scanSheet = ScanSheet.None
                                navController.openTab(GroupsDestination)
                                requests.trySend(ShellRequest.NewGroup)
                            })
                            ScanSheet.None -> Unit
                        }
                    }
                }
            }
        }
    }
}

/** A destination that hands its transition scope to [containerTransform] and owns the status bar once it has arrived. */
private inline fun <reified T : Any> NavGraphBuilder.screen(noinline content: @Composable (NavBackStackEntry) -> Unit) {
    composable<T> { backStackEntry ->
        StatusBarSurface(SplitSmartTheme.colors.bg, settled = hasArrived())
        CompositionLocalProvider(LocalNavAnimatedScope provides this) { content(backStackEntry) }
    }
}
