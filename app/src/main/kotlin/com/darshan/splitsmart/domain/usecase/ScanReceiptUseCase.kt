package com.darshan.splitsmart.domain.usecase

import com.darshan.splitsmart.core.DispatcherProvider
import com.darshan.splitsmart.data.ocr.ImageImportException
import com.darshan.splitsmart.data.ocr.ReceiptImageStore
import com.darshan.splitsmart.data.ocr.TextRecognizer
import com.darshan.splitsmart.domain.model.ReceiptDraft
import com.darshan.splitsmart.domain.model.ScanError
import com.darshan.splitsmart.domain.parser.OcrTextNormalizer
import com.darshan.splitsmart.domain.repository.ReceiptDraftRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

sealed interface ScanOutcome {
    data class Success(val draft: ReceiptDraft) : ScanOutcome
    data class Failure(val error: ScanError) : ScanOutcome
}

/** Image file → OCR → normalised text → saved [ReceiptDraft]. Everything runs off the main thread. */
class ScanReceiptUseCase @Inject constructor(
    private val recognizer: TextRecognizer,
    private val imageStore: ReceiptImageStore,
    private val drafts: ReceiptDraftRepository,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) {
    suspend fun recognize(groupId: String, image: File): ScanOutcome = withContext(dispatchers.default) {
        if (!image.isFile || image.length() == 0L) return@withContext ScanOutcome.Failure(ScanError.IMAGE_UNREADABLE)
        val result = try {
            recognizer.recognize(image)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext ScanOutcome.Failure(ScanError.RECOGNITION_FAILED)
        }
        val text = OcrTextNormalizer.normalize(result.text)
        if (text.isBlank()) return@withContext ScanOutcome.Failure(ScanError.NO_TEXT)
        val draft = ReceiptDraft(UUID.randomUUID().toString(), groupId, image.absolutePath, text, clock.instant())
        try {
            drafts.save(draft)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext ScanOutcome.Failure(ScanError.STORAGE_FAILED)
        }
        ScanOutcome.Success(draft)
    }

    suspend fun importAndRecognize(groupId: String, sourceUri: String): ScanOutcome {
        val file = try {
            imageStore.import(sourceUri)
        } catch (e: ImageImportException) {
            return ScanOutcome.Failure(ScanError.IMAGE_UNREADABLE)
        }
        return recognize(groupId, file)
    }
}
