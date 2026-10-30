package com.darshan.splitsmart.ui.scan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darshan.splitsmart.core.DispatcherProvider
import com.darshan.splitsmart.data.ocr.CameraPermission
import com.darshan.splitsmart.data.ocr.ReceiptCamera
import com.darshan.splitsmart.data.ocr.ReceiptImageStore
import com.darshan.splitsmart.domain.model.ReceiptDraft
import com.darshan.splitsmart.domain.model.ScanError
import com.darshan.splitsmart.domain.repository.ReceiptDraftRepository
import com.darshan.splitsmart.domain.usecase.ScanOutcome
import com.darshan.splitsmart.domain.usecase.ScanReceiptUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface ScanPhase {
    data object CheckingPermission : ScanPhase
    data class PermissionRequired(val permanentlyDenied: Boolean) : ScanPhase
    data object Ready : ScanPhase
    data object Capturing : ScanPhase
    data object Recognizing : ScanPhase
    data class Result(val draft: ReceiptDraft) : ScanPhase
    data class Error(val error: ScanError) : ScanPhase
}

data class ScanUiState(val phase: ScanPhase, val groupId: String)

/**
 * Camera / gallery → OCR → saved draft. The draft id is kept in [SavedStateHandle], so after process death the
 * recognised text comes back from Room instead of forcing a re-scan.
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val scanReceipt: ScanReceiptUseCase,
    private val imageStore: ReceiptImageStore,
    val camera: ReceiptCamera,
    private val cameraPermission: CameraPermission,
    private val drafts: ReceiptDraftRepository,
    dispatchers: DispatcherProvider,
) : ViewModel() {
    val groupId: String = checkNotNull(savedStateHandle[ARG_GROUP_ID]) { "groupId missing" }
    private val phase = MutableStateFlow<ScanPhase>(ScanPhase.CheckingPermission)

    val uiState: StateFlow<ScanUiState> = phase.map { ScanUiState(it, groupId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScanUiState(ScanPhase.CheckingPermission, groupId))

    init {
        viewModelScope.launch {
            val restored = savedStateHandle.get<String>(KEY_DRAFT_ID)?.let { id -> runCatching { drafts.get(id) }.getOrNull() }
            if (restored != null) {
                phase.value = ScanPhase.Result(restored)
            } else {
                savedStateHandle[KEY_DRAFT_ID] = null
                phase.value = readyOrPermission()
            }
        }
    }

    private fun readyOrPermission(): ScanPhase =
        if (cameraPermission.hasCameraPermission()) ScanPhase.Ready else ScanPhase.PermissionRequired(permanentlyDenied = false)

    fun onPermissionResult(granted: Boolean, canAskAgain: Boolean) {
        if (phase.value is ScanPhase.Capturing || phase.value is ScanPhase.Recognizing) return
        phase.value = if (granted) ScanPhase.Ready else ScanPhase.PermissionRequired(permanentlyDenied = !canAskAgain)
    }

    fun capture() {
        if (phase.value != ScanPhase.Ready) return
        phase.value = ScanPhase.Capturing
        viewModelScope.launch {
            val file = imageStore.newCaptureFile()
            try {
                camera.capture(file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                phase.value = ScanPhase.Error(ScanError.CAPTURE_FAILED)
                return@launch
            }
            phase.value = ScanPhase.Recognizing
            finish(scanReceipt.recognize(groupId, file))
        }
    }

    fun onImagePicked(sourceUri: String) {
        if (phase.value is ScanPhase.Capturing || phase.value is ScanPhase.Recognizing) return
        phase.value = ScanPhase.Recognizing
        viewModelScope.launch { finish(scanReceipt.importAndRecognize(groupId, sourceUri)) }
    }

    private fun finish(outcome: ScanOutcome) {
        phase.value = when (outcome) {
            is ScanOutcome.Success -> { savedStateHandle[KEY_DRAFT_ID] = outcome.draft.id; ScanPhase.Result(outcome.draft) }
            is ScanOutcome.Failure -> ScanPhase.Error(outcome.error)
        }
    }

    fun retry() {
        if (phase.value is ScanPhase.Error) phase.value = readyOrPermission()
    }

    /** Throws the current draft (and its image) away and goes back to the camera. */
    fun rescan() {
        val current = phase.value as? ScanPhase.Result ?: return
        viewModelScope.launch {
            runCatching { drafts.delete(current.draft.id) }
            current.draft.imagePath?.let { path -> imageStore.resolveOwnedFile(File(path).name)?.let { runCatching { imageStore.delete(it) } } }
            savedStateHandle[KEY_DRAFT_ID] = null
            phase.value = readyOrPermission()
        }
    }

    companion object {
        const val ARG_GROUP_ID = "groupId"
        private const val KEY_DRAFT_ID = "draftId"
    }
}
