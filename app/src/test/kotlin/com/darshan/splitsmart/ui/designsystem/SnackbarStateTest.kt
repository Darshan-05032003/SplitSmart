package com.darshan.splitsmart.ui.designsystem

import com.darshan.splitsmart.ui.designsystem.components.SnackbarResult
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SnackbarStateTest {
    @Test
    fun `undo resolves the waiting caller once`() = runTest {
        val state = SsSnackbarState()
        val result = async { state.show("Group archived", emoji = "📦", actionLabel = "Undo") }
        runCurrent()
        val shown = state.current!!
        assertThat(shown.message).isEqualTo("Group archived")
        assertThat(shown.performAction()).isTrue()
        assertThat(shown.performAction()).isFalse()
        assertThat(shown.timeOut()).isFalse()
        assertThat(result.await()).isEqualTo(SnackbarResult.Undo)
        assertThat(state.current).isNull()
    }

    @Test
    fun `timing out makes the change final`() = runTest {
        val state = SsSnackbarState()
        val result = async { state.show("Bill deleted", actionLabel = "Undo") }
        runCurrent()
        state.current!!.timeOut()
        assertThat(result.await()).isEqualTo(SnackbarResult.TimedOut)
    }

    @Test
    fun `a newer message replaces the one on screen`() = runTest {
        val state = SsSnackbarState()
        val first = async { state.show("First") }
        runCurrent()
        val second = async { state.show("Second") }
        runCurrent()
        assertThat(first.await()).isEqualTo(SnackbarResult.Replaced)
        assertThat(state.current!!.message).isEqualTo("Second")
        state.current!!.dismiss()
        assertThat(second.await()).isEqualTo(SnackbarResult.Dismissed)
    }

    @Test
    fun `a cancelled caller takes its message off screen`() = runTest {
        val state = SsSnackbarState()
        val job = launch { state.show("Gone soon") }
        runCurrent()
        assertThat(state.current).isNotNull()
        job.cancel()
        runCurrent()
        assertThat(state.current).isNull()
    }

    @Test
    fun `a thousand messages in a row leave nothing behind`() = runTest {
        val state = SsSnackbarState()
        repeat(1_000) {
            val result = async { state.show("m$it", actionLabel = "Undo") }
            runCurrent()
            if (it % 2 == 0) state.current!!.performAction() else state.current!!.timeOut()
            result.await()
        }
        assertThat(state.current).isNull()
    }

    @Test
    fun `remaining fraction drains from one to zero`() {
        assertThat(SsSnackbarState.remainingFraction(0, 5_000)).isEqualTo(1f)
        assertThat(SsSnackbarState.remainingFraction(2_500, 5_000)).isWithin(1e-4f).of(0.5f)
        assertThat(SsSnackbarState.remainingFraction(9_000, 5_000)).isEqualTo(0f)
        assertThat(SsSnackbarState.remainingFraction(10, 0)).isEqualTo(0f)
    }
}
