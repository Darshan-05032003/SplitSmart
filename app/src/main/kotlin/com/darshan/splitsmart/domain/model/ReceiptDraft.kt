package com.darshan.splitsmart.domain.model

import java.time.Instant

/** The OCR text of one scanned receipt, saved before extraction so a crash or process death never loses a scan. */
data class ReceiptDraft(
    val id: String,
    val groupId: String,
    /** Absolute path of the captured / imported image inside the app's `receipts/` cache dir, if still available. */
    val imagePath: String?,
    val ocrText: String,
    val createdAt: Instant,
)

enum class ScanError { NO_TEXT, RECOGNITION_FAILED, IMAGE_UNREADABLE, CAPTURE_FAILED, STORAGE_FAILED }
