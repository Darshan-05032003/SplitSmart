package com.darshan.splitsmart.core

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class MoneyTest {
    @Test
    fun `formats paise as rupees with indian grouping`() {
        assertThat(Money.formatInr(0)).isEqualTo("₹0.00")
        assertThat(Money.formatInr(5)).isEqualTo("₹0.05")
        assertThat(Money.formatInr(123_456)).isEqualTo("₹1,234.56")
        assertThat(Money.formatInr(1_234_567_890)).isEqualTo("₹1,23,45,678.90")
        assertThat(Money.formatInr(-250)).isEqualTo("-₹2.50")
    }

    @Test
    fun `formats rupees for upi amount parameter with two decimals and no grouping`() {
        assertThat(Money.toUpiAmount(1)).isEqualTo("0.01")
        assertThat(Money.toUpiAmount(123_456)).isEqualTo("1234.56")
        assertThat(Money.toUpiAmount(100_000_000)).isEqualTo("1000000.00")
    }

    @Test
    fun `parses user input into paise`() {
        assertThat(Money.parseInrToPaise("120")).isEqualTo(12_000)
        assertThat(Money.parseInrToPaise("120.5")).isEqualTo(12_050)
        assertThat(Money.parseInrToPaise(" ₹1,234.56 ")).isEqualTo(123_456)
        assertThat(Money.parseInrToPaise("Rs. 99.99")).isEqualTo(9_999)
        assertThat(Money.parseInrToPaise("0")).isEqualTo(0)
        assertThat(Money.parseInrToPaise(".5")).isEqualTo(50)
    }

    @Test
    fun `rejects hostile or malformed input`() {
        listOf("", "   ", "abc", "-5", "1.234", "1e5", "12..3", "1,2,3.4,5", "٣٤", "NaN", "Infinity", "99999999999999999999", "1 2").forEach {
            assertWithMessage(it).that(Money.parseInrToPaise(it)).isNull()
        }
    }

    @Test
    fun `rejects amounts above the hard cap`() {
        assertThat(Money.parseInrToPaise("10000000.00")).isEqualTo(Money.MAX_PAISE)
        assertThat(Money.parseInrToPaise("10000000.01")).isNull()
    }
}
