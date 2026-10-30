package com.darshan.splitsmart.integration

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.core.DefaultDispatcherProvider
import com.darshan.splitsmart.data.local.SplitSmartDatabase
import com.darshan.splitsmart.data.local.toEntity
import com.darshan.splitsmart.data.repository.ReceiptDraftRepositoryImpl
import com.darshan.splitsmart.domain.usecase.ScanReceiptUseCase
import com.darshan.splitsmart.testutil.FakeCameraPermission
import com.darshan.splitsmart.testutil.FakeReceiptCamera
import com.darshan.splitsmart.testutil.FakeReceiptImageStore
import com.darshan.splitsmart.testutil.FakeTextRecognizer
import com.darshan.splitsmart.testutil.fixedClock
import com.darshan.splitsmart.testutil.runOffMain
import com.darshan.splitsmart.testutil.testGroup
import com.darshan.splitsmart.testutil.waitUntilWithMainLooper
import com.darshan.splitsmart.ui.SplitSmartTestTags
import com.darshan.splitsmart.ui.scan.ScanRoute
import com.darshan.splitsmart.ui.scan.ScanViewModel
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import com.darshan.splitsmart.testutil.RecordingAnalyticsTracker
import com.darshan.splitsmart.testutil.RecordingCrashReporter
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Fake camera + fake recognizer + real Room draft repository + real ViewModel + Compose scan screen. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ScanFlowIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: SplitSmartDatabase
    private val recognizer = FakeTextRecognizer(listOf("Masala Dosa 120.00", "Filter Coffee 40.00", "Total 160.00"))
    private val camera = FakeReceiptCamera()
    private val permission = FakeCameraPermission(granted = true)
    private val store = FakeReceiptImageStore()
    private val dispatchers = DefaultDispatcherProvider()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SplitSmartDatabase::class.java).build()
        runOffMain { db.groupDao().insert(testGroup().toEntity()) }
    }

    @After
    fun tearDown() = db.close()

    private fun vm(): ScanViewModel {
        val drafts = ReceiptDraftRepositoryImpl(db.receiptDraftDao(), dispatchers)
        return ScanViewModel(
            SavedStateHandle(mapOf("groupId" to "g1")), ScanReceiptUseCase(recognizer, store, drafts, fixedClock, dispatchers, RecordingAnalyticsTracker(), RecordingCrashReporter()),
            store, camera, permission, drafts, dispatchers,
        )
    }

    @Test
    fun `capture shows the recognised text and continue hands over the draft id`() {
        var continued: String? = null
        compose.setContent {
            SplitSmartTheme { ScanRoute(viewModel = vm(), onBack = {}, onContinue = { continued = it }, preview = { Box {} }, requestPermission = {}, pickImage = {}) }
        }
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.CAPTURE_BUTTON) }
        compose.onNodeWithTag(SplitSmartTestTags.CAPTURE_BUTTON).performClick()
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.OCR_TEXT) }
        compose.onNodeWithText("Masala Dosa 120.00", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(SplitSmartTestTags.CONTINUE_BUTTON).performClick()
        assertThat(continued).isNotNull()
        val stored = runOffMain { db.receiptDraftDao().getById(continued!!) }!!
        assertThat(stored.ocrText).contains("Filter Coffee 40.00")
        assertThat(camera.captures.get()).isEqualTo(1)
    }

    @Test
    fun `permission denied state offers the gallery path`() {
        permission.granted = false
        var asked = 0
        var picked = 0
        compose.setContent {
            SplitSmartTheme { ScanRoute(viewModel = vm(), onBack = {}, onContinue = {}, preview = { Box {} }, requestPermission = { asked++ }, pickImage = { picked++ }) }
        }
        compose.waitUntilWithMainLooper { compose.onAllNodesWithTagExists(SplitSmartTestTags.GRANT_PERMISSION_BUTTON) }
        compose.onNodeWithTag(SplitSmartTestTags.GRANT_PERMISSION_BUTTON).performClick()
        assertThat(asked).isEqualTo(1)
        compose.onNodeWithTag(SplitSmartTestTags.GALLERY_BUTTON).performClick()
        assertThat(picked).isEqualTo(1)
    }
}
