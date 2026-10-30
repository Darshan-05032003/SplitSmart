package com.darshan.splitsmart.domain.usecase

import com.darshan.splitsmart.core.Money
import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.BillSource
import com.darshan.splitsmart.domain.model.BillStatus
import com.darshan.splitsmart.domain.model.NameError
import com.darshan.splitsmart.domain.model.NameValidation
import com.darshan.splitsmart.domain.model.NameValidator
import com.darshan.splitsmart.domain.repository.BillRepository
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

sealed interface CreateBillResult {
    data class Created(val bill: Bill) : CreateBillResult
    data class InvalidTitle(val error: NameError) : CreateBillResult
    data object InvalidAmount : CreateBillResult
    data class Failed(val cause: Throwable) : CreateBillResult
}

/** A bill typed in by hand: title + total, no line items (equal split only). */
class CreateManualBillUseCase @Inject constructor(
    private val repository: BillRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(groupId: String, rawTitle: String, rawAmount: String, paidByMemberId: String?): CreateBillResult {
        val title = when (val v = NameValidator.validate(rawTitle, Bill.MAX_TITLE_LENGTH)) {
            is NameValidation.Invalid -> return CreateBillResult.InvalidTitle(v.error)
            is NameValidation.Valid -> v.name
        }
        val paise = Money.parseInrToPaise(rawAmount)?.takeIf { it > 0 } ?: return CreateBillResult.InvalidAmount
        val bill = Bill(
            id = UUID.randomUUID().toString(), groupId = groupId, title = title, totalPaise = paise, taxPaise = 0, tipPaise = 0,
            paidByMemberId = paidByMemberId, source = BillSource.MANUAL, status = BillStatus.DRAFT, createdAt = clock.instant(), items = emptyList(),
        )
        return runCatching { repository.saveBill(bill) }.fold({ CreateBillResult.Created(it) }, { CreateBillResult.Failed(it) })
    }
}
