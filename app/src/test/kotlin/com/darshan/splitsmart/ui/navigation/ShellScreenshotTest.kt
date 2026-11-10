package com.darshan.splitsmart.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.domain.model.BillSource
import com.darshan.splitsmart.domain.model.GroupSummary
import com.darshan.splitsmart.domain.model.NameError
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.testGroup
import com.darshan.splitsmart.testutil.testMember
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.designsystem.captureNode
import com.darshan.splitsmart.ui.designsystem.components.SsBottomSheet
import com.darshan.splitsmart.ui.designsystem.effects.ThemeMode
import com.darshan.splitsmart.ui.designsystem.effects.UiSettings
import com.darshan.splitsmart.ui.designsystem.setDs
import com.darshan.splitsmart.ui.groupdetail.GroupDetail
import com.darshan.splitsmart.ui.groupdetail.GroupDetailCallbacks
import com.darshan.splitsmart.ui.groupdetail.GroupDetailScreen
import com.darshan.splitsmart.ui.groupdetail.GroupDetailUiState
import com.darshan.splitsmart.ui.groups.GroupCard
import com.darshan.splitsmart.ui.groups.GroupsScreen
import com.darshan.splitsmart.ui.groups.GroupsUiState
import com.darshan.splitsmart.ui.groups.HomeOverview
import com.darshan.splitsmart.ui.me.MeCallbacks
import com.darshan.splitsmart.ui.me.MeScreen
import com.darshan.splitsmart.ui.me.MeUiState
import com.darshan.splitsmart.ui.me.NameEdit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The shell as people see it: Me, the bar over the groups list, the bar's scan sheets, and the screens not rebuilt yet now
 * drawn with Marigold's Material slots (checked for readability until rows 10 to 13 rebuild them).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ShellScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, dark: Boolean, fontScale: Float = 1f, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setDs(dark = dark, fontScale = fontScale) {
            CompositionLocalProvider(LocalBottomBarInset provides BottomBarInset) {
                Box(Modifier.fillMaxSize().testTag(ROOT)) { content() }
            }
        }
        rule.mainClock.advanceTimeBy(1_500)
        rule.onNodeWithTag(ROOT).captureNode("shell_${name}_${if (dark) "dark" else "light"}${if (fontScale != 1f) "_font130" else ""}")
    }

    private val me = MeUiState(name = "Asha Rao", settings = UiSettings(theme = ThemeMode.System), aiReading = false)

    @Composable
    private fun WithBar(selected: Int, content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize()) {
            content()
            ShellBottomBar(selectedIndex = selected, onSelect = {}, onReselect = {}, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    @Test fun me_light() = shot("me", dark = false) { WithBar(2) { MeScreen(me, MeCallbacks()) } }
    @Test fun me_dark() = shot("me", dark = true) { WithBar(2) { MeScreen(me.copy(aiReading = true), MeCallbacks()) } }
    @Test fun me_font130() = shot("me", dark = false, fontScale = 1.3f) { WithBar(2) { MeScreen(me, MeCallbacks()) } }
    @Test fun me_name_sheet() = shot("me_name_sheet", dark = false) { MeScreen(me.copy(nameDraft = NameEdit("", NameError.BLANK)), MeCallbacks()) }

    private val goa = testGroup("goa", "Goa trip")
    private val flat = testGroup("flat", "Flat 302")
    private val cards = listOf(
        GroupCard(GroupSummary(goa, 3, 4), listOf("Darshan", "Asha", "Ravi"), outstandingPaise = 111_870, yourBalancePaise = 111_870),
        GroupCard(GroupSummary(flat, 2, 2), listOf("Darshan", "Priya"), outstandingPaise = 60_000, yourBalancePaise = -60_000),
    )

    @Composable
    private fun Groups() {
        Box(Modifier.fillMaxSize()) {
            GroupsScreen(
                state = GroupsUiState(LoadState.Content(cards), overview = HomeOverview("Darshan", 111_870, 60_000, 171_870, groupsWithYou = 2, groupCount = 2)),
                snackbar = SnackbarHostState(), onOpenGroup = {}, onAdd = {}, onDismiss = {}, onNameChange = {}, onConfirm = {}, onRetry = {},
            )
        }
    }

    /** The groups list is not rebuilt yet: it keeps its own Scaffold and ends above the bar, as in the shell. */
    @Test fun groups_with_bar_light() = shot("groups_with_bar", dark = false) { WithBar(0) { Box(Modifier.fillMaxSize().paddedForBar()) { Groups() } } }
    @Test fun groups_with_bar_dark() = shot("groups_with_bar", dark = true) { WithBar(0) { Box(Modifier.fillMaxSize().paddedForBar()) { Groups() } } }

    @Test
    fun legacy_group_detail_light() = shot("legacy_group_detail", dark = false) { DetailScreen() }

    @Test
    fun legacy_group_detail_dark() = shot("legacy_group_detail", dark = true) { DetailScreen() }

    @Composable
    private fun DetailScreen() {
        val asha = testMember("m1", "goa", "Asha")
        val ravi = testMember("m2", "goa", "Ravi")
        val bills = listOf(
            testBill(id = "ai", groupId = "goa", title = "Beach shack dinner", source = BillSource.SCAN_LLM, paidByMemberId = "m1"),
            testBill(id = "manual", groupId = "goa", title = "Cab", source = BillSource.MANUAL),
        )
        GroupDetailScreen(GroupDetailUiState(LoadState.Content(GroupDetail(goa, listOf(asha, ravi), bills))), SnackbarHostState(), detailCallbacks())
    }

    @Test
    fun scan_picker_light() = shot("scan_picker", dark = false) {
        Box(Modifier.fillMaxSize()) {
            Groups()
            SsBottomSheet(visible = true, onDismissRequest = {}) {
                ScanPickerContent(listOf(ScanChoice("goa", "Goa trip"), ScanChoice("flat", "Flat 302"), ScanChoice("office", "Office lunches"))) {}
            }
        }
    }

    @Test
    fun scan_needs_group_dark() = shot("scan_needs_group", dark = true) {
        Box(Modifier.fillMaxSize()) {
            SsBottomSheet(visible = true, onDismissRequest = {}) { ScanNeedsGroupContent(onCreateGroup = {}) }
        }
    }

    private fun detailCallbacks() = GroupDetailCallbacks(
        onBack = {}, onRetry = {}, onOpenBill = {}, onScan = {}, onSettleUp = {}, onDeleteGroup = {}, onAddMember = {}, onDismissMember = {},
        onMemberName = {}, onMemberVpa = {}, onSubmitMember = {}, onRemoveMember = {}, onAddBill = {}, onDismissBill = {}, onBillTitle = {},
        onBillAmount = {}, onBillPayer = {}, onSubmitBill = {}, onScanFromGallery = {},
    )

    private companion object {
        const val ROOT = "root"
    }
}

/** Same padding the shell gives the groups list. */
private fun Modifier.paddedForBar(): Modifier = padding(bottom = BottomBarInset)
