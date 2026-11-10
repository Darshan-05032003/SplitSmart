package com.darshan.splitsmart.data.ocr

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LiveTextTest {
    @Test
    fun `the throttle lets one frame through per interval`() {
        val throttle = LiveTextThrottle(intervalMs = 250)
        assertThat(throttle.tryAcquire(1_000)).isTrue()
        assertThat(throttle.tryAcquire(1_100)).isFalse()
        assertThat(throttle.tryAcquire(1_249)).isFalse()
        assertThat(throttle.tryAcquire(1_250)).isTrue()
        assertThat(throttle.tryAcquire(1_251)).isFalse()
    }

    @Test
    fun `a clock that jumps back reopens the throttle instead of starving it`() {
        val throttle = LiveTextThrottle(intervalMs = 250)
        assertThat(throttle.tryAcquire(10_000)).isTrue()
        assertThat(throttle.tryAcquire(5_000)).isTrue()
        assertThat(throttle.tryAcquire(5_100)).isFalse()
    }

    @Test
    fun `boxes keep only real areas, never text, and stop at the cap`() {
        val boxes = (0 until 500).map { LiveTextBox(0f, it * 10f, 100f, it * 10f + 8f) } +
            LiveTextBox(5f, 5f, 5f, 20f) + // zero width
            LiveTextBox(5f, 30f, 50f, 10f) // upside down
        val live = LiveText.of(boxes, frame = 3)
        assertThat(live.boxes).hasSize(LiveText.MAX_BOXES)
        assertThat(live.boxes.all { it.right > it.left && it.bottom > it.top }).isTrue()
        assertThat(live.frame).isEqualTo(3)
        // The model has nowhere to put recognised words: only rectangles leave the analyzer.
        val instanceFields = LiveTextBox::class.java.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
        assertThat(instanceFields.map { it.type }.toSet()).containsExactly(Float::class.javaPrimitiveType)
    }

    @Test
    fun `no live text by default`() {
        assertThat(LiveText.NONE.boxes).isEmpty()
        val camera = object : ReceiptCamera {
            override suspend fun capture(target: java.io.File) = Unit
        }
        assertThat(camera.liveText.value).isEqualTo(LiveText.NONE)
        camera.setLiveTextEnabled(true) // a camera without live text ignores the request
        assertThat(camera.liveText.value).isEqualTo(LiveText.NONE)
    }
}
