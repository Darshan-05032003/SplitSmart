package com.darshan.splitsmart.domain.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OcrTextNormalizerTest {
    @Test
    fun `collapses whitespace and trims lines but keeps line breaks`() {
        assertThat(OcrTextNormalizer.normalize("  Paneer   Tikka \t 250.00 \r\n\n\n Total\t250.00  "))
            .isEqualTo("Paneer Tikka 250.00\nTotal 250.00")
    }

    @Test
    fun `fixes letter-digit confusions only inside numeric tokens`() {
        assertThat(OcrTextNormalizer.normalize("Dal Tadka 1O0.OO")).isEqualTo("Dal Tadka 100.00")
        assertThat(OcrTextNormalizer.normalize("Roti x2 l5.5O")).isEqualTo("Roti x2 15.50")
        assertThat(OcrTextNormalizer.normalize("Oil Masala")).isEqualTo("Oil Masala") // words untouched
        assertThat(OcrTextNormalizer.normalize("Total 3S0.00")).isEqualTo("Total 350.00")
    }

    @Test
    fun `normalises currency markers to a plain amount`() {
        assertThat(OcrTextNormalizer.normalize("Total ₹ 1,250.00")).isEqualTo("Total 1250.00")
        assertThat(OcrTextNormalizer.normalize("Total Rs.1,250.00")).isEqualTo("Total 1250.00")
        assertThat(OcrTextNormalizer.normalize("Total INR 1250")).isEqualTo("Total 1250")
        assertThat(OcrTextNormalizer.normalize("Total ₨1250/-")).isEqualTo("Total 1250")
    }

    @Test
    fun `fixes decimal separators mangled by OCR`() {
        assertThat(OcrTextNormalizer.normalize("Chai 25,00")).isEqualTo("Chai 25.00")
        assertThat(OcrTextNormalizer.normalize("Chai 25 .00")).isEqualTo("Chai 25.00")
        assertThat(OcrTextNormalizer.normalize("Chai 25. 00")).isEqualTo("Chai 25.00")
    }

    @Test
    fun `removes zero width, control and bidi characters`() {
        assertThat(OcrTextNormalizer.normalize("Tot​al\u0000 ‮250.00‏")).isEqualTo("Total 250.00")
    }

    @Test
    fun `caps the text length`() {
        val huge = "x".repeat(OcrTextNormalizer.MAX_CHARS + 5_000)
        assertThat(OcrTextNormalizer.normalize(huge)).hasLength(OcrTextNormalizer.MAX_CHARS)
    }

    @Test
    fun `drops empty lines and lines that are only noise`() {
        assertThat(OcrTextNormalizer.normalize("----------\n* * *\nTotal 10.00\n....\n")).isEqualTo("Total 10.00")
    }

    @Test
    fun `is idempotent`() {
        val once = OcrTextNormalizer.normalize("Dal  Tadka 1O0.OO\nTotal ₹ 1,250.00")
        assertThat(OcrTextNormalizer.normalize(once)).isEqualTo(once)
    }
}
