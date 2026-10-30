package com.darshan.splitsmart.domain.parser

import com.darshan.splitsmart.domain.model.ExtractedItem
import com.darshan.splitsmart.domain.model.ExtractedReceipt
import com.darshan.splitsmart.domain.model.NameValidator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal

class ReceiptParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Parses the strict receipt JSON (`{items:[{name,qty,unitPaise,totalPaise}],taxPaise,tipPaise,totalPaise}`).
 * Tolerant about types: numbers may arrive as strings; a value with a decimal point is taken as rupees and
 * converted to paise, an integer is already paise. Everything else is a [ReceiptParseException].
 */
class ReceiptJsonParser(private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }) {

    fun parse(text: String): ExtractedReceipt {
        val root = runCatching { json.parseToJsonElement(text) }.getOrElse { throw ReceiptParseException("not json", it) }
        val obj = root as? JsonObject ?: throw ReceiptParseException("root is not an object")
        val items = when (val el = obj["items"]) {
            null, JsonNull -> emptyList()
            is JsonArray -> el.mapNotNull { (it as? JsonObject)?.let(::parseItem) }
            else -> throw ReceiptParseException("items is not an array")
        }
        return ExtractedReceipt(items, paise(obj["taxPaise"]), paise(obj["tipPaise"]), paise(obj["totalPaise"]))
    }

    private fun parseItem(obj: JsonObject): ExtractedItem {
        val name = (obj["name"] as? JsonPrimitive)?.content?.trim()?.replace(Regex("\\s+"), " ")?.take(NameValidator.MAX_LENGTH) ?: ""
        return ExtractedItem(name = name, qty = int(obj["qty"], default = 1), unitPaise = paise(obj["unitPaise"]), totalPaise = paise(obj["totalPaise"]))
    }

    private fun int(el: JsonElement?, default: Int): Int {
        val p = el as? JsonPrimitive ?: return default
        if (p is JsonNull) return default
        val value = p.content.trim().toBigDecimalOrNull() ?: throw ReceiptParseException("qty is not a number: ${p.content}")
        if (value.abs() > BigDecimal(1_000_000)) throw ReceiptParseException("qty absurd")
        return value.toInt()
    }

    private fun paise(el: JsonElement?): Long {
        val p = el as? JsonPrimitive ?: return 0
        if (p is JsonNull) return 0
        val content = p.content.trim()
        if (content.isEmpty()) return 0
        val value = content.toBigDecimalOrNull() ?: throw ReceiptParseException("amount is not a number: $content")
        if (value.abs() > ABSURD) throw ReceiptParseException("amount absurd")
        val paise = if (content.contains('.') || content.contains('e') || content.contains('E')) value.movePointRight(2) else value
        return paise.setScale(0, java.math.RoundingMode.HALF_UP).toLong()
    }

    private companion object {
        val ABSURD = BigDecimal("1000000000000") // 10^12: far beyond any receipt in rupees or paise
    }
}
