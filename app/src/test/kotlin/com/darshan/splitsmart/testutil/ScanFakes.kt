package com.darshan.splitsmart.testutil

import com.darshan.splitsmart.data.ocr.CameraCaptureException
import com.darshan.splitsmart.data.ocr.CameraPermission
import com.darshan.splitsmart.data.ocr.ImageImportException
import com.darshan.splitsmart.data.ocr.OcrLine
import com.darshan.splitsmart.data.ocr.OcrResult
import com.darshan.splitsmart.data.ocr.ReceiptCamera
import com.darshan.splitsmart.data.ocr.ReceiptImageStore
import com.darshan.splitsmart.data.ocr.RecognitionException
import com.darshan.splitsmart.data.ocr.TextRecognizer
import com.darshan.splitsmart.domain.model.ReceiptDraft
import com.darshan.splitsmart.domain.repository.ReceiptDraftRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class FakeTextRecognizer(var lines: List<String> = listOf("Paneer Tikka 250.00", "Total 250.00")) : TextRecognizer {
    var failWith: Throwable? = null
    val calls = AtomicInteger()
    var lastFile: File? = null
    override suspend fun recognize(image: File): OcrResult {
        calls.incrementAndGet()
        lastFile = image
        failWith?.let { throw if (it is RecognitionException) it else RecognitionException(it) }
        return OcrResult(lines.mapIndexed { i, t -> OcrLine(t, top = i * 10, left = 0) })
    }
}

class FakeReceiptCamera : ReceiptCamera {
    var failWith: Throwable? = null
    val captures = AtomicInteger()
    override suspend fun capture(target: File) {
        captures.incrementAndGet()
        failWith?.let { throw if (it is CameraCaptureException) it else CameraCaptureException(it) }
        target.writeBytes(byteArrayOf(1, 2, 3))
    }
}

class FakeCameraPermission(var granted: Boolean = true) : CameraPermission {
    override fun hasCameraPermission(): Boolean = granted
}

/** Writes real files into a temp dir so file-based assertions work without Android's FileProvider. */
class FakeReceiptImageStore(private val root: File = kotlin.io.path.createTempDirectory("receipts").toFile()) : ReceiptImageStore {
    var importFailWith: Throwable? = null
    val imported = mutableListOf<String>()
    override fun newCaptureFile(): File = File(root, "${UUID.randomUUID()}.jpg")
    override fun uriFor(file: File): String = "content://test/receipts/${file.name}"
    override suspend fun import(sourceUri: String): File {
        importFailWith?.let { throw if (it is ImageImportException) it else ImageImportException(it) }
        imported += sourceUri
        return newCaptureFile().also { it.writeBytes(byteArrayOf(9)) }
    }
    override fun resolveOwnedFile(name: String): File? = File(root, name).takeIf { it.parentFile == root }
    override suspend fun delete(file: File) { file.delete() }
    override suspend fun purgeOlderThan(cutoff: Instant): Int = 0
}

class FakeReceiptDraftRepository : ReceiptDraftRepository {
    val drafts = MutableStateFlow<List<ReceiptDraft>>(emptyList())
    var failWith: Throwable? = null
    override suspend fun save(draft: ReceiptDraft) { failWith?.let { throw it }; drafts.update { list -> list.filterNot { it.id == draft.id } + draft } }
    override suspend fun get(id: String): ReceiptDraft? = drafts.value.find { it.id == id }
    override fun observe(id: String): Flow<ReceiptDraft?> = drafts.map { list -> list.find { it.id == id } }
    override suspend fun delete(id: String) { drafts.update { list -> list.filterNot { it.id == id } } }
    override suspend fun deleteOlderThan(cutoff: Instant): Int {
        val before = drafts.value.size
        drafts.update { list -> list.filterNot { it.createdAt.isBefore(cutoff) } }
        return before - drafts.value.size
    }
}

fun testDraft(id: String = "d1", groupId: String = "g1", text: String = "Paneer Tikka 250.00\nTotal 250.00", createdAt: Instant = FIXED_INSTANT) =
    ReceiptDraft(id = id, groupId = groupId, imagePath = null, ocrText = text, createdAt = createdAt)
