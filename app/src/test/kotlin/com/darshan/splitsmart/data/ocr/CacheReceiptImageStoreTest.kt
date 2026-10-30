package com.darshan.splitsmart.data.ocr

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darshan.splitsmart.testutil.FIXED_INSTANT
import com.darshan.splitsmart.testutil.TestDispatcherProvider
import com.darshan.splitsmart.testutil.fixedClock
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.InputStream

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CacheReceiptImageStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var store: CacheReceiptImageStore

    @Before
    fun setUp() {
        File(context.cacheDir, "receipts").deleteRecursively()
        store = CacheReceiptImageStore(context, fixedClock, TestDispatcherProvider(Dispatchers.Unconfined))
    }

    @Test
    fun `capture files live in the receipts cache dir and get a FileProvider uri`() {
        val file = store.newCaptureFile()
        assertThat(file.parentFile).isEqualTo(File(context.cacheDir, "receipts"))
        assertThat(file.name).matches("[0-9a-f-]{36}\\.jpg")
        val uri = Uri.parse(store.uriFor(file))
        assertThat(uri.scheme).isEqualTo("content")
        assertThat(uri.authority).isEqualTo("com.darshan.splitsmart.fileprovider")
        assertThat(uri.path).isEqualTo("/receipts/${file.name}")
    }

    @Test
    fun `path traversal names are never resolved outside the receipts dir`() {
        listOf("../splitsmart.db", "../../databases/splitsmart.db", "/etc/passwd", "a/../../b.jpg", "..", "", "x\u0000.jpg", "sub/dir.jpg").forEach {
            assertWithMessage(it).that(store.resolveOwnedFile(it)).isNull()
        }
        val ok = store.newCaptureFile()
        assertThat(store.resolveOwnedFile(ok.name)).isEqualTo(ok)
    }

    @Test
    fun `import copies the source bytes into an owned file`() = runTest {
        val bytes = ByteArray(1_000) { 7 }
        val uri = Uri.parse("content://media/external/images/42")
        shadowOf(context.contentResolver).registerInputStream(uri, bytes.inputStream())
        val copy = store.import(uri.toString())
        assertThat(copy.parentFile).isEqualTo(File(context.cacheDir, "receipts"))
        assertThat(copy.readBytes()).isEqualTo(bytes)
    }

    @Test
    fun `import rejects unreadable and oversized sources`() = runTest {
        assertThat(runCatching { store.import("content://nope/1") }.exceptionOrNull()).isInstanceOf(ImageImportException::class.java)
        assertThat(runCatching { store.import("not a uri at all") }.exceptionOrNull()).isInstanceOf(ImageImportException::class.java)
        val hugeUri = Uri.parse("content://media/external/images/huge")
        shadowOf(context.contentResolver).registerInputStream(hugeUri, ZeroStream(CacheReceiptImageStore.MAX_BYTES + 1))
        assertThat(runCatching { store.import(hugeUri.toString()) }.exceptionOrNull()).isInstanceOf(ImageImportException::class.java)
        val emptyUri = Uri.parse("content://media/external/images/empty")
        shadowOf(context.contentResolver).registerInputStream(emptyUri, ByteArray(0).inputStream())
        assertThat(runCatching { store.import(emptyUri.toString()) }.exceptionOrNull()).isInstanceOf(ImageImportException::class.java)
        assertThat(File(context.cacheDir, "receipts").listFiles().orEmpty()).isEmpty() // partial copy cleaned up
    }

    @Test
    fun `import refuses sources that point inside app private storage`() = runTest {
        val db = File(context.getDatabasePath("x.db").parentFile, "x.db").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1)) }
        assertThat(runCatching { store.import(Uri.fromFile(db).toString()) }.exceptionOrNull()).isInstanceOf(ImageImportException::class.java)
    }

    @Test
    fun `purge deletes old files only`() = runTest {
        val old = store.newCaptureFile().apply { writeBytes(byteArrayOf(1)); setLastModified(FIXED_INSTANT.minusSeconds(100_000).toEpochMilli()) }
        val fresh = store.newCaptureFile().apply { writeBytes(byteArrayOf(1)); setLastModified(FIXED_INSTANT.toEpochMilli()) }
        assertThat(store.purgeOlderThan(FIXED_INSTANT.minusSeconds(3_600))).isEqualTo(1)
        assertThat(old.exists()).isFalse()
        assertThat(fresh.exists()).isTrue()
        store.delete(fresh)
        assertThat(fresh.exists()).isFalse()
    }
}

/** Streams [size] zero bytes without allocating them. */
private class ZeroStream(private var size: Long) : InputStream() {
    override fun read(): Int = if (size-- > 0) 0 else -1
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (size <= 0) return -1
        val n = minOf(len.toLong(), size).toInt()
        java.util.Arrays.fill(b, off, off + n, 0)
        size -= n
        return n
    }
}
