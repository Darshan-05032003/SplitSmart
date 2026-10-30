package com.darshan.splitsmart.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.darshan.splitsmart.ui.bill.BillDetailRoute
import com.darshan.splitsmart.ui.groupdetail.GroupDetailRoute
import com.darshan.splitsmart.ui.groups.GroupsRoute
import com.darshan.splitsmart.ui.scan.ScanRoute
import kotlinx.serialization.Serializable

@Serializable data object GroupsDestination
@Serializable data class GroupDetailDestination(val groupId: String)
@Serializable data class BillDetailDestination(val billId: String)
@Serializable data class ScanDestination(val groupId: String)

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
            )
        }
        composable<BillDetailDestination> {
            BillDetailRoute(onBack = { navController.popBackStack() })
        }
        composable<ScanDestination> {
            // Feature 3 replaces the pop with the editable review screen for the saved draft.
            ScanRoute(onBack = { navController.popBackStack() }, onContinue = { navController.popBackStack() })
        }
    }
}
