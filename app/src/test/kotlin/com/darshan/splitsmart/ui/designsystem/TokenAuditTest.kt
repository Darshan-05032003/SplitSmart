package com.darshan.splitsmart.ui.designsystem

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * Screens use design-system tokens only: no raw colours, dp/sp, durations or easing outside `ui/designsystem` and `ui/theme`.
 * Same rules as `scripts/token_audit.py`; a deliberate exception carries `// token-audit: allow (reason)`.
 *
 * Screens built before the design system are listed in `scripts/token_audit_legacy.txt` with the raw values each still has.
 * The count may only go down, and a screen dropped from the list must be clean. Plain JUnit over source files: milliseconds.
 */
class TokenAuditTest {
    private val rules = mapOf(
        "colour" to Regex("""Color\(\s*0x|Color\(\s*[0-9]|"#[0-9A-Fa-f]{3,8}"|parseColor\(|Color\.(White|Black|Red|Green|Blue|Gray|Yellow|Cyan|Magenta|DarkGray|LightGray)\b"""),
        "size" to Regex("""(?<![\w.])(?!0+(\.0+)?f?\.(dp|sp)\b)\d+(\.\d+)?f?\.(dp|sp)\b"""),
        "duration" to Regex(
            """\btween\(\s*\d|\bsnap\(\s*\d|\bdelay\(\s*\d|durationMillis\s*=\s*\d|delayMillis\s*=\s*\d|\bkeyframes\s*\{|\bspring\s*(<[^>]*>)?\(|\d+\.(milliseconds|seconds)\b|const val \w*(_MS|MILLIS)\w*\s*(:\s*\w+)?\s*=\s*\d""",
        ),
        "easing" to Regex("""CubicBezierEasing\(|\b(LinearEasing|FastOutSlowInEasing|LinearOutSlowInEasing|FastOutLinearInEasing)\b|\bEase(In|Out|InOut)\w*\b|\bEasing\s*\{"""),
    )

    /** Unit tests run with the app module as the working directory. */
    private val projectRoot = File("..")

    private fun screenFiles(): List<File> = listOf("src/main/kotlin/com/darshan/splitsmart/ui", "src/debug/kotlin/com/darshan/splitsmart/ui")
        .map(::File)
        .flatMap { base -> base.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
        .filterNot { file -> file.invariantSeparatorsPath.let { "/ui/designsystem/" in it || "/ui/theme/" in it } }

    private fun violations(lines: List<String>): List<String> {
        val found = mutableListOf<String>()
        var previous = ""
        lines.forEachIndexed { index, line ->
            val stripped = line.trim()
            val allowedAbove = previous.startsWith("//") && ALLOW in previous
            val skip = listOf("import ", "package ", "//", "*", "/*").any(stripped::startsWith) || ALLOW in line || allowedAbove
            previous = stripped
            if (!skip) {
                val code = line.substringBefore("//")
                for ((name, rule) in rules) if (rule.containsMatchIn(code)) found += "${index + 1}: [$name] $stripped"
            }
        }
        return found
    }

    private fun legacyBudget(): Map<String, Int> = File(projectRoot, "scripts/token_audit_legacy.txt").readLines()
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .associate { line -> line.substringBeforeLast(' ') to line.substringAfterLast(' ').toInt() }

    @Test
    fun screen_code_uses_only_design_system_tokens() {
        val files = screenFiles()
        assertWithMessage("screen sources found").that(files.size).isAtLeast(15)
        val budget = legacyBudget()
        val report = files.mapNotNull { file ->
            val name = "app/" + file.invariantSeparatorsPath
            val found = violations(file.readLines())
            val allowed = budget[name] ?: 0
            if (found.size > allowed) "$name: ${found.size} raw values, allowed $allowed\n  " + found.joinToString("\n  ") else null
        }
        assertWithMessage("raw values in screen code; use tokens or add `// token-audit: allow (reason)`:\n${report.joinToString("\n")}")
            .that(report).isEmpty()
    }

    @Test
    fun the_legacy_list_only_names_files_that_exist() {
        for (name in legacyBudget().keys) assertWithMessage(name).that(File(projectRoot, name).isFile).isTrue()
    }

    @Test
    fun the_rules_catch_each_kind_of_raw_value() {
        val bad = listOf(
            "val a = Color(0xFF112233)",
            "Modifier.padding(12.dp)",
            "val f = 14.sp",
            "tween(300)",
            "spring<Float>(dampingRatio = 0.5f)",
            "private const val FADE_MS = 200L",
            "animationSpec = tween(MotionDurations.Small, easing = FastOutSlowInEasing)",
            "val g = Easing { it }",
            "Color.White",
        )
        for (line in bad) assertWithMessage(line).that(violations(listOf(line))).isNotEmpty()
        val fine = listOf(
            "Modifier.size(0.dp)",
            "Modifier.padding(Spacing.md)",
            "motion.spec(MotionToken.Bounce)",
            "Color.Black // token-audit: allow (QR modules)",
            "// 12.dp in a comment",
            "import androidx.compose.animation.core.LinearEasing",
        )
        for (line in fine) assertWithMessage(line).that(violations(listOf(line))).isEmpty()
        assertThat(violations(listOf("// token-audit: allow (reason)", "Color.White"))).isEmpty()
        assertThat(violations(listOf("Color.White // token-audit: allow (reason)", "Color.Black"))).hasSize(1)
    }

    private companion object {
        const val ALLOW = "token-audit: allow"
    }
}
