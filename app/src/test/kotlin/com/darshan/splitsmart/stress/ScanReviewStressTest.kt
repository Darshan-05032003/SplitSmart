package com.darshan.splitsmart.stress

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.darshan.splitsmart.data.ocr.LiveText
import com.darshan.splitsmart.data.ocr.LiveTextBox
import com.darshan.splitsmart.domain.model.Bill
import com.darshan.splitsmart.domain.model.ExtractionWarning
import com.darshan.splitsmart.domain.usecase.ExtractReceiptUseCase
import com.darshan.splitsmart.domain.usecase.ScanReceiptUseCase
import com.darshan.splitsmart.testutil.FakeBillRepository
import com.darshan.splitsmart.testutil.FakeCameraPermission
import com.darshan.splitsmart.testutil.FakeGroupRepository
import com.darshan.splitsmart.testutil.FakeLlmExtractor
import com.darshan.splitsmart.testutil.FakeReceiptCamera
import com.darshan.splitsmart.testutil.FakeReceiptDraftRepository
import com.darshan.splitsmart.testutil.FakeReceiptImageStore
import com.darshan.splitsmart.testutil.FakeTextRecognizer
import com.darshan.splitsmart.testutil.MainDispatcherRule
import com.darshan.splitsmart.testutil.RecordingAnalyticsTracker
import com.darshan.splitsmart.testutil.RecordingCrashReporter
import com.darshan.splitsmart.testutil.TestDispatcherProvider
import com.darshan.splitsmart.testutil.fixedClock
import com.darshan.splitsmart.testutil.testDraft
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.designsystem.commitState
import com.darshan.splitsmart.ui.designsystem.components.SsSnackbarState
import com.darshan.splitsmart.ui.designsystem.effects.HapticEvent
import com.darshan.splitsmart.ui.designsystem.effects.UiEffect
import com.darshan.splitsmart.ui.designsystem.recompositionCycles
import com.darshan.splitsmart.ui.designsystem.setDs
import com.darshan.splitsmart.ui.review.ItemReveal
import com.darshan.splitsmart.ui.review.ItemRevealLogic
import com.darshan.splitsmart.ui.review.ItemRow
import com.darshan.splitsmart.ui.review.ReviewCallbacks
import com.darshan.splitsmart.ui.review.ReviewForm
import com.darshan.splitsmart.ui.review.ReviewPhase
import com.darshan.splitsmart.ui.review.ReviewScreen
import com.darshan.splitsmart.ui.review.ReviewTags
import com.darshan.splitsmart.ui.review.ReviewUiState
import com.darshan.splitsmart.ui.review.ReviewViewModel
import com.darshan.splitsmart.ui.scan.ScanCallbacks
import com.darshan.splitsmart.ui.scan.ScanEffect
import com.darshan.splitsmart.ui.scan.ScanPhase
import com.darshan.splitsmart.ui.scan.ScanScreen
import com.darshan.splitsmart.ui.scan.ScanUiState
import com.darshan.splitsmart.ui.scan.ScanViewModel
import com.google.common.truth.Truth.assertThat
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Load for the scan flow and the review. Prints `STRESS [...]` lines for docs/LOAD_TEST_REPORT.md. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ScanReviewStressTest {
    @get:Rule val rule = createComposeRule()
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val mainRule = MainDispatcherRule(dispatcher)
    private val dispatchers = TestDispatcherProvider(dispatcher)

    private fun scanVm(camera: FakeReceiptCamera, drafts: FakeReceiptDraftRepository, store: FakeReceiptImageStore, recognizer: FakeTextRecognizer = FakeTextRecognizer()) = ScanViewModel(
        SavedStateHandle(mapOf("groupId" to "g1")),
        ScanReceiptUseCase(recognizer, store, drafts, fixedClock, dispatchers, RecordingAnalyticsTracker(), RecordingCrashReporter()),
        store, camera, FakeCameraPermission(true), drafts, dispatchers,
    )

    @Test
    fun `1,000 rapid shutter taps take one picture, play one heavy haptic and open one review`() = runTest(dispatcher) {
        val camera = FakeReceiptCamera()
        val vm = scanVm(camera, FakeReceiptDraftRepository(), FakeReceiptImageStore())
        vm.uiState.test { awaitItem(); awaitItem(); cancelAndIgnoreRemainingEvents() }
        val effects = mutableListOf<UiEffect>()
        vm.effects.test {
            val ms = measureTimeMillis { repeat(1_000) { vm.capture() } }
            dispatcher.scheduler.advanceUntilIdle()
            effects += awaitItem()
            effects += awaitItem()
            expectNoEvents()
            println("STRESS [scan] 1,000 shutter taps in $ms ms -> ${camera.captures.get()} picture, effects $effects")
        }
        assertThat(camera.captures.get()).isEqualTo(1)
        assertThat(effects.count { it == UiEffect.Haptic(HapticEvent.Heavy) }).isEqualTo(1)
        assertThat(effects.count { it is ScanEffect.OpenReview }).isEqualTo(1)
    }

    @Test
    fun `1,000 scans in a row, each thrown away, leave no draft, no photo and no stray effect`() = runTest(dispatcher) {
        val camera = FakeReceiptCamera()
        val drafts = FakeReceiptDraftRepository()
        val root = kotlin.io.path.createTempDirectory("receipts").toFile()
        val store = FakeReceiptImageStore(root)
        val vm = scanVm(camera, drafts, store)
        var heavy = 0
        var opened = 0
        vm.uiState.test {
            awaitItem(); awaitItem()
            vm.effects.test {
                val ms = measureTimeMillis {
                    repeat(1_000) {
                        vm.capture()
                        dispatcher.scheduler.advanceUntilIdle()
                        vm.rescan()
                        dispatcher.scheduler.advanceUntilIdle()
                    }
                }
                while (true) {
                    val effect = runCatching { awaitItem() }.getOrNull() ?: break
                    if (effect == UiEffect.Haptic(HapticEvent.Heavy)) heavy++
                    if (effect is ScanEffect.OpenReview) opened++
                    if (heavy == 1_000 && opened == 1_000) break
                }
                println("STRESS [scan] 1,000 capture + rescan cycles in $ms ms: drafts left ${drafts.drafts.value.size}, photos left ${root.listFiles()?.size}")
                cancelAndIgnoreRemainingEvents()
            }
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(camera.captures.get()).isEqualTo(1_000)
        assertThat(drafts.drafts.value).isEmpty()
        assertThat(root.listFiles().orEmpty().toList()).isEmpty()
        assertThat(heavy).isEqualTo(1_000)
        assertThat(opened).isEqualTo(1_000)
        // The last rescan left the camera Ready, so live text is back on.
        assertThat(camera.liveTextRequests.last()).isTrue()
    }

    @Test
    fun `a 500-line receipt becomes a capped 200-row review that composes and scrolls in bounded time`() {
        val text = buildString {
            append("Hotel Marigold Bhavan\n")
            for (i in 1..500) append("Item number $i ").append(100 + i).append(".00\n")
            append("TOTAL 99999.00\n")
        }
        val useCase = ExtractReceiptUseCase(FakeLlmExtractor(), com.darshan.splitsmart.core.DefaultDispatcherProvider(), RecordingAnalyticsTracker(), RecordingCrashReporter())
        lateinit var result: com.darshan.splitsmart.domain.model.ExtractionResult
        val parseMs = measureTimeMillis { result = kotlinx.coroutines.runBlocking { useCase.provisional(text) } }
        assertThat(result.receipt.items.size).isAtMost(Bill.MAX_ITEMS)
        assertThat(result.warnings).contains(ExtractionWarning.ITEMS_CAPPED)
        val rows = result.receipt.items.mapIndexed { i, item -> ItemRow("k$i", item.name, item.qty.toString(), com.darshan.splitsmart.core.Money.toPlainRupees(item.totalPaise)) }
        val state = ReviewUiState(
            phase = ReviewPhase.EDITING,
            form = ReviewForm(items = rows, total = "99999.00", warnings = result.warnings),
            subtotalPaise = rows.sumOf { com.darshan.splitsmart.core.Money.parseInrToPaise(it.amount) ?: 0 },
            mismatchPaise = 1,
            reveal = ItemReveal(1, rows.map { it.key }),
        )
        rule.mainClock.autoAdvance = false
        val composeMs = measureTimeMillis {
            rule.setDs { Box(Modifier.fillMaxSize()) { ReviewScreen(state, SsSnackbarState(), noopReview) } }
            rule.mainClock.advanceTimeBy(16)
        }
        // The whole reveal, frame by frame: capped, and drawn rather than recomposed.
        val before = recompositionCycles()
        var t = 0L
        while (t < ItemRevealLogic.durationMs(rows.size) + 32) {
            rule.mainClock.advanceTimeBy(16)
            t += 16
        }
        val revealCycles = recompositionCycles() - before
        val list = rule.onNodeWithTag(ReviewTags.LIST)
        val scrollMs = measureTimeMillis {
            for (jump in 0 until 300) {
                list.performScrollToIndex((jump * 37) % (rows.size + 5))
                rule.mainClock.advanceTimeBy(16)
            }
        }
        println(
            "STRESS [review] 500-line receipt: parsed in $parseMs ms -> ${rows.size} rows (capped), first frame $composeMs ms, " +
                "reveal ${ItemRevealLogic.durationMs(rows.size)} ms with $revealCycles recomposer cycles, 300 jumps in $scrollMs ms",
        )
        assertThat(rows.size).isEqualTo(Bill.MAX_ITEMS)
        assertThat(ItemRevealLogic.durationMs(rows.size)).isAtMost(1_200L)
        assertThat(revealCycles).isLessThan(8L)
    }

    @Test
    fun `10,000 live text frames at the analyzer's pace barely recompose the camera`() {
        val live = mutableStateOf(LiveText.NONE)
        rule.mainClock.autoAdvance = false
        rule.setDs {
            ScanScreen(ScanUiState(ScanPhase.Ready, "g1"), preview = { Box(Modifier.fillMaxSize()) }, callbacks = ScanCallbacks({}, {}, {}, {}, {}, {}, {}, {}), liveText = live)
        }
        rule.mainClock.advanceTimeBy(100)
        val before = recompositionCycles()
        val ms = measureTimeMillis {
            for (frame in 0 until 10_000) {
                val count = 20 + frame % 60 // always "text found", never past the cap
                commitState { live.value = LiveText.of(List(count) { LiveTextBox(150f, 500f + it * 12f, 600f, 510f + it * 12f) }, frame.toLong()) }
                rule.mainClock.advanceTimeBy(16)
            }
        }
        val cycles = recompositionCycles() - before
        println("STRESS [scan] 10,000 live text frames (20-79 outlines each) in $ms ms, $cycles recomposer cycles")
        assertThat(cycles).isLessThan(20L)
    }

    @Test
    fun `1,000 save taps on a review write one bill`() = runTest(dispatcher) {
        val drafts = FakeReceiptDraftRepository().also { it.save(testDraft("d1", text = com.darshan.splitsmart.testutil.SAMPLE_OCR_TEXT)) }
        val bills = FakeBillRepository()
        val vm = ReviewViewModel(
            SavedStateHandle(mapOf("draftId" to "d1")), drafts,
            ExtractReceiptUseCase(FakeLlmExtractor(), dispatchers, RecordingAnalyticsTracker(), RecordingCrashReporter()),
            bills, FakeGroupRepository(), FakeReceiptImageStore(), fixedClock, dispatchers,
        )
        vm.uiState.test {
            while (awaitItem().phase != ReviewPhase.EDITING) { /* reading */ }
            repeat(1_000) { vm.save("Scanned bill") }
            dispatcher.scheduler.advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
        println("STRESS [review] 1,000 save taps -> ${bills.bills.value.size} bill")
        assertThat(bills.bills.value).hasSize(1)
    }

    @Test
    fun `100 injected taps on the real shutter take one picture`() {
        val camera = FakeReceiptCamera()
        val vm = scanVm(camera, FakeReceiptDraftRepository(), FakeReceiptImageStore())
        rule.mainClock.autoAdvance = false
        rule.setDs(reduceMotion = true) {
            com.darshan.splitsmart.ui.scan.ScanRoute(vm, onBack = {}, onContinue = {}, preview = { Box(Modifier.fillMaxSize()) }, requestPermission = {}, pickImage = {})
        }
        // The view model runs on the test dispatcher: step it and the frame clock until the camera is ready.
        var frames = 0
        while (rule.onAllNodes(androidx.compose.ui.test.hasTestTag(SplitSmartTestTags.CAPTURE_BUTTON)).fetchSemanticsNodes().isEmpty() && frames < 100) {
            dispatcher.scheduler.advanceUntilIdle()
            rule.mainClock.advanceTimeBy(16)
            frames++
        }
        // All taps land while the first picture is still being taken (the view model's work waits on the test dispatcher).
        repeat(100) {
            rule.onNodeWithTag(SplitSmartTestTags.CAPTURE_BUTTON).performClick()
            rule.mainClock.advanceTimeBy(16)
        }
        dispatcher.scheduler.advanceUntilIdle()
        println("STRESS [scan] 100 injected shutter taps -> ${camera.captures.get()} picture")
        assertThat(camera.captures.get()).isEqualTo(1)
    }

    private val noopReview = ReviewCallbacks({}, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {}, {})
}
