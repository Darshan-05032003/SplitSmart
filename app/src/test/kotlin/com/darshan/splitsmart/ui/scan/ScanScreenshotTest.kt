package com.darshan.splitsmart.ui.scan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.data.ocr.LiveText
import com.darshan.splitsmart.data.ocr.LiveTextBox
import com.darshan.splitsmart.domain.model.ScanError
import com.darshan.splitsmart.testutil.testDraft
import com.darshan.splitsmart.ui.designsystem.captureNode
import com.darshan.splitsmart.ui.designsystem.commitState
import com.darshan.splitsmart.ui.designsystem.setDs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The scan flow's states. The camera is the night palette in both app themes, so its "light" and "dark" baselines differ only
 * where the app theme leaks through (it must not). The preview is a painted stand-in for a photo of a receipt on a table.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ScanScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val phase = mutableStateOf<ScanPhase>(ScanPhase.Ready)
    private val live = mutableStateOf(LiveText.NONE)
    private val torch = mutableStateOf(com.darshan.splitsmart.data.ocr.Torch.Unavailable)
    private val noop = ScanCallbacks({}, {}, {}, {}, {}, {}, {}, {})

    private fun shot(name: String, dark: Boolean = false, fontScale: Float = 1f, reduced: Boolean = false, settle: Long = 1_500, act: () -> Unit = {}) {
        rule.mainClock.autoAdvance = false
        rule.setDs(dark = dark, fontScale = fontScale, reduceMotion = reduced) {
            Box(Modifier.fillMaxSize().testTag(ROOT)) {
                ScanScreen(ScanUiState(phase.value, "g1"), preview = { FakePhoto() }, callbacks = noop, liveText = live, torch = torch)
            }
        }
        rule.mainClock.advanceTimeBy(settle)
        act()
        rule.onNodeWithTag(ROOT).captureNode("scan_${name}_${if (dark) "dark" else "light"}${if (fontScale != 1f) "_font130" else ""}${if (reduced) "_reduced" else ""}")
    }

    /** Lines of the painted receipt, in preview pixels (411 × 891 dp at 2×). */
    private fun receiptLines() = LiveText.of(
        (0 until 11).map { i ->
            val top = 560f + i * 66f
            LiveTextBox(250f, top, if (i % 3 == 0) 590f else 560f, top + 34f)
        } + LiveTextBox(250f, 1340f, 580f, 1384f),
        frame = 1,
    )

    @Test fun camera_light() = shot("camera")
    @Test fun camera_dark() = shot("camera", dark = true)
    @Test fun camera_font130() = shot("camera", fontScale = 1.3f)
    @Test fun camera_reduced() = shot("camera", reduced = true)

    /** A phone with a flash unit: the torch balances the gallery (row 17). */
    @Test
    fun camera_torch_off_light() {
        torch.value = com.darshan.splitsmart.data.ocr.Torch.Off
        shot("camera_torch_off")
    }

    @Test
    fun camera_torch_on_dark() {
        torch.value = com.darshan.splitsmart.data.ocr.Torch.On
        shot("camera_torch_on", dark = true)
    }

    @Test
    fun camera_live_text_light() = shot("camera_live_text", act = {
        commitState { live.value = receiptLines() }
        rule.mainClock.advanceTimeBy(600)
    })

    @Test
    fun camera_flash_light() = shot("camera_flash", act = {
        commitState { phase.value = ScanPhase.Capturing }
        rule.mainClock.advanceTimeBy(64)
    })

    @Test
    fun reading_light() = shot("reading", act = {
        commitState { phase.value = ScanPhase.Capturing }
        rule.mainClock.advanceTimeBy(300)
        commitState { phase.value = ScanPhase.Recognizing }
        rule.mainClock.advanceTimeBy(900)
    })

    @Test
    fun reading_gallery_dark() {
        phase.value = ScanPhase.Recognizing
        shot("reading_gallery", dark = true)
    }

    @Test
    fun permission_light() {
        phase.value = ScanPhase.PermissionRequired(permanentlyDenied = false)
        shot("permission")
    }

    @Test
    fun permission_font130() {
        phase.value = ScanPhase.PermissionRequired(permanentlyDenied = false)
        shot("permission", fontScale = 1.3f)
    }

    @Test
    fun permission_denied_dark() {
        phase.value = ScanPhase.PermissionRequired(permanentlyDenied = true)
        shot("permission_denied", dark = true)
    }

    @Test
    fun error_light() {
        phase.value = ScanPhase.Error(ScanError.NO_TEXT)
        shot("error")
    }

    @Test
    fun result_dark() {
        phase.value = ScanPhase.Result(
            testDraft(text = "Hotel Marigold Bhavan\nPaneer Tikka 250.00\nButter Naan x2 90.00\nMasala Chaas 60.00\nSub Total 400.00\nCGST 2.5% 10.00\nSGST 2.5% 10.00\nService 20.00\nTOTAL 440.00\nThank you, visit again"),
        )
        shot("result", dark = true)
    }

    private companion object {
        const val ROOT = "root"
    }
}

/** A painted stand-in for the live picture: a receipt lying on a dark table. Test-only colours. */
@Composable
private fun FakePhoto() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Color(0xFF3A2C24))
        rotate(-2f) {
            val left = size.width * 0.27f
            val top = size.height * 0.27f
            drawRect(Color(0xFFF4F1EA), Offset(left, top), Size(size.width * 0.46f, size.height * 0.52f))
            for (i in 0 until 11) {
                val y = top + 66f * (i + 1)
                drawLine(Color(0xFF6D6A66), Offset(left + 40f, y), Offset(left + if (i % 3 == 0) 380f else 300f, y), 14f, StrokeCap.Round)
            }
        }
    }
}
