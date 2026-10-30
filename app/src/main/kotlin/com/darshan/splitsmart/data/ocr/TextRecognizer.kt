package com.darshan.splitsmart.data.ocr

import android.content.Context
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * One recognised line with its position so the receipt can be re-assembled in reading order. [width]/[height] = 0
 * means "size unknown"; [angle] is ML Kit's line rotation in degrees (clockwise positive).
 */
data class OcrLine(val text: String, val top: Int, val left: Int, val width: Int = 0, val height: Int = 0, val angle: Float = 0f)

data class OcrResult(val lines: List<OcrLine>) {
    /** Lines in reading order, with the columns of one printed row joined back together (see [OcrLayout]). */
    val text: String get() = OcrLayout.assemble(lines)
}

class RecognitionException(cause: Throwable) : Exception(cause)

/** On-device OCR. Wrapped so JVM tests use a fake; the real implementation needs a device. */
interface TextRecognizer {
    suspend fun recognize(image: File): OcrResult
}

/** ML Kit Text Recognition v2 (Latin model bundled in the APK, fully offline). Device-only; compile-only in this build. */
class MlKitTextRecognizer @Inject constructor(@ApplicationContext private val context: Context) : TextRecognizer {
    override suspend fun recognize(image: File): OcrResult {
        val input = try {
            InputImage.fromFilePath(context, android.net.Uri.fromFile(image))
        } catch (e: Exception) {
            throw RecognitionException(e)
        }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            suspendCancellableCoroutine { cont ->
                recognizer.process(input)
                    .addOnSuccessListener { text ->
                        val lines = text.textBlocks.flatMap { block -> block.lines }.mapIndexed { i, line ->
                            val box = line.boundingBox
                            OcrLine(
                                line.text, top = box?.top ?: i, left = box?.left ?: 0,
                                width = box?.width() ?: 0, height = box?.height() ?: 0, angle = line.angle,
                            )
                        }
                        cont.resume(OcrResult(lines))
                    }
                    .addOnFailureListener { cont.resumeWithException(RecognitionException(it)) }
            }
        } finally {
            recognizer.close()
        }
    }
}
