package com.darshan.splitsmart.ui.designsystem

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * Reduced motion has to reach every animation, and Compose's own defaults never ask for it: `Modifier.animateItem()` with no
 * arguments fades and moves rows on Compose's springs even when Reduce motion is on. Screen code passes all three specs from
 * the motion vocabulary (`motion.spec(MotionToken.…)`, or `null` for "no fade"). Plain JUnit over the screen sources.
 */
class MotionAuditTest {
    /** The argument text of every `animateItem(…)` call in [text], with nested parentheses balanced. */
    private fun calls(text: String): List<Pair<Int, String>> = Regex("""\banimateItem\(""").findAll(text).map { match ->
        var depth = 1
        var end = match.range.last + 1
        while (end < text.length && depth > 0) {
            when (text[end]) {
                '(' -> depth++
                ')' -> depth--
            }
            end++
        }
        text.substring(0, match.range.first).count { it == '\n' } + 1 to text.substring(match.range.last + 1, end - 1)
    }.toList()

    private fun screenFiles(): List<File> = listOf("src/main/kotlin/com/darshan/splitsmart/ui", "src/debug/kotlin/com/darshan/splitsmart/ui")
        .map(::File)
        .flatMap { base -> base.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
        .filterNot { "/ui/designsystem/" in it.invariantSeparatorsPath }

    private fun findings(name: String, text: String): List<String> = calls(text).mapNotNull { (line, args) ->
        val complete = listOf("fadeInSpec", "placementSpec", "fadeOutSpec").all { it in args }
        if (complete) null else "$name:$line animateItem($args)"
    }

    @Test
    fun list_items_animate_on_the_motion_vocabulary() {
        val files = screenFiles()
        assertWithMessage("screen sources found").that(files.size).isAtLeast(15)
        val report = files.flatMap { findings(it.invariantSeparatorsPath, it.readText()) }
        assertWithMessage("animateItem without all three motion specs (ignores Reduce motion):\n${report.joinToString("\n")}").that(report).isEmpty()
    }

    @Test
    fun the_audit_catches_default_item_animations() {
        assertThat(findings("x.kt", "Modifier.animateItem()")).hasSize(1)
        assertThat(findings("x.kt", "Modifier.animateItem(placementSpec = motion.spec(MotionToken.Settle))")).hasSize(1)
        assertThat(
            findings("x.kt", "Modifier.animateItem(fadeInSpec = null, placementSpec = motion.spec(MotionToken.Settle), fadeOutSpec = motion.spec(MotionToken.Micro))"),
        ).isEmpty()
    }
}
