package com.darshan.splitsmart.integration

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.core.DefaultDispatcherProvider
import com.darshan.splitsmart.data.llm.LlmException
import com.darshan.splitsmart.data.local.SplitSmartDatabase
import com.darshan.splitsmart.data.local.toEntity
import com.darshan.splitsmart.data.repository.BillRepositoryImpl
import com.darshan.splitsmart.data.repository.GroupRepositoryImpl
import com.darshan.splitsmart.data.repository.ReceiptDraftRepositoryImpl
import com.darshan.splitsmart.domain.model.BillSource
import com.darshan.splitsmart.domain.usecase.ExtractReceiptUseCase
import com.darshan.splitsmart.testutil.FakeLlmExtractor
import com.darshan.splitsmart.testutil.FakeReceiptImageStore
import com.darshan.splitsmart.testutil.SAMPLE_OCR_TEXT
import com.darshan.splitsmart.testutil.fixedClock
import com.darshan.splitsmart.testutil.runOffMain
import com.darshan.splitsmart.testutil.testDraft
import com.darshan.splitsmart.testutil.testGroup
import com.darshan.splitsmart.testutil.waitUntilWithMainLooper
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.review.ReviewRoute
import com.darshan.splitsmart.ui.review.ReviewViewModel
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import com.darshan.splitsmart.testutil.RecordingAnalyticsTracker
import com.darshan.splitsmart.testutil.RecordingCrashReporter
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Draft in Room → fake Gemini → review screen → edit → save → Bill with items in Room. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ReviewFlowIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: SplitSmartDatabase
    private val llm = FakeLlmExtractor()
    private val dispatchers = DefaultDispatcherProvider()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SplitSmartDatabase::class.java).build()
        runOffMain {
            db.groupDao().insert(testGroup().toEntity())
            db.receiptDraftDao().upsert(testDraft("d1", text = SAMPLE_OCR_TEXT).toEntity())
        }
    }

    @After
    fun tearDown() = db.close()

    private fun vm() = ReviewViewModel(
        SavedStateHandle(mapOf("draftId" to "d1")), ReceiptDraftRepositoryImpl(db.receiptDraftDao(), dispatchers),
        ExtractReceiptUseCase(llm, dispatchers, RecordingAnalyticsTracker(), RecordingCrashReporter()), BillRepositoryImpl(db.billDao(), dispatchers),
        GroupRepositoryImpl(db.groupDao(), db.memberDao(), fixedClock, dispatchers), FakeReceiptImageStore(), fixedClock, dispatchers,
    )

    @Test
    fun `llm items are shown, editable and saved as a bill`() {
        var savedId: String? = null
        compose.setContent { SplitSmartTheme { ReviewRoute(viewModel = vm(), onBack = {}, onSaved = { savedId = it }) } }
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.REVIEW_SAVE) }
        compose.onAllNodesWithTag(SplitSmartTestTags.REVIEW_ITEM).assertCountEquals(2)
        compose.onNodeWithText("Scanned (AI)").assertIsDisplayed()
        compose.onNodeWithTag(SplitSmartTestTags.REVIEW_TITLE).performTextInput("Team dinner")
        compose.onAllNodesWithTag(SplitSmartTestTags.REVIEW_ITEM_AMOUNT)[0].performScrollTo().performTextClearance()
        compose.onAllNodesWithTag(SplitSmartTestTags.REVIEW_ITEM_AMOUNT)[0].performTextInput("300")
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.MISMATCH_BANNER) }
        compose.onNodeWithTag(SplitSmartTestTags.USE_COMPUTED_TOTAL).performScrollTo().performClick()
        compose.onNodeWithTag(SplitSmartTestTags.REVIEW_SAVE).performClick()
        compose.waitUntilWithMainLooper { savedId != null }
        val bill = runOffMain { BillRepositoryImpl(db.billDao(), dispatchers).getBill(savedId!!) }!!
        assertThat(bill.title).isEqualTo("Team dinner")
        assertThat(bill.source).isEqualTo(BillSource.SCAN_LLM)
        assertThat(bill.items.map { it.totalPaise }).containsExactly(30_000L, 9_000L).inOrder()
        assertThat(bill.totalPaise).isEqualTo(30_000 + 9_000 + 1_700)
        assertThat(runOffMain { db.receiptDraftDao().getById("d1") }).isNull()
    }

    @Test
    fun `without an api key the on-device parser result is shown with a notice`() {
        llm.failWith = LlmException.KeyMissing()
        compose.setContent { SplitSmartTheme { ReviewRoute(viewModel = vm(), onBack = {}, onSaved = {}) } }
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.FALLBACK_BANNER) }
        compose.onNodeWithText("Scanned (on-device)").assertIsDisplayed()
        compose.onAllNodesWithTag(SplitSmartTestTags.REVIEW_ITEM).assertCountEquals(2)
        compose.onNodeWithText("Paneer Tikka", substring = true).assertExists()
        // key missing: no point offering a retry of the AI path
        assertThat(compose.onAllNodesWithTagExists(SplitSmartTestTags.REVIEW_RERUN)).isFalse()
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountEquals(n: Int) {
    assertThat(fetchSemanticsNodes().size).isEqualTo(n)
}
