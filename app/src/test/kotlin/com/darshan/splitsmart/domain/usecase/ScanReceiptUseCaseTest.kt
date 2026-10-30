package com.darshan.splitsmart.domain.usecase

import com.darshan.splitsmart.data.ocr.ImageImportException
import com.darshan.splitsmart.domain.model.ScanError
import com.darshan.splitsmart.testutil.FakeReceiptDraftRepository
import com.darshan.splitsmart.testutil.FakeReceiptImageStore
import com.darshan.splitsmart.testutil.FakeTextRecognizer
import com.darshan.splitsmart.testutil.TestDispatcherProvider
import com.darshan.splitsmart.testutil.fixedClock
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File
import java.io.IOException

class ScanReceiptUseCaseTest {
    private val recognizer = FakeTextRecognizer()
    private val store = FakeReceiptImageStore()
    private val drafts = FakeReceiptDraftRepository()
    private val useCase = ScanReceiptUseCase(recognizer, store, drafts, fixedClock, TestDispatcherProvider(Dispatchers.Unconfined))

    private fun imageFile(): File = store.newCaptureFile().also { it.writeBytes(byteArrayOf(1)) }

    @Test
    fun `recognises, normalises and saves a draft`() = runTest {
        recognizer.lines = listOf("Paneer   Tikka  25O.OO", "Total ₹ 250.00")
        val result = useCase.recognize("g1", imageFile()) as ScanOutcome.Success
        assertThat(result.draft.ocrText).isEqualTo("Paneer Tikka 250.00\nTotal 250.00")
        assertThat(result.draft.groupId).isEqualTo("g1")
        assertThat(result.draft.imagePath).isNotNull()
        assertThat(result.draft.createdAt).isEqualTo(fixedClock.instant())
        assertThat(drafts.get(result.draft.id)).isEqualTo(result.draft)
    }

    @Test
    fun `no text is a NO_TEXT failure and nothing is saved`() = runTest {
        recognizer.lines = listOf("   ", "----")
        assertThat(useCase.recognize("g1", imageFile())).isEqualTo(ScanOutcome.Failure(ScanError.NO_TEXT))
        assertThat(drafts.drafts.value).isEmpty()
    }

    @Test
    fun `recognizer crash is RECOGNITION_FAILED`() = runTest {
        recognizer.failWith = IllegalStateException("ml kit died")
        assertThat(useCase.recognize("g1", imageFile())).isEqualTo(ScanOutcome.Failure(ScanError.RECOGNITION_FAILED))
    }

    @Test
    fun `missing image file is IMAGE_UNREADABLE`() = runTest {
        assertThat(useCase.recognize("g1", File("/nonexistent/x.jpg"))).isEqualTo(ScanOutcome.Failure(ScanError.IMAGE_UNREADABLE))
        assertThat(recognizer.calls.get()).isEqualTo(0)
    }

    @Test
    fun `gallery import copies then recognises`() = runTest {
        val result = useCase.importAndRecognize("g1", "content://media/external/images/42")
        assertThat(result).isInstanceOf(ScanOutcome.Success::class.java)
        assertThat(store.imported).containsExactly("content://media/external/images/42")
        assertThat(recognizer.lastFile!!.parentFile).isEqualTo(store.newCaptureFile().parentFile)
    }

    @Test
    fun `gallery import failure is IMAGE_UNREADABLE`() = runTest {
        store.importFailWith = ImageImportException(IOException("not an image"))
        assertThat(useCase.importAndRecognize("g1", "content://x")).isEqualTo(ScanOutcome.Failure(ScanError.IMAGE_UNREADABLE))
    }

    @Test
    fun `draft save failure is STORAGE_FAILED`() = runTest {
        drafts.failWith = IllegalStateException("disk full")
        assertThat(useCase.recognize("g1", imageFile())).isEqualTo(ScanOutcome.Failure(ScanError.STORAGE_FAILED))
    }
}
