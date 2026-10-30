package com.darshan.splitsmart.domain.repository

import com.darshan.splitsmart.domain.model.ReceiptDraft
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface ReceiptDraftRepository {
    suspend fun save(draft: ReceiptDraft)
    suspend fun get(id: String): ReceiptDraft?
    fun observe(id: String): Flow<ReceiptDraft?>
    suspend fun delete(id: String)
    /** Returns the number of drafts removed. */
    suspend fun deleteOlderThan(cutoff: Instant): Int
}
