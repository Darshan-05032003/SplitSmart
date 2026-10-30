package com.darshan.splitsmart.domain.model

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class ValidatorsTest {
    @Test
    fun `name validator trims and accepts normal names`() {
        assertThat(NameValidator.validate("  Goa trip ")).isEqualTo(NameValidation.Valid("Goa trip"))
        assertThat(NameValidator.validate("Ümit & Søren 🎉")).isEqualTo(NameValidation.Valid("Ümit & Søren 🎉"))
        assertThat(NameValidator.validate("अंकित")).isEqualTo(NameValidation.Valid("अंकित"))
    }

    @Test
    fun `name validator rejects blank, too long and control characters`() {
        assertThat(NameValidator.validate("")).isEqualTo(NameValidation.Invalid(NameError.BLANK))
        assertThat(NameValidator.validate("   ")).isEqualTo(NameValidation.Invalid(NameError.BLANK))
        assertThat(NameValidator.validate("a".repeat(61))).isEqualTo(NameValidation.Invalid(NameError.TOO_LONG))
        assertThat(NameValidator.validate("bad\u0000name")).isEqualTo(NameValidation.Invalid(NameError.INVALID_CHARS))
        assertThat(NameValidator.validate("rtl‮evil")).isEqualTo(NameValidation.Invalid(NameError.INVALID_CHARS))
        assertThat(NameValidator.validate("line\nbreak")).isEqualTo(NameValidation.Invalid(NameError.INVALID_CHARS))
    }

    @Test
    fun `name validator collapses internal whitespace`() {
        assertThat(NameValidator.validate("Goa    trip")).isEqualTo(NameValidation.Valid("Goa trip"))
    }

    @Test
    fun `vpa validator accepts well formed handles and lowercases them`() {
        assertThat(VpaValidator.validate("Asha.Rao@okicici")).isEqualTo(VpaValidation.Valid("asha.rao@okicici"))
        assertThat(VpaValidator.validate("9876543210@ybl")).isEqualTo(VpaValidation.Valid("9876543210@ybl"))
        assertThat(VpaValidator.validate("a-b_c@upi")).isEqualTo(VpaValidation.Valid("a-b_c@upi"))
    }

    @Test
    fun `vpa validator rejects hostile input`() {
        listOf(
            "", "noatsign", "@ybl", "user@", "us er@ybl", "user@y bl", "user@@ybl", "user@ybl?am=1",
            "javascript:alert(1)@ybl", "user@ybl&pa=x", "a".repeat(260) + "@ybl", "user@ybl1", "ユーザー@ybl",
            "user@ybl\u0000", "user%40ybl", "user@ybl/x",
        ).forEach {
            assertWithMessage(it).that(VpaValidator.validate(it)).isInstanceOf(VpaValidation.Invalid::class.java)
        }
    }

    @Test
    fun `vpa validator reports the specific error`() {
        assertThat(VpaValidator.validate("")).isEqualTo(VpaValidation.Invalid(VpaError.BLANK))
        assertThat(VpaValidator.validate("noat")).isEqualTo(VpaValidation.Invalid(VpaError.MISSING_HANDLE))
        assertThat(VpaValidator.validate("x y@ybl")).isEqualTo(VpaValidation.Invalid(VpaError.INVALID_CHARS))
    }
}
