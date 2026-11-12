package com.darshan.splitsmart.ui.scan

import androidx.compose.ui.geometry.Rect
import com.darshan.splitsmart.ui.designsystem.motion.MotionEasing
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** What the camera's hint pill says. */
enum class ScanHint { Frame, Saved, TextFound, HoldStill, None }

/** One frame of the scan line: [y] from the window's top (0) to its bottom (1), and how strongly it shows. */
data class BeamFrame(val y: Float, val alpha: Float)

/** The camera screen's decisions, kept pure so the JVM tests cover them without a camera. */
object ScanLogic {
    /** This many lines of text in view and the hint says the receipt is readable. */
    const val TEXT_FOUND_LINES = 3

    /** The window is at most this much of the screen's width... */
    const val WIDTH_FRACTION = 0.84f

    /** ...and at most this tall for its width: receipts are long, but a window taller than this stops looking like one. */
    const val MAX_ASPECT = 1.6f

    /** Share of the free height between the top bar and the controls the window may use. */
    private const val HEIGHT_FRACTION = 0.94f

    /** [afterSave]: just back from saving a bill; until text is found, the pill says it was saved and asks for the next one. */
    fun hint(phase: ScanPhase, liveLines: Int, afterSave: Boolean = false): ScanHint = when (phase) {
        ScanPhase.Ready -> when {
            liveLines >= TEXT_FOUND_LINES -> ScanHint.TextFound
            afterSave -> ScanHint.Saved
            else -> ScanHint.Frame
        }
        ScanPhase.Capturing -> ScanHint.HoldStill
        else -> ScanHint.None
    }

    /**
     * Whether the live preview is on screen. From Ready through the shutter it is; while a photo is read it stays only if it was
     * already up (a capture), so a gallery pick reads on the plain page.
     */
    fun showsPreview(phase: ScanPhase, previous: Boolean): Boolean = when (phase) {
        ScanPhase.Ready, ScanPhase.Capturing -> true
        ScanPhase.Recognizing -> previous
        else -> false
    }

    /** The receipt-shaped window, centred in the free band between [top] and [bottom] of a [width] × [height] screen. */
    fun window(width: Float, height: Float, top: Float, bottom: Float): Rect {
        val band = max(0f, min(bottom, height) - max(top, 0f))
        val windowWidth = width * WIDTH_FRACTION
        val windowHeight = min(band * HEIGHT_FRACTION, windowWidth * MAX_ASPECT)
        val finalWidth = min(windowWidth, if (windowHeight > 0f) windowHeight / MIN_ASPECT else 0f).coerceAtLeast(0f)
        val left = (width - finalWidth) / 2f
        val y = max(top, 0f) + (band - windowHeight) / 2f
        return Rect(left, y, left + finalWidth, y + windowHeight)
    }

    /** A wide, short band (a landscape phone) still frames a portrait-ish window. */
    private const val MIN_ASPECT = 0.75f

    /** The scan line at [progress] (0 to 1) of one pass: it eases down the window and fades in and out at the edges. */
    fun beam(progress: Float): BeamFrame {
        val p = progress.coerceIn(0f, 1f)
        return BeamFrame(y = MotionEasing.Standard.curve.transform(p), alpha = sin(p * PI).toFloat())
    }
}
