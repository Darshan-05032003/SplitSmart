package com.darshan.splitsmart.data.ocr

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.darshan.splitsmart.core.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

class ImageImportException(cause: Throwable) : Exception(cause)

/**
 * Owns the `receipts/` cache directory: every captured or imported image is a file the app named itself
 * (UUID + `.jpg`), so no caller-controlled path ever reaches the file system or the FileProvider.
 */
interface ReceiptImageStore {
    fun newCaptureFile(): File
    /** `content://` URI (FileProvider) for a file inside the store, for sharing with the camera / other apps. */
    fun uriFor(file: File): String
    /** Copies an external image (gallery pick) into the store. */
    suspend fun import(sourceUri: String): File
    /** Resolves a bare file name to a file inside the store, or null if the name tries to escape it. */
    fun resolveOwnedFile(name: String): File?
    suspend fun delete(file: File)
    suspend fun purgeOlderThan(cutoff: Instant): Int
}

@Singleton
class CacheReceiptImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) : ReceiptImageStore {
    private val dir: File get() = File(context.cacheDir, DIR_NAME).apply { mkdirs() }
    private val authority: String get() = "${context.packageName}.fileprovider"

    override fun newCaptureFile(): File = File(dir, "${UUID.randomUUID()}.jpg")

    override fun uriFor(file: File): String = FileProvider.getUriForFile(context, authority, file).toString()

    override suspend fun import(sourceUri: String): File = withContext(dispatchers.io) {
        val uri = runCatching { Uri.parse(sourceUri) }.getOrNull()
            ?: throw ImageImportException(IllegalArgumentException("unparseable uri"))
        when (uri.scheme) {
            "content" -> Unit
            "file" -> {
                val path = uri.path?.let { File(it).canonicalFile } ?: throw ImageImportException(IllegalArgumentException("no path"))
                // A file:// source must never point into the app's own private storage (database, prefs, ...).
                if (path.path.startsWith(context.dataDir.canonicalPath)) throw ImageImportException(SecurityException("private storage"))
            }
            else -> throw ImageImportException(IllegalArgumentException("unsupported scheme ${uri.scheme}"))
        }
        val target = newCaptureFile()
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw ImageImportException(IllegalStateException("no stream"))
            input.use { source ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BYTES) throw ImageImportException(IllegalStateException("image larger than $MAX_BYTES bytes"))
                        out.write(buffer, 0, read)
                    }
                    if (total == 0L) throw ImageImportException(IllegalStateException("empty image"))
                }
            }
            target
        } catch (e: ImageImportException) {
            target.delete(); throw e
        } catch (e: Exception) {
            target.delete(); throw ImageImportException(e)
        }
    }

    override fun resolveOwnedFile(name: String): File? {
        if (name.isEmpty() || name == "." || name == ".." || name.any { it == '/' || it == '\\' || it == '\u0000' }) return null
        val file = File(dir, name)
        return file.takeIf { it.canonicalFile.parentFile == dir.canonicalFile }
    }

    override suspend fun delete(file: File) = withContext(dispatchers.io) {
        if (resolveOwnedFile(file.name) == file) file.delete()
        Unit
    }

    override suspend fun purgeOlderThan(cutoff: Instant): Int = withContext(dispatchers.io) {
        dir.listFiles().orEmpty().filter { it.lastModified() < cutoff.toEpochMilli() }.count { it.delete() }
    }

    companion object {
        const val DIR_NAME = "receipts"
        /** 20 MB: larger than any phone photo, smaller than anything that could exhaust the cache. */
        const val MAX_BYTES = 20L * 1024 * 1024
    }
}
