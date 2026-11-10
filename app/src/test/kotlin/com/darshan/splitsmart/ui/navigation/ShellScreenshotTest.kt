package com.darshan.splitsmart.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.domain.model.GroupSummary
import com.darshan.splitsmart.domain.model.NameError
import com.darshan.splitsmart.testutil.testGroup
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.designsystem.captureNode
import com.darshan.splitsmart.ui.designsystem.components.SsBottomSheet
import com.darshan.splitsmart.ui.designsystem.effects.ThemeMode
import com.darshan.splitsmart.ui.designsystem.effects.UiSettings
import com.darshan.splitsmart.ui.designsystem.setDs
import com.darshan.splitsmart.ui.groups.GroupCard
import com.darshan.splitsmart.ui.groups.GroupsCallbacks
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
 * The shell as people see it: Me, the bar over the groups list and the bar's scan sheets. The group screen's baselines moved to
 * `GroupDetailScreenshotTest` when it was rebuilt (row 10).
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
                callbacks = GroupsCallbacks(),
            )
        }
    }

    /** The groups list draws under the floating bar, as in the shell. */
    @Test fun groups_with_bar_light() = shot("groups_with_bar", dark = false) { WithBar(0) { Groups() } }
    @Test fun groups_with_bar_dark() = shot("groups_with_bar", dark = true) { WithBar(0) { Groups() } }

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

    private companion object {
        const val ROOT = "root"
    }
}
