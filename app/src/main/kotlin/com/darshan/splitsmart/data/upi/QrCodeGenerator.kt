package com.darshan.splitsmart.data.upi

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import javax.inject.Inject

/** Platform-free QR bitmap: `size × size` modules, true = dark. Rendered to an Android Bitmap in the UI layer. */
class QrMatrix(val size: Int, val modules: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = modules[y * size + x]
}

interface QrCodeGenerator {
    fun generate(content: String): QrMatrix
}

class ZxingQrCodeGenerator @Inject constructor() : QrCodeGenerator {
    override fun generate(content: String): QrMatrix {
        require(content.isNotEmpty()) { "empty QR content" }
        require(content.length <= MAX_CONTENT) { "QR content too long: ${content.length}" }
        val hints = mapOf(EncodeHintType.MARGIN to QUIET_ZONE, EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
        val size = matrix.width
        val modules = BooleanArray(size * size)
        for (y in 0 until size) for (x in 0 until size) modules[y * size + x] = matrix[x, y]
        return QrMatrix(size, modules)
    }

    companion object {
        const val QUIET_ZONE = 4
        const val MAX_CONTENT = 2_000
    }
}
