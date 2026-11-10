package com.darshan.splitsmart.ui.settleup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.domain.model.SettlementMethod
import com.darshan.splitsmart.ui.ErrorReason
import com.darshan.splitsmart.ui.LoadState
import com.darshan.splitsmart.ui.designsystem.captureNode
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.darshan.splitsmart.ui.designsystem.setDs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The rebuilt settle-up screen in its states, light and dark, at 1.3× text, with its sheets. Reduced motion: no glint, the stamp at rest. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class SettleUpScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, state: SettleUpUiState, dark: Boolean = false, fontScale: Float = 1f, act: () -> Unit = {}) {
        rule.mainClock.autoAdvance = false
        rule.setDs(dark = dark, fontScale = fontScale, reduceMotion = true) {
            Box(Modifier.fillMaxSize().testTag(ROOT)) { SettleUpScreen(state, SsSnackbarState(), SettleUpFixtures.callbacks) }
        }
        rule.mainClock.advanceTimeBy(1_500)
        act()
        rule.onNodeWithTag(ROOT).captureNode("settle_${name}_${if (dark) "dark" else "light"}${if (fontScale != 1f) "_font130" else ""}")
    }

    private fun scrollTo(key: String) {
        rule.onNodeWithTag(SettleUpTags.LIST).performScrollToKey(key)
        rule.mainClock.advanceTimeBy(500)
    }

    @Test fun payments_light() = shot("payments", SettleUpFixtures.state())
    @Test fun payments_dark() = shot("payments", SettleUpFixtures.state(), dark = true)
    @Test fun payments_font130() = shot("payments", SettleUpFixtures.state(), fontScale = 1.3f)
    @Test fun asking_light() = shot("asking", SettleUpFixtures.state(check = SettleUpFixtures.check()))
    @Test fun away_dark() = shot("away", SettleUpFixtures.state(check = SettleUpFixtures.check(asking = false)), dark = true)
    @Test fun other_way_dark() = shot("other_way", SettleUpFixtures.state(check = SettleUpFixtures.check(SettleUpFixtures.items[1], SettlementMethod.OTHER)), dark = true)
    @Test fun settled_light() = shot("settled", SettleUpFixtures.state(items = SettleUpFixtures.items.drop(1), settled = SettleUpFixtures.moment()))
    @Test fun settled_dark() = shot("settled", SettleUpFixtures.state(items = SettleUpFixtures.items.drop(1), settled = SettleUpFixtures.moment()), dark = true)
    @Test fun all_square_light() = shot("all_square", SettleUpFixtures.state(items = emptyList(), history = 3))
    @Test fun all_square_dark() = shot("all_square", SettleUpFixtures.state(items = emptyList(), settled = SettleUpFixtures.moment(allSquare = true), history = 3), dark = true)

    @Test
    fun to_fix_light() = shot("to_fix", SettleUpFixtures.state(toFix = SettleUpFixtures.toFix), act = { scrollTo("toFix") })

    @Test
    fun missing_upi_dark() = shot("missing_upi", SettleUpFixtures.state(), dark = true, act = { scrollTo("m>r") })

    @Test
    fun qr_sheet_light() = shot("qr_sheet", SettleUpFixtures.state().copy(qrItem = SettleUpFixtures.items.first()))

    @Test
    fun vpa_sheet_dark() = shot("vpa_sheet", SettleUpFixtures.state().copy(vpaDraft = VpaDraft("r", "ravi@")), dark = true)

    @Test fun empty_light() = shot("empty", SettleUpUiState(LoadState.Empty))
    @Test fun loading_dark() = shot("loading", SettleUpUiState(), dark = true)
    @Test fun error_light() = shot("error", SettleUpUiState(LoadState.Error(ErrorReason.STORAGE)))

    private companion object {
        const val ROOT = "root"
    }
}
