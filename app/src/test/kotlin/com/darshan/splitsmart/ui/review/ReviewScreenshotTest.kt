package com.darshan.splitsmart.ui.review

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.domain.model.ExtractionSource
import com.darshan.splitsmart.domain.model.ExtractionWarning
import com.darshan.splitsmart.domain.model.LlmFailure
import com.darshan.splitsmart.testutil.testMember
import com.darshan.splitsmart.ui.designsystem.captureNode
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.darshan.splitsmart.ui.designsystem.setDs
import com.darshan.splitsmart.ui.designsystem.motion.MotionDurations
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The review's states, light and dark, at 1.3× text, plus a frame from the middle of the line-by-line reveal. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ReviewScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val noop = ReviewCallbacks({}, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {}, {})
    private val rows = listOf(
        ItemRow("a", "Paneer Tikka", "1", "250.00"),
        ItemRow("b", "Butter Naan", "2", "90.00"),
        ItemRow("c", "Masala Chaas", "1", "60.00"),
        ItemRow("d", "Gulab Jamun", "3", "120.00"),
    )
    private val members = listOf(testMember("m1", name = "Asha"), testMember("m2", name = "Ravi"), testMember("m3", name = "Darshan"))
    private val ai = ReviewForm(title = "Dinner at Hotel Marigold Bhavan", items = rows, tax = "26.00", tip = "", total = "546.00", payerId = "m1", source = ExtractionSource.LLM)
    private fun editing(form: ReviewForm = ai, mismatch: Long? = 0) =
        ReviewUiState(phase = ReviewPhase.EDITING, form = form, subtotalPaise = 52_000, mismatchPaise = mismatch, members = members)

    private val state = mutableStateOf(ReviewUiState())

    private fun shot(name: String, dark: Boolean = false, fontScale: Float = 1f, settle: Long = 1_500, act: () -> Unit = {}) {
        rule.mainClock.autoAdvance = false
        rule.setDs(dark = dark, fontScale = fontScale) {
            Box(Modifier.fillMaxSize().testTag(ROOT)) { ReviewScreen(state.value, SsSnackbarState(), noop) }
        }
        rule.mainClock.advanceTimeBy(settle)
        act()
        rule.onNodeWithTag(ROOT).captureNode("review_${name}_${if (dark) "dark" else "light"}${if (fontScale != 1f) "_font130" else ""}")
    }

    @Test fun reading_light() { state.value = ReviewUiState(phase = ReviewPhase.EXTRACTING); shot("reading") }
    @Test fun reading_dark() { state.value = ReviewUiState(phase = ReviewPhase.EXTRACTING); shot("reading", dark = true) }
    @Test fun ai_light() { state.value = editing(); shot("ai") }
    @Test fun ai_dark() { state.value = editing(); shot("ai", dark = true) }
    @Test fun ai_font130() { state.value = editing(); shot("ai", fontScale = 1.3f) }

    @Test
    fun reveal_midway_light() {
        state.value = editing().copy(reveal = ItemReveal(1, rows.map { it.key }))
        // Row 0 done, row 1 typing, rows 2 and 3 still skeletons.
        shot("reveal_midway", settle = MotionDurations.Micro.toLong() + MotionDurations.SmallSlow / 2L)
    }

    @Test
    fun refining_light() {
        state.value = editing(ai.copy(source = ExtractionSource.REGEX)).copy(refining = true)
        shot("refining")
    }

    @Test
    fun fallback_key_missing_dark() {
        state.value = editing(ai.copy(source = ExtractionSource.REGEX, llmFailure = LlmFailure.KEY_MISSING, warnings = setOf(ExtractionWarning.TOTAL_DERIVED)))
        shot("fallback_key_missing", dark = true)
    }

    @Test
    fun fallback_offline_light() {
        state.value = editing(ai.copy(source = ExtractionSource.REGEX, llmFailure = LlmFailure.OFFLINE))
        shot("fallback_offline")
    }

    @Test
    fun ai_offer_light() {
        state.value = editing(ai.copy(source = ExtractionSource.REGEX)).copy(pendingAi = ai)
        shot("ai_offer")
    }

    @Test
    fun row_open_light() {
        state.value = editing()
        shot("row_open", act = {
            rule.onNodeWithContentDescription("Butter Naan, quantity 2, ₹90.00").performClick()
            rule.mainClock.advanceTimeBy(1_500)
        })
    }

    @Test
    fun row_errors_dark() {
        state.value = editing(ai.copy(items = rows + ItemRow("e", "", "1", "")))
        shot("row_errors", dark = true, act = {
            state.value = state.value.copy(errors = ReviewErrors(items = mapOf("e" to setOf(ItemError.NAME, ItemError.AMOUNT))))
            rule.mainClock.advanceTimeBy(1_500)
        })
    }

    @Test
    fun totals_mismatch_light() {
        state.value = editing(mismatch = 2_000)
        shot("totals_mismatch", act = {
            rule.onNodeWithTag(ReviewTags.LIST).performScrollToKey("totals")
            rule.mainClock.advanceTimeBy(1_000)
        })
    }

    @Test
    fun totals_adds_up_dark() {
        state.value = editing()
        shot("totals_adds_up", dark = true, act = {
            rule.onNodeWithTag(ReviewTags.LIST).performScrollToKey("totals")
            rule.mainClock.advanceTimeBy(1_000)
        })
    }

    private companion object {
        const val ROOT = "root"
    }
}
