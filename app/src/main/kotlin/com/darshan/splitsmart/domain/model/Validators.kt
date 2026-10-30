package com.darshan.splitsmart.domain.model

enum class NameError { BLANK, TOO_LONG, INVALID_CHARS }

sealed interface NameValidation {
    data class Valid(val name: String) : NameValidation
    data class Invalid(val error: NameError) : NameValidation
}

/** Group, member and bill names: trimmed, whitespace-collapsed, 1..60 chars, no control / bidi-override characters. */
object NameValidator {
    const val MAX_LENGTH = 60
    private val WHITESPACE = Regex("\\s+")

    fun validate(raw: String, maxLength: Int = MAX_LENGTH): NameValidation {
        if (raw.any { it.isISOControl() || it in BIDI_CONTROLS }) return NameValidation.Invalid(NameError.INVALID_CHARS)
        val name = raw.trim().replace(WHITESPACE, " ")
        if (name.isEmpty()) return NameValidation.Invalid(NameError.BLANK)
        if (name.length > maxLength) return NameValidation.Invalid(NameError.TOO_LONG)
        return NameValidation.Valid(name)
    }

    /** Explicit bidi overrides / isolates that let a name render differently from how it is stored. */
    private val BIDI_CONTROLS = setOf('‪', '‫', '‬', '‭', '‮', '⁦', '⁧', '⁨', '⁩', '‎', '‏')
}

enum class VpaError { BLANK, MISSING_HANDLE, INVALID_CHARS, TOO_LONG }

sealed interface VpaValidation {
    data class Valid(val vpa: String) : VpaValidation
    data class Invalid(val error: VpaError) : VpaValidation
}

/**
 * UPI VPA (`user@handle`): the user part allows letters, digits, `.`, `-`, `_`; the handle is letters only.
 * Everything is ASCII, so no query/URL meta characters can ever be smuggled into a `upi://pay` link.
 */
object VpaValidator {
    private val USER = Regex("^[A-Za-z0-9._-]{1,256}$")
    private val HANDLE = Regex("^[A-Za-z]{2,64}$")

    fun validate(raw: String): VpaValidation {
        val vpa = raw.trim()
        if (vpa.isEmpty()) return VpaValidation.Invalid(VpaError.BLANK)
        if (vpa.length > 320) return VpaValidation.Invalid(VpaError.TOO_LONG)
        val at = vpa.indexOf('@')
        if (at <= 0 || at == vpa.lastIndex) return VpaValidation.Invalid(VpaError.MISSING_HANDLE)
        val user = vpa.substring(0, at)
        val handle = vpa.substring(at + 1)
        if (!USER.matches(user) || !HANDLE.matches(handle)) return VpaValidation.Invalid(VpaError.INVALID_CHARS)
        return VpaValidation.Valid(vpa.lowercase())
    }
}
