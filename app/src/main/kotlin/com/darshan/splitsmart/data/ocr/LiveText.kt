package com.darshan.splitsmart.data.ocr

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where one line of text sits in the live camera preview, in the preview's own pixels. Only the rectangle leaves the analyzer:
 * the words themselves are dropped on the spot, so nothing the camera glimpses is kept, logged or sent anywhere.
 */
@Immutable
data class LiveTextBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val isUsable: Boolean get() = right > left && bottom > top
}

/** The lines of text the live preview currently sees. [frame] counts analysed frames, so the screen can tell two frames apart. */
@Immutable
data class LiveText(val boxes: List<LiveTextBox>, val frame: Long) {
    companion object {
        /** Drawing more outlines than this says nothing new and costs frames on a dense receipt. */
        const val MAX_BOXES = 80

        val NONE = LiveText(emptyList(), 0)

        fun of(boxes: List<LiveTextBox>, frame: Long): LiveText = LiveText(boxes.asSequence().filter { it.isUsable }.take(MAX_BOXES).toList(), frame)
    }
}

/**
 * Lets one camera frame through per [intervalMs], so live outlines cost a few recognitions a second instead of one per frame.
 * A clock that jumps back reopens it rather than starving the analyzer.
 */
class LiveTextThrottle(private val intervalMs: Long = DEFAULT_INTERVAL_MS) {
    private var last: Long? = null

    @Synchronized
    fun tryAcquire(nowMs: Long): Boolean {
        val previous = last
        if (previous != null && nowMs >= previous && nowMs - previous < intervalMs) return false
        last = nowMs
        return true
    }

    companion object {
        /** About four outline updates a second: lively enough to follow the receipt, light enough for a mid-range phone. */
        const val DEFAULT_INTERVAL_MS = 250L
    }
}

internal val NoLiveText: StateFlow<LiveText> = MutableStateFlow(LiveText.NONE).asStateFlow()
