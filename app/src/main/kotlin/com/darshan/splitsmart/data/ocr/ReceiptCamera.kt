package com.darshan.splitsmart.data.ocr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CameraCaptureException(cause: Throwable) : Exception(cause)

/** Takes one still picture into [target]. Wrapped so JVM tests use a fake. */
interface ReceiptCamera {
    suspend fun capture(target: File)

    /** Lines of text the live preview sees right now, as rectangles only. Empty for a camera without live recognition. */
    val liveText: StateFlow<LiveText> get() = NoLiveText

    /** Live recognition runs only while the screen is ready for a picture; off, it drops frames and clears the outlines. */
    fun setLiveTextEnabled(enabled: Boolean) = Unit
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
 *
 * Live outlines: once the preview is bound, [attachLiveText] adds an image-analysis stream read by ML Kit through CameraX's
 * [MlKitAnalyzer] in the PreviewView's own coordinates, a few frames a second ([LiveTextThrottle]). Only line rectangles leave the
 * callback. If the phone cannot run preview, capture and analysis together, CameraX restores capture-only and the outlines simply
 * never appear: taking the picture always comes first. Device-only; the JVM tests use a fake.
 */
class CameraXReceiptCamera @Inject constructor(@ApplicationContext private val context: Context) : ReceiptCamera {
    private var controller: LifecycleCameraController? = null
    private val live = MutableStateFlow(LiveText.NONE)
    override val liveText: StateFlow<LiveText> = live.asStateFlow()

    @Volatile private var liveEnabled = false
    private var liveAttached = false
    private var liveUnsupported = false
    private var recognizer: com.google.mlkit.vision.text.TextRecognizer? = null
    private var analysisExecutor: ExecutorService? = null
    private var frames = 0L

    override fun setLiveTextEnabled(enabled: Boolean) {
        liveEnabled = enabled
        if (!enabled) live.value = LiveText.NONE
    }

    /** Call on the main thread after binding the preview. Safe to call again; does nothing once the phone said no. */
    fun attachLiveText() {
        val camera = controller ?: return
        if (liveAttached || liveUnsupported) return
        liveAttached = true
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val executor = Executors.newSingleThreadExecutor()
        val throttle = LiveTextThrottle()
        val analyzer = MlKitAnalyzer(listOf(client), ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, ContextCompat.getMainExecutor(context)) { result ->
            val text = result.getValue(client)
            if (!liveEnabled || text == null) return@MlKitAnalyzer
            val boxes = text.textBlocks.asSequence().flatMap { it.lines.asSequence() }.mapNotNull { line ->
                line.boundingBox?.let { LiveTextBox(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }
            }.toList()
            live.value = LiveText.of(boxes, ++frames)
        }
        recognizer = client
        analysisExecutor = executor
        camera.initializationFuture.addListener({
            if (!liveAttached) return@addListener
            try {
                camera.setImageAnalysisBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                camera.setImageAnalysisAnalyzer(executor, ThrottledAnalyzer(analyzer) { liveEnabled && throttle.tryAcquire(SystemClock.uptimeMillis()) })
                camera.setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
            } catch (e: IllegalStateException) {
                // The phone cannot stream analysis next to preview and capture; CameraX has already restored capture-only.
                liveUnsupported = true
                detachLiveText()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** Call on the main thread before unbinding: stops the analyzer, frees the recogniser and clears the outlines. */
    fun detachLiveText() {
        liveAttached = false
        controller?.clearImageAnalysisAnalyzer()
        recognizer?.close()
        recognizer = null
        analysisExecutor?.shutdown()
        analysisExecutor = null
        live.value = LiveText.NONE
    }

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

/** Hands a frame to [delegate] only when [gate] allows; otherwise closes it at once so the camera never stalls. */
private class ThrottledAnalyzer(private val delegate: ImageAnalysis.Analyzer, private val gate: () -> Boolean) : ImageAnalysis.Analyzer {
    override fun analyze(image: ImageProxy) {
        if (gate()) delegate.analyze(image) else image.close()
    }

    override fun getDefaultTargetResolution(): Size? = delegate.defaultTargetResolution

    override fun getTargetCoordinateSystem(): Int = delegate.targetCoordinateSystem

    override fun updateTransform(matrix: Matrix?) = delegate.updateTransform(matrix)
}
