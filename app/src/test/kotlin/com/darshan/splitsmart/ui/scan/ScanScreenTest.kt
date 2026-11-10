package com.darshan.splitsmart.ui.scan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.data.ocr.LiveText
import com.darshan.splitsmart.data.ocr.LiveTextBox
import com.darshan.splitsmart.domain.model.ScanError
import com.darshan.splitsmart.testutil.testDraft
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.designsystem.assertInside
import com.darshan.splitsmart.ui.designsystem.commitState
import com.darshan.splitsmart.ui.designsystem.modifiers.PressFeedback
import com.darshan.splitsmart.ui.designsystem.modifiers.rememberPressFeedback
import com.darshan.splitsmart.ui.designsystem.recompositionCycles
import com.darshan.splitsmart.ui.designsystem.setDs
import com.darshan.splitsmart.ui.navigation.LocalStatusBarController
import com.darshan.splitsmart.ui.navigation.StatusBarController
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The camera screen's chrome, states and feedback, with a fake preview (CameraX does not run on the JVM). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ScanScreenTest {
    @get:Rule val rule = createComposeRule()

    private class Calls {
        var captures = 0
        var gallery = 0
        var permission = 0
        var settings = 0
        var retry = 0
        var rescan = 0
        var continued = 0
        var back = 0
    }

    private fun callbacks(calls: Calls) = ScanCallbacks(
        onBack = { calls.back++ }, onCapture = { calls.captures++ }, onPickImage = { calls.gallery++ },
        onRequestPermission = { calls.permission++ }, onOpenSettings = { calls.settings++ }, onRetry = { calls.retry++ },
        onRescan = { calls.rescan++ }, onContinue = { calls.continued++ },
    )

    private val live = mutableStateOf(LiveText.NONE)
    private val phase = mutableStateOf<ScanPhase>(ScanPhase.Ready)

    /** The clock is paused before content: the corners breathe and the scan line sweeps forever. */
    private fun show(calls: Calls = Calls(), reduceMotion: Boolean = false, fontScale: Float = 1f, dark: Boolean = false, statusBar: StatusBarController? = null) {
        rule.mainClock.autoAdvance = false
        rule.setDs(reduceMotion = reduceMotion, fontScale = fontScale, dark = dark) {
            CompositionLocalProvider(LocalStatusBarController provides statusBar) {
                Box(Modifier.fillMaxSize().testTag(ROOT)) {
                    ScanScreen(ScanUiState(phase.value, "g1"), preview = { Box(Modifier.fillMaxSize().testTag(PREVIEW)) }, callbacks = callbacks(calls), liveText = live)
                }
            }
        }
        rule.mainClock.advanceTimeBy(FRAME * 4)
    }

    private fun lines(count: Int) = LiveText.of((0 until count).map { LiveTextBox(200f, 500f + it * 60f, 600f, 540f + it * 60f) }, frame = count.toLong())

    @Test
    fun `ready shows the live preview, the frame hint, the gallery and one shutter`() {
        val calls = Calls()
        show(calls)
        rule.onNodeWithTag(PREVIEW).assertExists()
        rule.onNodeWithText("Scan a receipt").assertIsDisplayed()
        rule.onNodeWithText("Fit the whole receipt in the frame").assertIsDisplayed()
        rule.onNodeWithTag(ScanTags.VIEWFINDER).assertContentDescriptionEquals("Lines of text the camera can see")
        rule.onNodeWithContentDescription("Pick from gallery").performClick()
        assertThat(calls.gallery).isEqualTo(1)
        // A burst of taps inside the shutter's tap window takes one picture.
        repeat(10) { rule.onNodeWithTag(SplitSmartTestTags.CAPTURE_BUTTON).performClick() }
        assertThat(calls.captures).isEqualTo(1)
        rule.onNodeWithContentDescription("Back").performClick()
        assertThat(calls.back).isEqualTo(1)
    }

    @Test
    fun `the hint says text was found once a few lines are in view, and the outlines never recompose the screen`() {
        show()
        commitState { live.value = lines(ScanLogic.TEXT_FOUND_LINES - 1) }
        rule.mainClock.advanceTimeBy(FRAME * 2)
        rule.onNodeWithText("Fit the whole receipt in the frame").assertIsDisplayed()
        commitState { live.value = lines(ScanLogic.TEXT_FOUND_LINES) }
        rule.mainClock.advanceTimeBy(FRAME * 30)
        rule.onNodeWithText("Text found · hold steady").assertIsDisplayed()
        // Thirty more analysed frames with text in view: drawn, not recomposed.
        val before = recompositionCycles()
        for (frame in 0 until 30) {
            commitState { live.value = lines(ScanLogic.TEXT_FOUND_LINES + frame % 7) }
            rule.mainClock.advanceTimeBy(FRAME)
        }
        assertThat(recompositionCycles() - before).isLessThan(4L)
    }

    @Test
    fun `the shutter flashes, then fades, and the controls step back while the picture is taken`() {
        show()
        commitState { phase.value = ScanPhase.Capturing }
        rule.mainClock.advanceTimeBy(FRAME * 2)
        rule.onNodeWithTag(ScanTags.FLASH).assertExists()
        rule.onNodeWithText("Hold still…").assertIsDisplayed()
        rule.onNodeWithTag(SplitSmartTestTags.CAPTURE_BUTTON).assertIsNotEnabled()
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithTag(ScanTags.FLASH).assertDoesNotExist()
        // The preview was never torn down during the capture.
        rule.onNodeWithTag(PREVIEW).assertExists()
    }

    @Test
    fun `under reduced motion the flash still marks the shutter, briefly`() {
        show(reduceMotion = true)
        commitState { phase.value = ScanPhase.Capturing }
        rule.mainClock.advanceTimeBy(FRAME * 2)
        rule.onNodeWithTag(ScanTags.FLASH).assertExists()
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithTag(ScanTags.FLASH).assertDoesNotExist()
    }

    @Test
    fun `the shutter squashes from the first frames of a press`() {
        lateinit var press: PressFeedback
        rule.mainClock.autoAdvance = false
        rule.setDs {
            press = rememberPressFeedback()
            ShutterButton(enabled = true, onClick = {}, modifier = Modifier.testTag(SHUTTER), press = press)
        }
        rule.onNodeWithTag(SHUTTER).performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(FRAME * 2)
        assertThat(press.progress).isGreaterThan(0f)
        rule.onNodeWithTag(SHUTTER).performTouchInput { up() }
        rule.mainClock.advanceTimeBy(1_000)
        assertThat(press.progress).isWithin(0.01f).of(0f)
    }

    @Test
    fun `reading a photo keeps the frozen preview, dims it and tells TalkBack it is working`() {
        show()
        commitState { phase.value = ScanPhase.Capturing }
        rule.mainClock.advanceTimeBy(FRAME * 2)
        commitState { phase.value = ScanPhase.Recognizing }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithTag(PREVIEW).assertExists()
        rule.onNodeWithTag(ScanTags.READING)
            .assertContentDescriptionEquals("Reading the receipt…")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate))
        rule.onNodeWithText("Text is read on this phone. The photo never leaves it.").assertExists()
    }

    @Test
    fun `a gallery photo is read on the plain page, without a camera behind it`() {
        phase.value = ScanPhase.Recognizing
        show()
        rule.onNodeWithTag(PREVIEW).assertDoesNotExist()
        rule.onNodeWithTag(ScanTags.READING).assertExists()
    }

    @Test
    fun `the permission ask explains what stays on the phone and offers the gallery as an equal way in`() {
        val calls = Calls()
        phase.value = ScanPhase.PermissionRequired(permanentlyDenied = false)
        show(calls)
        rule.onNodeWithTag(PREVIEW).assertDoesNotExist()
        rule.onNodeWithText("Show SplitSmart the bill").assertIsDisplayed()
        rule.onNodeWithText("only that text goes to Gemini", substring = true).assertIsDisplayed()
        // The Baseline Profile journey waits for this exact label.
        rule.onNodeWithText("Allow camera", substring = true).assertIsDisplayed()
        rule.onNodeWithTag(SplitSmartTestTags.GRANT_PERMISSION_BUTTON).performClick()
        rule.onNodeWithTag(SplitSmartTestTags.GALLERY_BUTTON).performClick()
        assertThat(calls.permission).isEqualTo(1)
        assertThat(calls.gallery).isEqualTo(1)
    }

    @Test
    fun `a permanent no sends the user to Settings instead of asking again`() {
        val calls = Calls()
        phase.value = ScanPhase.PermissionRequired(permanentlyDenied = true)
        show(calls)
        rule.onNodeWithTag(SplitSmartTestTags.GRANT_PERMISSION_BUTTON).assertDoesNotExist()
        rule.onNodeWithTag(SplitSmartTestTags.OPEN_SETTINGS_BUTTON).performClick()
        assertThat(calls.settings).isEqualTo(1)
    }

    @Test
    fun `errors say what went wrong and offer a retry and the gallery`() {
        val calls = Calls()
        phase.value = ScanPhase.Error(ScanError.NO_TEXT)
        show(calls)
        rule.onNodeWithTag(SplitSmartTestTags.ERROR).assertExists()
        rule.onNodeWithText("No text found. Try better light or move closer.").assertIsDisplayed()
        rule.onNodeWithText("Try again").performClick()
        rule.onNodeWithTag(SplitSmartTestTags.GALLERY_BUTTON).performClick()
        assertThat(calls.retry).isEqualTo(1)
        assertThat(calls.gallery).isEqualTo(1)
    }

    @Test
    fun `a saved scan shows what the camera read and leads to the review`() {
        val calls = Calls()
        val text = (1..12).joinToString("\n") { "Line $it 10.00" }
        phase.value = ScanPhase.Result(testDraft(text = text))
        show(calls)
        rule.onNodeWithTag(SplitSmartTestTags.OCR_TEXT).assertIsDisplayed()
        rule.onNodeWithText("Line 1 10.00").assertIsDisplayed()
        rule.onNodeWithText("and 4 more lines").assertIsDisplayed()
        rule.onNodeWithText("12 lines read on this phone and saved as a draft for this group.").assertIsDisplayed()
        rule.onNodeWithTag(SplitSmartTestTags.CONTINUE_BUTTON).performClick()
        rule.onNodeWithTag(SplitSmartTestTags.RESCAN_BUTTON).performClick()
        assertThat(calls.continued).isEqualTo(1)
        assertThat(calls.rescan).isEqualTo(1)
    }

    @Test
    fun `the saved scan's slip shows OCR lines cleaned of direction overrides`() {
        phase.value = ScanPhase.Result(testDraft(text = "Chai\u202E 00.05\u202C\nTotal 50.00"))
        show()
        rule.onNodeWithText("Chai 00.05").assertIsDisplayed()
        rule.onNodeWithText("Total 50.00").assertIsDisplayed()
    }

    @Test
    fun `at 1_3x text the ask and its buttons stay on screen`() {
        phase.value = ScanPhase.PermissionRequired(permanentlyDenied = false)
        show(fontScale = 1.3f)
        rule.onNodeWithTag(SplitSmartTestTags.GRANT_PERMISSION_BUTTON).assertInside(rule.onNodeWithTag(ROOT))
        rule.onNodeWithTag(SplitSmartTestTags.GALLERY_BUTTON).assertInside(rule.onNodeWithTag(ROOT))
    }

    @Test
    fun `at 1_3x text the camera controls stay on screen`() {
        show(fontScale = 1.3f)
        rule.onNodeWithTag(SplitSmartTestTags.CAPTURE_BUTTON).assertInside(rule.onNodeWithTag(ROOT))
        rule.onNodeWithTag(SplitSmartTestTags.GALLERY_BUTTON).assertInside(rule.onNodeWithTag(ROOT))
        rule.onNodeWithTag(ScanTags.HINT).assertInside(rule.onNodeWithTag(ROOT))
    }

    @Test
    fun `the camera owns the status bar with light icons, even in the light theme`() {
        val controller = StatusBarController()
        show(dark = false, statusBar = controller)
        assertThat(controller.size).isEqualTo(1)
        assertThat(controller.darkIcons(themeDark = false)).isFalse()
    }

    private companion object {
        const val ROOT = "root"
        const val PREVIEW = "fake_preview"
        const val SHUTTER = "shutter"
        const val FRAME = 16L
    }
}
