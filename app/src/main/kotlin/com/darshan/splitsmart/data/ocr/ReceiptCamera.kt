package com.darshan.splitsmart.data.ocr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CameraCaptureException(cause: Throwable) : Exception(cause)

/** Takes one still picture into [target]. Wrapped so JVM tests use a fake. */
interface ReceiptCamera {
    suspend fun capture(target: File)
}

interface CameraPermission {
    fun hasCameraPermission(): Boolean
}

class AndroidCameraPermission @Inject constructor(@ApplicationContext private val context: Context) : CameraPermission {
    override fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}

/**
 * CameraX [LifecycleCameraController] bound to the scan screen's PreviewView (see `ui/scan/CameraPreview`).
 * Device-only; compile-only in this build (no device / emulator was available).
 */
class CameraXReceiptCamera @Inject constructor(@ApplicationContext private val context: Context) : ReceiptCamera {
    private var controller: LifecycleCameraController? = null

    /** Created lazily on first preview so constructing the ViewModel never touches camera hardware. */
    fun controller(): LifecycleCameraController = controller ?: LifecycleCameraController(context).apply {
        setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
    }.also { controller = it }

    override suspend fun capture(target: File) {
        val camera = controller ?: throw CameraCaptureException(IllegalStateException("camera preview not attached"))
        val options = ImageCapture.OutputFileOptions.Builder(target).build()
        suspendCancellableCoroutine { cont ->
            camera.takePicture(
                options,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) = cont.resume(Unit)
                    override fun onError(exception: ImageCaptureException) = cont.resumeWithException(CameraCaptureException(exception))
                },
            )
        }
    }
}
