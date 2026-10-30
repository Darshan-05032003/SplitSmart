package com.darshan.splitsmart.domain.parser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Turns "almost JSON" from an LLM into JSON: strips code fences and prose, normalises smart quotes, removes comments,
 * `NaN`/`Infinity`, trailing commas, and closes truncated objects/arrays/strings. String contents are never touched.
 * Returns null when there is no object to recover or the result still does not parse.
 */
object JsonRepair {
    const val MAX_INPUT = 200_000
    private val FENCE = Regex("```[A-Za-z]*")
    private val lenient = Json { isLenient = false; ignoreUnknownKeys = true }

    fun repair(raw: String): String? {
        if (raw.isEmpty() || raw.length > MAX_INPUT) return null
        var s = FENCE.replace(raw, "")
        s = s.replace('“', '"').replace('”', '"').replace('‘', '"').replace('’', '"')
        val start = s.indexOf('{')
        if (start < 0) return null
        val repaired = walk(s.substring(start))
        return runCatching { lenient.parseToJsonElement(repaired) }.getOrNull()?.takeIf { it is JsonObject }?.let { repaired }
    }

    /** Single pass over the text with string awareness; applies all structural repairs. */
    private fun walk(input: String): String {
        val out = StringBuilder(input.length + 8)
        val stack = ArrayDeque<Char>()
        var inString = false
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (inString) {
                out.append(c)
                if (c == '\\' && i + 1 < input.length) { out.append(input[i + 1]); i += 2; continue }
                if (c == '"') inString = false
                i++
                continue
            }
            when {
                c == '"' -> { inString = true; out.append(c); i++ }
                c == '/' && input.startsWith("//", i) -> { i = input.indexOf('\n', i).let { if (it < 0) input.length else it } }
                c == '/' && input.startsWith("/*", i) -> { val close = input.indexOf("*/", i + 2); i = if (close < 0) input.length else close + 2 }
                c == '{' || c == '[' -> { stack.addLast(if (c == '{') '}' else ']'); out.append(c); i++ }
                c == '}' || c == ']' -> {
                    if (stack.lastOrNull() == c) stack.removeLast()
                    trimDanglingSeparator(out)
                    out.append(c)
                    i++
                    if (stack.isEmpty()) break // top-level object closed: anything after it is prose
                }
                c == ',' -> {
                    var j = i + 1
                    while (j < input.length && input[j].isWhitespace()) j++
                    if (j < input.length && (input[j] == '}' || input[j] == ']')) { i++ } else { out.append(c); i++ }
                }
                input.startsWith("NaN", i) -> { out.append("null"); i += 3 }
                input.startsWith("-Infinity", i) -> { out.append("null"); i += 9 }
                input.startsWith("Infinity", i) -> { out.append("null"); i += 8 }
                else -> { out.append(c); i++ }
            }
        }
        if (inString) out.append('"')
        trimDanglingSeparator(out)
        while (stack.isNotEmpty()) { out.append(stack.removeLast()); trimDanglingSeparator(out) }
        return out.toString()
    }

    /** `..., }` → `...}`;  `"key": }` → `"key": null}`. */
    private fun trimDanglingSeparator(out: StringBuilder) {
        var end = out.length
        while (end > 0 && out[end - 1].isWhitespace()) end--
        if (end == 0) return
        when (out[end - 1]) {
            ',' -> out.setLength(end - 1)
            ':' -> out.append("null")
        }
    }
}
