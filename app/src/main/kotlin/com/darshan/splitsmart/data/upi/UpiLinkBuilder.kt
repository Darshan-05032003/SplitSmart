package com.darshan.splitsmart.data.upi

import com.darshan.splitsmart.core.Money
import com.darshan.splitsmart.domain.model.NameValidation
import com.darshan.splitsmart.domain.model.NameValidator
import com.darshan.splitsmart.domain.model.VpaValidation
import com.darshan.splitsmart.domain.model.VpaValidator

/** A validated `upi://pay` deep link. Construct only through [UpiLinkBuilder]. */
data class UpiPayLink(
    val payeeVpa: String,
    val payeeName: String,
    val amountPaise: Long,
    val note: String,
    val txnRef: String?,
) {
    val uri: String
        get() = buildString {
            append("upi://pay?pa=").append(UpiLinkBuilder.encode(payeeVpa))
            append("&pn=").append(UpiLinkBuilder.encode(payeeName))
            append("&am=").append(Money.toUpiAmount(amountPaise))
            append("&cu=INR")
            if (note.isNotEmpty()) append("&tn=").append(UpiLinkBuilder.encode(note))
            if (txnRef != null) append("&tr=").append(txnRef)
        }
}

/**
 * Builds and parses NPCI `upi://pay` links. Every value is validated (VPA grammar, amount range, name/note
 * sanitised) and RFC 3986 percent-encoded, so nothing a user types can inject extra parameters.
 */
object UpiLinkBuilder {
    const val MAX_NOTE_LENGTH = 80
    private val TXN_REF = Regex("^[A-Za-z0-9]{1,35}$")
    private val AMOUNT = Regex("^\\d{1,8}(?:\\.\\d{1,2})?$")
    private val INVISIBLE = Regex("[\\p{Cntrl}\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2064\\u2066-\\u2069\\uFEFF]")
    private val ALLOWED_PARAMS = setOf("pa", "pn", "am", "cu", "tn", "tr", "mc", "tid", "mode", "purpose", "orgid", "sign")

    fun build(vpa: String, name: String, amountPaise: Long, note: String, txnRef: String? = null): UpiPayLink {
        val payee = when (val v = VpaValidator.validate(vpa)) {
            is VpaValidation.Valid -> v.vpa
            is VpaValidation.Invalid -> throw IllegalArgumentException("invalid vpa: ${v.error}")
        }
        val payeeName = when (val v = NameValidator.validate(clean(name))) {
            is NameValidation.Valid -> v.name
            is NameValidation.Invalid -> throw IllegalArgumentException("invalid payee name: ${v.error}")
        }
        require(amountPaise in 1..Money.MAX_PAISE) { "amount out of range: $amountPaise" }
        require(txnRef == null || TXN_REF.matches(txnRef)) { "invalid transaction reference" }
        return UpiPayLink(payee, payeeName, amountPaise, sanitizeNote(note), txnRef)
    }

    /** Strict parse of a link (our own or scanned): null for anything that is not a well-formed INR `upi://pay`. */
    fun parse(uri: String): UpiPayLink? {
        if (uri.length > 2_048) return null
        val lower = uri.lowercase()
        if (!lower.startsWith("upi://pay?")) return null
        val params = linkedMapOf<String, String>()
        for (pair in uri.substring("upi://pay?".length).split('&')) {
            if (pair.isEmpty()) return null
            val key = pair.substringBefore('=').lowercase()
            val value = decode(pair.substringAfter('=', "")) ?: return null
            if (key !in ALLOWED_PARAMS || params.put(key, value) != null) return null
        }
        val vpa = (VpaValidator.validate(params["pa"] ?: return null) as? VpaValidation.Valid)?.vpa ?: return null
        val amountText = params["am"] ?: return null
        if (!AMOUNT.matches(amountText)) return null
        val amount = Money.parseInrToPaise(amountText)?.takeIf { it in 1..Money.MAX_PAISE } ?: return null
        if (params["cu"]?.let { it.uppercase() != "INR" } == true) return null
        val name = params["pn"]?.let { (NameValidator.validate(clean(it)) as? NameValidation.Valid)?.name } ?: ""
        val txnRef = params["tr"]?.also { if (!TXN_REF.matches(it)) return null }
        return UpiPayLink(vpa, name, amount, sanitizeNote(params["tn"] ?: ""), txnRef)
    }

    private fun sanitizeNote(note: String): String = clean(note).replace(Regex("\\s+"), " ").trim().take(MAX_NOTE_LENGTH)

    /** Removes invisible/bidi characters and unpaired UTF-16 surrogates (which cannot be UTF-8 encoded and would round-trip as `?`). */
    private fun clean(value: String): String {
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            when {
                c.isHighSurrogate() && i + 1 < value.length && value[i + 1].isLowSurrogate() -> { sb.append(c).append(value[i + 1]); i += 2; continue }
                c.isSurrogate() -> Unit // unpaired: drop
                else -> sb.append(c)
            }
            i++
        }
        return INVISIBLE.replace(sb, "")
    }

    /** RFC 3986: everything but unreserved characters is percent-encoded (spaces become `%20`, never `+`). */
    fun encode(value: String): String = buildString {
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val c = byte.toInt() and 0xFF
            if (c.toChar().isLetterOrDigit() && c < 128 || c.toChar() in "-._~") append(c.toChar()) else append('%').append("%02X".format(c))
        }
    }

    private fun decode(value: String): String? {
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '%') {
                if (i + 2 >= value.length) return null
                val hex = value.substring(i + 1, i + 3).toIntOrNull(16) ?: return null
                out.write(hex); i += 3
            } else {
                out.write(c.toString().toByteArray(Charsets.UTF_8)); i++
            }
        }
        return out.toString(Charsets.UTF_8.name())
    }
}
