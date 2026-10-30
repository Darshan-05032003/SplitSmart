package com.darshan.splitsmart.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.darshan.splitsmart.ui.bill.BillDetailRoute
import com.darshan.splitsmart.ui.groupdetail.GroupDetailRoute
import com.darshan.splitsmart.ui.groups.GroupsRoute
import com.darshan.splitsmart.ui.review.ReviewRoute
import com.darshan.splitsmart.ui.scan.ScanRoute
import com.darshan.splitsmart.ui.settleup.SettleUpRoute
import com.darshan.splitsmart.ui.split.SplitRoute
import kotlinx.serialization.Serializable

@Serializable data object GroupsDestination
@Serializable data class GroupDetailDestination(val groupId: String)
@Serializable data class BillDetailDestination(val billId: String)
@Serializable data class ScanDestination(val groupId: String)
@Serializable data class ReviewDestination(val draftId: String)
@Serializable data class SplitDestination(val billId: String)
@Serializable data class SettleUpDestination(val groupId: String)

@Composable
fun SplitSmartNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = GroupsDestination) {
        composable<GroupsDestination> {
            GroupsRoute(onOpenGroup = { navController.navigate(GroupDetailDestination(it)) })
        }
        composable<GroupDetailDestination> {
            GroupDetailRoute(
                onBack = { navController.popBackStack() },
                onOpenBill = { navController.navigate(BillDetailDestination(it)) },
                onScanReceipt = { navController.navigate(ScanDestination(it)) },
                onSettleUp = { navController.navigate(SettleUpDestination(it)) },
            )
        }
        composable<BillDetailDestination> {
            BillDetailRoute(onBack = { navController.popBackStack() }, onSplit = { navController.navigate(SplitDestination(it)) })
        }
        composable<SettleUpDestination> {
            SettleUpRoute(onBack = { navController.popBackStack() })
        }
        composable<SplitDestination> {
            SplitRoute(onBack = { navController.popBackStack() }, onSaved = { navController.popBackStack() })
        }
        composable<ScanDestination> {
            ScanRoute(onBack = { navController.popBackStack() }, onContinue = { navController.navigate(ReviewDestination(it)) })
        }
        composable<ReviewDestination> {
            ReviewRoute(
                onBack = { navController.popBackStack() },
                onSaved = { billId -> navController.navigate(BillDetailDestination(billId)) { popUpTo<GroupDetailDestination>() } },
            )
        }
    }
}
