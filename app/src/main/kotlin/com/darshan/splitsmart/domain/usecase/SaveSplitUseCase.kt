package com.darshan.splitsmart.domain.usecase

import com.darshan.splitsmart.domain.analytics.AnalyticsEvent
import com.darshan.splitsmart.domain.analytics.AnalyticsTracker
import com.darshan.splitsmart.domain.analytics.CrashReporter
import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.BillSplit
import com.darshan.splitsmart.domain.model.BillStatus
import com.darshan.splitsmart.domain.model.SplitConfig
import com.darshan.splitsmart.domain.model.SplitError
import com.darshan.splitsmart.domain.model.SplitException
import com.darshan.splitsmart.domain.repository.BillRepository
import com.darshan.splitsmart.domain.split.SplitCalculator
import javax.inject.Inject

sealed interface SaveSplitResult {
    data class Saved(val bill: Bill) : SaveSplitResult
    data class Invalid(val reason: SplitError) : SaveSplitResult
    data class Failed(val cause: Throwable) : SaveSplitResult
}

class SaveSplitUseCase @Inject constructor(
    private val repository: BillRepository,
    private val analytics: AnalyticsTracker,
    private val crashReporter: CrashReporter,
) {
    suspend operator fun invoke(bill: Bill, config: SplitConfig, payerId: String?): SaveSplitResult {
        val result = try {
            SplitCalculator.split(bill, config)
        } catch (e: SplitException) {
            return SaveSplitResult.Invalid(e.reason)
        }
        val updated = bill.copy(paidByMemberId = payerId, status = BillStatus.SPLIT, split = BillSplit(config, result.shares))
        return runCatching { repository.saveBill(updated) }.fold(
            {
                crashReporter.setKey("group_size", config.participantIds.size)
                crashReporter.setKey("item_count", bill.items.size)
                analytics.track(AnalyticsEvent.SplitCreated(config.mode, config.participantIds.size, bill.items.size))
                SaveSplitResult.Saved(it)
            },
            {
                crashReporter.recordNonFatal(it, mapOf("group_size" to config.participantIds.size, "item_count" to bill.items.size))
                SaveSplitResult.Failed(it)
            },
        )
    }
}
