package com.darshan.splitsmart.ui.scan

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
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
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
        scanReceipt = ScanReceiptUseCase(recognizer, store, drafts, fixedClock, dispatchers),
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
}
