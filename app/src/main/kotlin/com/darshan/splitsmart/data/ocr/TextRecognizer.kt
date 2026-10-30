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

/** One recognised line with its position so the receipt can be re-assembled top to bottom. */
data class OcrLine(val text: String, val top: Int, val left: Int)

data class OcrResult(val lines: List<OcrLine>) {
    /** Lines in reading order (top → bottom, then left → right). */
    val text: String get() = lines.sortedWith(compareBy({ it.top }, { it.left })).joinToString("\n") { it.text }
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
                            OcrLine(line.text, top = line.boundingBox?.top ?: i, left = line.boundingBox?.left ?: 0)
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
