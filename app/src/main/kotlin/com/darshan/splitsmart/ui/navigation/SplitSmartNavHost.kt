package com.darshan.splitsmart.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.darshan.splitsmart.ui.bill.BillDetailRoute
import com.darshan.splitsmart.ui.groupdetail.GroupDetailRoute
import com.darshan.splitsmart.ui.groups.GroupsRoute
import kotlinx.serialization.Serializable

@Serializable data object GroupsDestination
@Serializable data class GroupDetailDestination(val groupId: String)
@Serializable data class BillDetailDestination(val billId: String)

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
                onScanReceipt = { /* feature 2 */ },
            )
        }
        composable<BillDetailDestination> {
            BillDetailRoute(onBack = { navController.popBackStack() })
        }
    }
}
