package com.darshan.splitsmart.data.upi

import com.google.common.truth.Truth.assertThat
import com.google.zxing.BinaryBitmap
import com.google.zxing.LuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Test

class QrCodeGeneratorTest {
    private val generator = ZxingQrCodeGenerator()

    @Test
    fun `renders a square module matrix that decodes back to the same content`() {
        val uri = UpiLinkBuilder.build("asha@okicici", "Asha Rao", 15_050, "Goa trip").uri
        val qr = generator.generate(uri)
        assertThat(qr.size).isGreaterThan(20)
        assertThat(qr.modules.size).isEqualTo(qr.size * qr.size)
        val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(MatrixSource(qr))))
        assertThat(decoded.text).isEqualTo(uri)
    }

    @Test
    fun `rejects empty or oversized content`() {
        assertThat(runCatching { generator.generate("") }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(runCatching { generator.generate("x".repeat(3_000)) }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `quiet zone is present on all sides`() {
        val qr = generator.generate("upi://pay?pa=a@upi&pn=A&am=1.00&cu=INR")
        val margin = ZxingQrCodeGenerator.QUIET_ZONE
        for (i in 0 until qr.size) {
            for (m in 0 until margin) {
                assertThat(qr[i, m]).isFalse(); assertThat(qr[m, i]).isFalse()
                assertThat(qr[i, qr.size - 1 - m]).isFalse(); assertThat(qr[qr.size - 1 - m, i]).isFalse()
            }
        }
    }
}

/** Scales each module to 4×4 pixels so zxing's reader can lock onto the finder patterns. */
private class MatrixSource(private val qr: QrMatrix, private val scale: Int = 4) : LuminanceSource(qr.size * scale, qr.size * scale) {
    override fun getRow(y: Int, row: ByteArray?): ByteArray {
        val out = row?.takeIf { it.size >= width } ?: ByteArray(width)
        for (x in 0 until width) out[x] = if (qr[x / scale, y / scale]) 0 else 0xFF.toByte()
        return out
    }

    override fun getMatrix(): ByteArray {
        val out = ByteArray(width * height)
        for (y in 0 until height) for (x in 0 until width) out[y * width + x] = if (qr[x / scale, y / scale]) 0 else 0xFF.toByte()
        return out
    }
}
