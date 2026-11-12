package com.darshan.splitsmart.ui.scan

import com.darshan.splitsmart.data.ocr.Torch

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.darshan.splitsmart.domain.model.ScanError
import com.darshan.splitsmart.domain.usecase.ScanReceiptUseCase
import com.darshan.splitsmart.testutil.FakeCameraPermission
import com.darshan.splitsmart.testutil.FakeReceiptCamera
import com.darshan.splitsmart.testutil.FakeReceiptDraftRepository
import com.darshan.splitsmart.testutil.FakeReceiptImageStore
import com.darshan.splitsmart.testutil.FakeTextRecognizer
import com.darshan.splitsmart.testutil.MainDispatcherRule
import com.darshan.splitsmart.testutil.TestDispatcherProvider
import com.darshan.splitsmart.testutil.fixedClock
import com.darshan.splitsmart.testutil.testDraft
import com.darshan.splitsmart.testutil.RecordingAnalyticsTracker
import com.darshan.splitsmart.testutil.RecordingCrashReporter
import com.darshan.splitsmart.ui.designsystem.effects.HapticEvent
import com.darshan.splitsmart.ui.designsystem.effects.UiEffect
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import kotlinx.coroutines.launch
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val mainRule = MainDispatcherRule(dispatcher)
    private val recognizer = FakeTextRecognizer()
    private val store = FakeReceiptImageStore()
    private val drafts = FakeReceiptDraftRepository()
    private val camera = FakeReceiptCamera()
    private val permission = FakeCameraPermission(granted = true)
    private val dispatchers = TestDispatcherProvider(dispatcher)

    private fun vm(handle: SavedStateHandle = SavedStateHandle(mapOf("groupId" to "g1"))) = ScanViewModel(
        savedStateHandle = handle,
        scanReceipt = ScanReceiptUseCase(recognizer, store, drafts, fixedClock, dispatchers, RecordingAnalyticsTracker(), RecordingCrashReporter()),
        imageStore = store,
        camera = camera,
        cameraPermission = permission,
        drafts = drafts,
        dispatchers = dispatchers,
    )

    @Test
    fun `starts ready when permission is granted`() = runTest(dispatcher) {
        vm().uiState.test {
            assertThat(awaitItem().phase).isEqualTo(ScanPhase.CheckingPermission)
            assertThat(awaitItem().phase).isEqualTo(ScanPhase.Ready)
        }
    }

    @Test
    fun `asks for permission when missing and honours the answer`() = runTest(dispatcher) {
        permission.granted = false
        val vm = vm()
        vm.uiState.test {
            awaitItem()
            assertThat(awaitItem().phase).isEqualTo(ScanPhase.PermissionRequired(permanentlyDenied = false))
            vm.onPermissionResult(granted = false, canAskAgain = false)
            assertThat(awaitItem().phase).isEqualTo(ScanPhase.PermissionRequired(permanentlyDenied = true))
            vm.onPermissionResult(granted = true, canAskAgain = true)
            assertThat(awaitItem().phase).isEqualTo(ScanPhase.Ready)
        }
    }

    @Test
    fun `capture goes Capturing then Recognizing then Result and persists the draft id`() = runTest(dispatcher) {
        val handle = SavedStateHandle(mapOf("groupId" to "g1"))
        val vm = vm(handle)
        vm.uiState.test {
            awaitItem(); awaitItem()
            vm.capture()
            assertThat(awaitItem().phase).isEqualTo(ScanPhase.Capturing)
            assertThat(awaitItem().phase).isEqualTo(ScanPhase.Recognizing)
            val result = awaitItem().phase as ScanPhase.Result
            assertThat(result.draft.ocrText).isEqualTo("Paneer Tikka 250.00\nTotal 250.00")
            assertThat(handle.get<String>("draftId")).isEqualTo(result.draft.id)
        }
        assertThat(camera.captures.get()).isEqualTo(1)
    }

    @Test
    fun `double tap on capture takes one picture`() = runTest(dispatcher) {
        val vm = vm()
        vm.uiState.test { awaitItem(); awaitItem(); cancelAndIgnoreRemainingEvents() }
        vm.capture(); vm.capture(); vm.capture()
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(camera.captures.get()).isEqualTo(1)
        assertThat(vm.uiState.value.phase).isInstanceOf(ScanPhase.Result::class.java)
    }

    @Test
    fun `camera failure is a CAPTURE_FAILED error and retry returns to Ready`() = runTest(dispatcher) {
        camera.failWith = IllegalStateException("no camera")
        val vm = vm()
        vm.uiState.test {
            awaitItem(); awaitItem()
            vm.capture()
            awaitItem() // Capturing
            assertThat(awaitItem().phase).isEqualTo(ScanPhase.Error(ScanError.CAPTURE_FAILED))
            vm.retry()
            assertThat(awaitItem().phase).isEqualTo(ScanPhase.Ready)
        }
    }

    @Test
    fun `gallery pick imports and recognises`() = runTest(dispatcher) {
        val vm = vm()
        vm.uiState.test {
            awaitItem(); awaitItem()
            vm.onImagePicked("content://media/1")
            assertThat(awaitItem().phase).isEqualTo(ScanPhase.Recognizing)
            assertThat(awaitItem().phase).isInstanceOf(ScanPhase.Result::class.java)
        }
        assertThat(store.imported).containsExactly("content://media/1")
    }

    @Test
    fun `gallery pick works even without camera permission`() = runTest(dispatcher) {
        permission.granted = false
        val vm = vm()
        vm.uiState.test {
            awaitItem(); awaitItem()
            vm.onImagePicked("content://media/1")
            awaitItem()
            assertThat(awaitItem().phase).isInstanceOf(ScanPhase.Result::class.java)
        }
    }

    @Test
    fun `no text error offers retry`() = runTest(dispatcher) {
        recognizer.lines = emptyList()
        val vm = vm()
        vm.uiState.test {
            awaitItem(); awaitItem()
            vm.onImagePicked("content://media/1")
            awaitItem()
            assertThat(awaitItem().phase).isEqualTo(ScanPhase.Error(ScanError.NO_TEXT))
        }
    }

    @Test
    fun `process death after a scan restores the result from the saved draft id`() = runTest(dispatcher) {
        drafts.save(testDraft("d9"))
        val vm = vm(SavedStateHandle(mapOf("groupId" to "g1", "draftId" to "d9")))
        vm.uiState.test {
            awaitItem()
            val phase = awaitItem().phase as ScanPhase.Result
            assertThat(phase.draft.id).isEqualTo("d9")
        }
    }

    @Test
    fun `rescan discards the draft and returns to Ready`() = runTest(dispatcher) {
        drafts.save(testDraft("d9"))
        val handle = SavedStateHandle(mapOf("groupId" to "g1", "draftId" to "d9"))
        val vm = vm(handle)
        vm.uiState.test {
            awaitItem(); awaitItem()
            vm.rescan()
            assertThat(awaitItem().phase).isEqualTo(ScanPhase.Ready)
        }
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(drafts.get("d9")).isNull()
        assertThat(handle.get<String>("draftId")).isNull()
    }

    @Test
    fun `an accepted shutter plays the heavy haptic once, however many taps`() = runTest(dispatcher) {
        val vm = vm()
        vm.uiState.test { awaitItem(); awaitItem(); cancelAndIgnoreRemainingEvents() }
        vm.effects.test {
            repeat(5) { vm.capture() }
            assertThat(awaitItem()).isEqualTo(UiEffect.Haptic(HapticEvent.Heavy))
            dispatcher.scheduler.advanceUntilIdle()
            // Then the finished scan hands over to the review, and nothing else arrives.
            assertThat(awaitItem()).isInstanceOf(ScanEffect.OpenReview::class.java)
            expectNoEvents()
        }
        assertThat(camera.captures.get()).isEqualTo(1)
    }

    @Test
    fun `a finished scan opens the review once with the saved draft id`() = runTest(dispatcher) {
        val handle = SavedStateHandle(mapOf("groupId" to "g1"))
        val vm = vm(handle)
        vm.uiState.test { awaitItem(); awaitItem(); cancelAndIgnoreRemainingEvents() }
        vm.effects.test {
            vm.onImagePicked("content://media/1")
            dispatcher.scheduler.advanceUntilIdle()
            val open = awaitItem() as ScanEffect.OpenReview
            assertThat(open.draftId).isEqualTo(handle.get<String>("draftId"))
            assertThat(drafts.get(open.draftId)).isNotNull()
            expectNoEvents()
        }
    }

    @Test
    fun `a scan restored after process death waits for the user instead of opening the review again`() = runTest(dispatcher) {
        drafts.save(testDraft("d9"))
        val vm = vm(SavedStateHandle(mapOf("groupId" to "g1", "draftId" to "d9")))
        vm.effects.test {
            vm.uiState.test { awaitItem(); assertThat(awaitItem().phase).isInstanceOf(ScanPhase.Result::class.java); cancelAndIgnoreRemainingEvents() }
            dispatcher.scheduler.advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun `a failed scan opens nothing and plays no shutter after the failure`() = runTest(dispatcher) {
        recognizer.lines = emptyList()
        val vm = vm()
        vm.uiState.test { awaitItem(); awaitItem(); cancelAndIgnoreRemainingEvents() }
        vm.effects.test {
            vm.onImagePicked("content://media/1")
            dispatcher.scheduler.advanceUntilIdle()
            expectNoEvents()
        }
        assertThat(vm.uiState.value.phase).isEqualTo(ScanPhase.Error(ScanError.NO_TEXT))
    }

    @Test
    fun `live text runs only while the camera is ready for a picture`() = runTest(dispatcher) {
        val vm = vm()
        vm.uiState.test {
            awaitItem()
            assertThat(awaitItem().phase).isEqualTo(ScanPhase.Ready)
            dispatcher.scheduler.advanceUntilIdle()
            assertThat(camera.liveTextRequests.last()).isTrue()
            vm.capture()
            assertThat(awaitItem().phase).isEqualTo(ScanPhase.Capturing)
            dispatcher.scheduler.advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
        // Off from the shutter on: the real OCR gets the processor to itself.
        assertThat(camera.liveTextRequests).containsExactly(false, true, false).inOrder()
    }

    @Test
    fun `live text boxes reach the screen untouched`() = runTest(dispatcher) {
        val vm = vm()
        val boxes = com.darshan.splitsmart.data.ocr.LiveText.of(listOf(com.darshan.splitsmart.data.ocr.LiveTextBox(1f, 2f, 30f, 12f)), frame = 1)
        camera.live.value = boxes
        assertThat(vm.liveText.value).isEqualTo(boxes)
    }

    @Test
    fun `the torch lights and goes out while the camera is up, and is ignored without one`() = runTest(dispatcher) {
        val vm = vm()
        vm.uiState.test {
            awaitItem(); assertThat(awaitItem().phase).isEqualTo(ScanPhase.Ready)
            vm.toggleTorch()
            assertThat(camera.torchRequests).isEmpty() // no flash unit: nothing to light, and no button shown
            camera.torchState.value = Torch.Off
            vm.toggleTorch()
            assertThat(vm.torch.value).isEqualTo(Torch.On)
            vm.toggleTorch()
            assertThat(vm.torch.value).isEqualTo(Torch.Off)
            assertThat(camera.torchRequests).containsExactly(true, false).inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the torch is left alone once the camera has gone (an error page)`() = runTest(dispatcher) {
        camera.torchState.value = Torch.Off
        camera.failWith = IllegalStateException("closed")
        val vm = vm()
        vm.uiState.test {
            awaitItem(); awaitItem()
            vm.capture()
            assertThat(awaitItem().phase).isEqualTo(ScanPhase.Capturing)
            assertThat(awaitItem().phase).isInstanceOf(ScanPhase.Error::class.java)
            vm.toggleTorch()
            assertThat(camera.torchRequests).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `opened again after a save, the state says so until the next picture, and the hand-off still happens once`() = runTest(dispatcher) {
        val handle = SavedStateHandle(mapOf("groupId" to "g1", "saved" to true))
        val vm = vm(handle)
        val opened = mutableListOf<String>()
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            vm.effects.collect { if (it is ScanEffect.OpenReview) opened += it.draftId }
        }
        vm.uiState.test {
            awaitItem()
            val ready = awaitItem()
            assertThat(ready.phase).isEqualTo(ScanPhase.Ready)
            assertThat(ready.afterSave).isTrue()
            vm.capture()
            val capturing = awaitItem()
            assertThat(capturing.phase).isEqualTo(ScanPhase.Capturing)
            assertThat(capturing.afterSave).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(handle.get<Boolean>("saved")).isFalse()
        assertThat(opened).hasSize(1)
    }
}
