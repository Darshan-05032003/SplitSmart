package com.darshan.splitsmart.ui.scan

import com.darshan.splitsmart.domain.model.ScanError
import com.darshan.splitsmart.testutil.testDraft
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScanLogicTest {
    @Test
    fun `the hint asks to frame the receipt until a few lines of text are in view`() {
        assertThat(ScanLogic.hint(ScanPhase.Ready, liveLines = 0)).isEqualTo(ScanHint.Frame)
        assertThat(ScanLogic.hint(ScanPhase.Ready, liveLines = ScanLogic.TEXT_FOUND_LINES - 1)).isEqualTo(ScanHint.Frame)
        assertThat(ScanLogic.hint(ScanPhase.Ready, liveLines = ScanLogic.TEXT_FOUND_LINES)).isEqualTo(ScanHint.TextFound)
        assertThat(ScanLogic.hint(ScanPhase.Capturing, liveLines = 40)).isEqualTo(ScanHint.HoldStill)
        assertThat(ScanLogic.hint(ScanPhase.Recognizing, liveLines = 40)).isEqualTo(ScanHint.None)
        assertThat(ScanLogic.hint(ScanPhase.Error(ScanError.NO_TEXT), liveLines = 0)).isEqualTo(ScanHint.None)
    }

    @Test
    fun `the preview stays up from ready through reading a photo the camera just took`() {
        assertThat(ScanLogic.showsPreview(ScanPhase.Ready, previous = false)).isTrue()
        assertThat(ScanLogic.showsPreview(ScanPhase.Capturing, previous = true)).isTrue()
        // Reading keeps whatever was on screen: the frozen camera after a capture, the plain page after a gallery pick.
        assertThat(ScanLogic.showsPreview(ScanPhase.Recognizing, previous = true)).isTrue()
        assertThat(ScanLogic.showsPreview(ScanPhase.Recognizing, previous = false)).isFalse()
        assertThat(ScanLogic.showsPreview(ScanPhase.Result(testDraft()), previous = true)).isFalse()
        assertThat(ScanLogic.showsPreview(ScanPhase.PermissionRequired(false), previous = true)).isFalse()
    }

    @Test
    fun `the window is receipt shaped and sits between the top bar and the controls`() {
        for ((w, h) in listOf(1080f to 2400f, 720f to 1280f, 1600f to 900f, 480f to 640f)) {
            val top = h * 0.12f
            val bottom = h * 0.78f
            val window = ScanLogic.window(w, h, top, bottom)
            assertThat(window.left).isAtLeast(0f)
            assertThat(window.right).isAtMost(w)
            assertThat(window.top).isAtLeast(top)
            assertThat(window.bottom).isAtMost(bottom)
            assertThat(window.height / window.width).isAtMost(ScanLogic.MAX_ASPECT + 0.001f)
            assertThat(window.width).isAtMost(w * ScanLogic.WIDTH_FRACTION + 0.5f)
            // Centred.
            assertThat(window.left).isWithin(0.5f).of(w - window.right)
        }
    }

    @Test
    fun `a squashed area never yields an inverted window`() {
        val window = ScanLogic.window(1000f, 300f, 200f, 210f)
        assertThat(window.height).isAtLeast(0f)
        assertThat(window.width).isAtLeast(0f)
    }

    @Test
    fun `the scan line eases down the window and fades at both ends`() {
        val start = ScanLogic.beam(0f)
        val middle = ScanLogic.beam(0.5f)
        val end = ScanLogic.beam(1f)
        assertThat(start.alpha).isWithin(0.001f).of(0f)
        assertThat(end.alpha).isWithin(0.001f).of(0f)
        assertThat(middle.alpha).isWithin(0.001f).of(1f)
        var last = -1f
        for (i in 0..100) {
            val y = ScanLogic.beam(i / 100f).y
            assertThat(y).isAtLeast(last)
            assertThat(y).isIn(com.google.common.collect.Range.closed(0f, 1f))
            last = y
        }
    }
}
