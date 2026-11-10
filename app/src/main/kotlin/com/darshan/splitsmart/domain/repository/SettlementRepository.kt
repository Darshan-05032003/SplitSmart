package com.darshan.splitsmart.domain.repository

import com.darshan.splitsmart.domain.model.Member
import com.darshan.splitsmart.domain.model.Settlement
import com.darshan.splitsmart.domain.model.SettlementMethod
import kotlinx.coroutines.flow.Flow

/** Payments people marked as made. Only an explicit tap records one; nothing a UPI app returns ever does. */
interface SettlementRepository {
    /** Newest first. */
    fun observeSettlements(groupId: String): Flow<List<Settlement>>

    /** Every group's payments (home screen balances). */
    fun observeAllSettlements(): Flow<List<Settlement>>

    /** [from] paid [to] [amountPaise] now. Both must be members of the same group. */
    suspend fun record(from: Member, to: Member, amountPaise: Long, method: SettlementMethod): Settlement

    /** Undo, or removing a payment marked by mistake. */
    suspend fun delete(settlementId: String)
}
