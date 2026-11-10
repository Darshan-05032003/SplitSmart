package com.darshan.splitsmart.benchmark

import com.darshan.splitsmart.data.settings.InMemoryOnboardingRepository
import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.repository.OnboardingRepository
import com.darshan.splitsmart.testutil.FakeBillRepository
import com.darshan.splitsmart.testutil.FakeGroupRepository
import com.darshan.splitsmart.testutil.FakeSettlementRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * The macrobenchmarks broadcast to the benchmark build's seed receiver and then wait for the groups list. A fresh install opens
 * on the intro, so the seed must mark it done or every benchmark times out. Compiled from `src/benchmark` (see the app build file).
 */
class SeedDataReceiverTest {
    @Test
    fun `seeding marks the intro done and fills the lists the benchmarks scroll`() = runBlocking {
        val onboarding = InMemoryOnboardingRepository(completed = false)
        val groups = FakeGroupRepository()
        val bills = FakeBillRepository()
        val settlements = FakeSettlementRepository()
        BenchmarkSeed.run(groups, bills, settlements, onboarding)
        assertThat(onboarding.completed.first()).isTrue()
        val summaries = groups.observeGroupSummaries().first()
        assertThat(summaries).hasSize(BenchmarkSeed.GROUPS)
        val main = summaries.single { it.group.name == BenchmarkSeed.MAIN_GROUP }
        assertThat(bills.observeBills(main.group.id).first()).hasSize(BenchmarkSeed.MAIN_BILLS)
        // Through the repository, between members of the main group, so the history screen has payments to scroll.
        val paid = settlements.observeSettlements(main.group.id).first()
        assertThat(paid).hasSize(BenchmarkSeed.MAIN_SETTLEMENTS)
        assertThat(paid.all { it.fromMemberId != it.toMemberId && it.counts }).isTrue()
    }

    @Test
    fun `the intro is marked done before any data is written, and again on a repeat run`() = runBlocking {
        val order = mutableListOf<String>()
        val onboarding = object : OnboardingRepository {
            private val state = MutableStateFlow(false)
            override val completed: Flow<Boolean> = state
            override suspend fun setCompleted(completed: Boolean) {
                order += "onboarding"
                state.value = completed
            }
        }
        val bills = object : FakeBillRepository() {
            override suspend fun saveBill(bill: Bill): Bill {
                if (order.lastOrNull() != "bill") order += "bill"
                return super.saveBill(bill)
            }
        }
        val groups = FakeGroupRepository()
        val settlements = FakeSettlementRepository()
        BenchmarkSeed.run(groups, bills, settlements, onboarding)
        assertThat(order.first()).isEqualTo("onboarding")
        // A second broadcast still marks the intro first, e.g. after someone replayed it between runs.
        onboarding.setCompleted(false)
        order.clear()
        BenchmarkSeed.run(groups, bills, settlements, onboarding)
        assertThat(order.first()).isEqualTo("onboarding")
        assertThat(onboarding.completed.first()).isTrue()
    }
}
