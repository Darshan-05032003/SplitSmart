package com.darshan.splitsmart.domain.usecase

import com.darshan.splitsmart.domain.model.BillStatus
import com.darshan.splitsmart.domain.model.SplitConfig
import com.darshan.splitsmart.domain.model.SplitError
import com.darshan.splitsmart.domain.model.SplitMode
import com.darshan.splitsmart.testutil.FakeBillRepository
import com.darshan.splitsmart.testutil.testBill
import com.darshan.splitsmart.testutil.RecordingAnalyticsTracker
import com.darshan.splitsmart.testutil.RecordingCrashReporter
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SaveSplitUseCaseTest {
    private val repo = FakeBillRepository()
    private val useCase = SaveSplitUseCase(repo, RecordingAnalyticsTracker(), RecordingCrashReporter())

    @Test
    fun `computes, marks the bill SPLIT, records the payer and saves`() = runTest {
        val bill = testBill(totalPaise = 300, items = emptyList())
        val result = useCase(bill, SplitConfig(SplitMode.EQUAL, listOf("a", "b", "c")), payerId = "a") as SaveSplitResult.Saved
        assertThat(result.bill.status).isEqualTo(BillStatus.SPLIT)
        assertThat(result.bill.paidByMemberId).isEqualTo("a")
        assertThat(result.bill.split!!.shares.map { it.totalPaise }).containsExactly(100L, 100L, 100L)
        assertThat(repo.bills.value.single()).isEqualTo(result.bill)
    }

    @Test
    fun `invalid config is reported without saving`() = runTest {
        val bill = testBill(totalPaise = 300, items = emptyList())
        assertThat(useCase(bill, SplitConfig(SplitMode.BY_SHARE, listOf("a"), shares = mapOf("a" to 0)), null)).isEqualTo(SaveSplitResult.Invalid(SplitError.NO_SHARES))
        assertThat(repo.bills.value).isEmpty()
    }

    @Test
    fun `repository failure is reported`() = runTest {
        repo.failWith = IllegalStateException("disk")
        assertThat(useCase(testBill(items = emptyList()), SplitConfig(SplitMode.EQUAL, listOf("a")), null)).isInstanceOf(SaveSplitResult.Failed::class.java)
    }
}
