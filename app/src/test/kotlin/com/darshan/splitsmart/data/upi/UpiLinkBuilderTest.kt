package com.darshan.splitsmart.data.upi

import com.darshan.splitsmart.core.Money
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class UpiLinkBuilderTest {
    @Test
    fun `builds a canonical upi pay link with rupees to two decimals`() {
        val link = UpiLinkBuilder.build(vpa = "asha@okicici", name = "Asha Rao", amountPaise = 15_050, note = "Goa trip")
        assertThat(link.uri).isEqualTo("upi://pay?pa=asha%40okicici&pn=Asha%20Rao&am=150.50&cu=INR&tn=Goa%20trip")
        assertThat(link.amountPaise).isEqualTo(15_050)
        assertThat(link.payeeVpa).isEqualTo("asha@okicici")
    }

    @Test
    fun `lowercases and trims the vpa, collapses the name`() {
        val link = UpiLinkBuilder.build(" Asha.Rao@OkICICI ", "  Asha   Rao ", 100, "x")
        assertThat(link.uri).startsWith("upi://pay?pa=asha.rao%40okicici&pn=Asha%20Rao&")
    }

    @Test
    fun `percent-encodes everything that could break the query`() {
        val link = UpiLinkBuilder.build("a@upi", "Tom & Jerry's #1 Café", 1, "Dinner=fun?&x=1 ₹ 100%")
        assertThat(link.uri).doesNotContain("&pa=x")
        assertThat(link.uri).contains("pn=Tom%20%26%20Jerry%27s%20%231%20Caf%C3%A9")
        assertThat(link.uri).contains("tn=Dinner%3Dfun%3F%26x%3D1%20%E2%82%B9%20100%25")
        // only our five parameters, in order
        assertThat(link.uri.substringAfter('?').split('&').map { it.substringBefore('=') }).containsExactly("pa", "pn", "am", "cu", "tn").inOrder()
    }

    @Test
    fun `optional transaction reference is included and validated`() {
        val link = UpiLinkBuilder.build("a@upi", "A", 100, "n", txnRef = "SS20261030ABC123")
        assertThat(link.uri).endsWith("&tn=n&tr=SS20261030ABC123")
        assertThat(runCatching { UpiLinkBuilder.build("a@upi", "A", 100, "n", txnRef = "bad ref&x") }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(runCatching { UpiLinkBuilder.build("a@upi", "A", 100, "n", txnRef = "x".repeat(40)) }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `rejects invalid payees and amounts`() {
        listOf("", "noat", "a@", "@b", "a b@upi", "javascript:alert(1)@upi", "a@upi?am=1", "a@upi&pa=evil@upi").forEach {
            assertWithMessage(it).that(runCatching { UpiLinkBuilder.build(it, "A", 100, "n") }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        }
        listOf(0L, -1L, Money.MAX_PAISE + 1, Long.MIN_VALUE, Long.MAX_VALUE).forEach {
            assertWithMessage("$it").that(runCatching { UpiLinkBuilder.build("a@upi", "A", it, "n") }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun `hostile names and notes are sanitised and capped`() {
        val link = UpiLinkBuilder.build("a@upi", "Evil‮\u0000Name\n", 100, "x".repeat(500))
        assertThat(link.payeeName).isEqualTo("EvilName")
        assertThat(link.note.length).isEqualTo(UpiLinkBuilder.MAX_NOTE_LENGTH)
        assertThat(link.uri.length).isLessThan(600)
        assertThat(runCatching { UpiLinkBuilder.build("a@upi", "   ", 100, "n") }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(UpiLinkBuilder.build("a@upi", "A", 100, "").uri).endsWith("&cu=INR")
    }

    @Test
    fun `unpaired surrogates are dropped so build and parse agree`() {
        val lone = "Asha \uD83D Rao" // high surrogate without its pair (a split emoji from a bad paste or OCR)
        val link = UpiLinkBuilder.build("asha@upi", lone, 100, lone)
        assertThat(link.payeeName).isEqualTo("Asha Rao")
        assertThat(UpiLinkBuilder.parse(link.uri)).isEqualTo(link)
        val paired = UpiLinkBuilder.build("asha@upi", "Asha \uD83D\uDE00", 100, "")
        assertThat(paired.payeeName).isEqualTo("Asha \uD83D\uDE00")
        assertThat(UpiLinkBuilder.parse(paired.uri)).isEqualTo(paired)
    }

    @Test
    fun `parse round trips what build produces`() {
        val original = UpiLinkBuilder.build("asha@okicici", "Asha Rao & co", 123_456, "Trip ₹ 2026", txnRef = "SS1")
        val parsed = UpiLinkBuilder.parse(original.uri)
        assertThat(parsed).isEqualTo(original)
    }

    @Test
    fun `parse rejects tampered or foreign links`() {
        listOf(
            "", "http://pay?pa=a@upi&am=1", "javascript:alert(1)", "upi://collect?pa=a@upi&am=1", "upi://pay?pa=a@upi",
            "upi://pay?pa=a@upi&am=-5&cu=INR", "upi://pay?pa=a@upi&am=1e3&cu=INR", "upi://pay?pa=a@upi&am=1&cu=USD",
            "upi://pay?pa=a@upi&pa=b@upi&am=1&cu=INR", "upi://pay?pa=a@upi&am=1&cu=INR&url=http://evil",
            "upi://pay?pa=a@upi&am=100000000.00&cu=INR", "upi://pay?pa=bad vpa&am=1&cu=INR", "upi://pay?pa=a@upi&am=1&cu=INR&tr=x y",
        ).forEach { assertWithMessage(it).that(UpiLinkBuilder.parse(it)).isNull() }
        val lenient = UpiLinkBuilder.parse("UPI://PAY?pa=A%40upi&am=1.5&cu=INR&pn=Some%20one&mode=02&purpose=00")
        assertThat(lenient).isNotNull()
        assertThat(lenient!!.amountPaise).isEqualTo(150)
        assertThat(lenient.payeeVpa).isEqualTo("a@upi")
        assertThat(lenient.payeeName).isEqualTo("Some one")
    }
}
