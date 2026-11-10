package com.darshan.splitsmart.ui.designsystem

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * Emoji are accents in a rupee app, so none may draw another currency or read as something else. The money emoji (💰 💲 💵 💴
 * 💶 💷 💸 💱) all print a dollar, yen, euro or pound sign in the common emoji fonts, and 👯 (dancers in bunny ears) was
 * once the "group" heading. Groups and people are 👥, settling up over UPI is 📲, a cash payment is 🤝, what each person
 * pays is 👛. 🪙 (Unicode 13) drew an empty box in the screenshot renderer's emoji font, as it would on an older phone that
 * cannot fetch a newer font. Plain JUnit over the app's sources and resources.
 */
class EmojiAuditTest {
    private val forbidden = mapOf(
        "💰" to "money bag (prints \$)",
        "💲" to "heavy dollar sign",
        "💵" to "dollar banknote",
        "💴" to "yen banknote",
        "💶" to "euro banknote",
        "💷" to "pound banknote",
        "💸" to "money with wings (dollar notes)",
        "💱" to "currency exchange",
        "👯" to "dancers in bunny ears (use 👥 for groups and people)",
        "🪙" to "coin, Unicode 13: an empty box where the emoji font is older",
    )

    private fun sources(): List<File> = listOf("src/main/kotlin", "src/main/res", "src/debug/kotlin")
        .map(::File)
        .flatMap { base -> base.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }.toList() }

    private fun findings(name: String, text: String): List<String> =
        text.lines().flatMapIndexed { index, line -> forbidden.filterKeys { it in line }.map { (emoji, why) -> "$name:${index + 1} $emoji $why" } }

    @Test
    fun no_off_locale_or_misleading_emoji_in_the_app() {
        val files = sources()
        assertWithMessage("source files found").that(files.size).isAtLeast(50)
        val report = files.flatMap { findings(it.invariantSeparatorsPath, it.readText()) }
        assertWithMessage("emoji that print another currency or read wrong:\n${report.joinToString("\n")}").that(report).isEmpty()
    }

    @Test
    fun the_audit_catches_each_forbidden_emoji() {
        for (emoji in forbidden.keys) assertWithMessage(emoji).that(findings("x.kt", "emoji = \"$emoji\"")).hasSize(1)
        assertThat(findings("x.kt", "emoji = \"👥\"")).isEmpty()
        assertThat(findings("x.kt", "emoji = \"👛\"")).isEmpty()
    }
}
