package com.darshan.splitsmart.ui.review

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.darshan.splitsmart.data.llm.LlmException
import com.darshan.splitsmart.domain.model.BillSource
import com.darshan.splitsmart.domain.model.ExtractionSource
import com.darshan.splitsmart.domain.model.LlmFailure
import com.darshan.splitsmart.domain.model.NameError
import com.darshan.splitsmart.domain.usecase.ExtractReceiptUseCase
import com.darshan.splitsmart.testutil.FakeBillRepository
import com.darshan.splitsmart.testutil.FakeGroupRepository
import com.darshan.splitsmart.testutil.FakeLlmExtractor
import com.darshan.splitsmart.testutil.FakeReceiptDraftRepository
import com.darshan.splitsmart.testutil.FakeReceiptImageStore
import com.darshan.splitsmart.testutil.MainDispatcherRule
import com.darshan.splitsmart.testutil.SAMPLE_OCR_TEXT
import com.darshan.splitsmart.testutil.TestDispatcherProvider
import com.darshan.splitsmart.testutil.fixedClock
import com.darshan.splitsmart.testutil.testDraft
import com.darshan.splitsmart.testutil.testMember
import com.darshan.splitsmart.ui.ErrorReason
import com.darshan.splitsmart.testutil.RecordingAnalyticsTracker
import com.darshan.splitsmart.testutil.RecordingCrashReporter
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val mainRule = MainDispatcherRule(dispatcher)
    private val dispatchers = TestDispatcherProvider(dispatcher)
    private val llm = FakeLlmExtractor()
    private val drafts = FakeReceiptDraftRepository()
    private val bills = FakeBillRepository()
    private val groups = FakeGroupRepository()
    private val images = FakeReceiptImageStore()

    @Before
    fun seed() = runTest {
        drafts.save(testDraft("d1", text = SAMPLE_OCR_TEXT))
        groups.members.value = listOf(testMember("m1", name = "Asha"), testMember("m2", name = "Ravi"))
    }

    private fun vm(handle: SavedStateHandle = SavedStateHandle(mapOf("draftId" to "d1"))) = ReviewViewModel(
        savedStateHandle = handle, drafts = drafts, extractReceipt = ExtractReceiptUseCase(llm, dispatchers, RecordingAnalyticsTracker(), RecordingCrashReporter()), billRepository = bills,
        groupRepository = groups, imageStore = images, clock = fixedClock, dispatchers = dispatchers,
    )

    private suspend fun ReviewViewModel.awaitEditing(): ReviewUiState {
        uiState.test {
            while (awaitItem().phase != ReviewPhase.EDITING) { /* Loading → Extracting → Editing */ }
            cancelAndIgnoreRemainingEvents()
        }
        dispatcher.scheduler.advanceUntilIdle()
        return uiState.value
    }

    @Test
    fun `loads the draft, extracts with the LLM and shows an editable form`() = runTest(dispatcher) {
        val vm = vm()
        vm.uiState.test {
            assertThat(awaitItem().phase).isEqualTo(ReviewPhase.LOADING)
            assertThat(awaitItem().phase).isEqualTo(ReviewPhase.EXTRACTING)
            val editing = awaitItem()
            assertThat(editing.phase).isEqualTo(ReviewPhase.EDITING)
            val form = editing.form!!
            assertThat(form.items.map { it.name }).containsExactly("Paneer Tikka", "Butter Naan").inOrder()
            assertThat(form.items[1].qty).isEqualTo("2")
            assertThat(form.items[1].amount).isEqualTo("90.00")
            assertThat(form.tax).isEqualTo("17.00")
            assertThat(form.total).isEqualTo("357.00")
            assertThat(form.source).isEqualTo(ExtractionSource.LLM)
            assertThat(editing.subtotalPaise).isEqualTo(34_000)
            assertThat(editing.mismatchPaise).isEqualTo(0)
            assertThat(editing.members.map { it.name }).containsExactly("Asha", "Ravi")
        }
    }

    @Test
    fun `fallback source and failure reason are exposed`() = runTest(dispatcher) {
        llm.failWith = LlmException.KeyMissing()
        val state = vm().awaitEditing()
        assertThat(state.form!!.source).isEqualTo(ExtractionSource.REGEX)
        assertThat(state.form!!.llmFailure).isEqualTo(LlmFailure.KEY_MISSING)
        assertThat(state.form!!.items).hasSize(2)
    }

    @Test
    fun `editing items updates the subtotal and mismatch live`() = runTest(dispatcher) {
        val vm = vm()
        val state = vm.awaitEditing()
        val key = state.form!!.items[0].key
        vm.onItemAmountChange(key, "300")
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.uiState.value.subtotalPaise).isEqualTo(39_000)
        assertThat(vm.uiState.value.mismatchPaise).isEqualTo(39_000 + 1_700 - 35_700)
        vm.useComputedTotal()
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.uiState.value.form!!.total).isEqualTo("407.00")
        assertThat(vm.uiState.value.mismatchPaise).isEqualTo(0)
        vm.addItem()
        vm.removeItem(key)
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.uiState.value.form!!.items).hasSize(2)
        assertThat(vm.uiState.value.form!!.items.last().name).isEmpty()
    }

    @Test
    fun `save validates every field and reports errors per row`() = runTest(dispatcher) {
        val vm = vm()
        val state = vm.awaitEditing()
        val key = state.form!!.items[0].key
        vm.onItemNameChange(key, " ")
        vm.onItemQtyChange(key, "0")
        vm.onItemAmountChange(state.form!!.items[1].key, "-1")
        vm.onTaxChange("abc")
        vm.onTotalChange("")
        vm.save(defaultTitle = "Scanned bill")
        dispatcher.scheduler.advanceUntilIdle()
        val errors = vm.uiState.value.errors
        assertThat(errors.items[key]).containsExactly(ItemError.NAME, ItemError.QTY)
        assertThat(errors.items[state.form!!.items[1].key]).containsExactly(ItemError.AMOUNT)
        assertThat(errors.tax).isTrue()
        assertThat(errors.total).isTrue()
        assertThat(bills.bills.value).isEmpty()
        assertThat(vm.uiState.value.phase).isEqualTo(ReviewPhase.EDITING)
    }

    @Test
    fun `save builds a paise-exact bill, deletes the draft and emits Saved`() = runTest(dispatcher) {
        val vm = vm()
        vm.awaitEditing()
        vm.onTitleChange("Team dinner")
        vm.onPayerChange("m2")
        vm.events.test {
            vm.save(defaultTitle = "Scanned bill")
            val saved = awaitItem() as ReviewEvent.Saved
            val bill = bills.bills.value.single()
            assertThat(bill.id).isEqualTo(saved.billId)
            assertThat(bill.title).isEqualTo("Team dinner")
            assertThat(bill.groupId).isEqualTo("g1")
            assertThat(bill.paidByMemberId).isEqualTo("m2")
            assertThat(bill.source).isEqualTo(BillSource.SCAN_LLM)
            assertThat(bill.totalPaise).isEqualTo(35_700)
            assertThat(bill.taxPaise).isEqualTo(1_700)
            assertThat(bill.items.map { it.totalPaise }).containsExactly(25_000L, 9_000L).inOrder()
            assertThat(bill.items[1].unitPaise).isEqualTo(4_500)
            assertThat(bill.items[1].billId).isEqualTo(bill.id)
            assertThat(bill.items.map { it.sortOrder }).containsExactly(0, 1).inOrder()
            assertThat(bill.createdAt).isEqualTo(fixedClock.instant())
        }
        assertThat(drafts.get("d1")).isNull()
    }

    @Test
    fun `blank title falls back to the default and regex source is recorded`() = runTest(dispatcher) {
        llm.failWith = LlmException.Network(RuntimeException())
        val vm = vm()
        vm.awaitEditing()
        vm.events.test {
            vm.save(defaultTitle = "Scanned bill")
            awaitItem()
        }
        assertThat(bills.bills.value.single().title).isEqualTo("Scanned bill")
        assertThat(bills.bills.value.single().source).isEqualTo(BillSource.SCAN_REGEX)
    }

    @Test
    fun `double save creates one bill`() = runTest(dispatcher) {
        val vm = vm()
        vm.awaitEditing()
        vm.save("x"); vm.save("x"); vm.save("x")
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(bills.bills.value).hasSize(1)
    }

    @Test
    fun `process death restores the edited form without calling the LLM again`() = runTest(dispatcher) {
        val handle = SavedStateHandle(mapOf("draftId" to "d1"))
        val vm = vm(handle)
        val state = vm.awaitEditing()
        vm.onItemNameChange(state.form!!.items[0].key, "Paneer Tikka (edited)")
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(llm.calls.get()).isEqualTo(1)
        val restored = vm(handle)
        val restoredState = restored.awaitEditing()
        assertThat(restoredState.form!!.items[0].name).isEqualTo("Paneer Tikka (edited)")
        assertThat(llm.calls.get()).isEqualTo(1)
    }

    @Test
    fun `rerun extraction replaces the form`() = runTest(dispatcher) {
        llm.failWith = LlmException.RateLimited(null)
        val vm = vm()
        assertThat(vm.awaitEditing().form!!.source).isEqualTo(ExtractionSource.REGEX)
        llm.failWith = null
        vm.rerunExtraction()
        val state = vm.awaitEditing()
        assertThat(state.form!!.source).isEqualTo(ExtractionSource.LLM)
        assertThat(llm.calls.get()).isEqualTo(2)
    }

    @Test
    fun `missing draft is a not found error`() = runTest(dispatcher) {
        val vm = vm(SavedStateHandle(mapOf("draftId" to "nope")))
        vm.uiState.test {
            awaitItem()
            val s = awaitItem()
            assertThat(s.phase).isEqualTo(ReviewPhase.ERROR)
            assertThat(s.error).isEqualTo(ErrorReason.NOT_FOUND)
        }
    }

    @Test
    fun `too many items are refused`() = runTest(dispatcher) {
        val vm = vm()
        vm.awaitEditing()
        repeat(250) { vm.addItem() }
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.uiState.value.form!!.items.size).isAtMost(200)
    }

    @Test
    fun `title errors are reported`() = runTest(dispatcher) {
        val vm = vm()
        vm.awaitEditing()
        vm.onTitleChange("x".repeat(100))
        vm.save("d")
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.uiState.value.errors.title).isEqualTo(NameError.TOO_LONG)
    }
}
