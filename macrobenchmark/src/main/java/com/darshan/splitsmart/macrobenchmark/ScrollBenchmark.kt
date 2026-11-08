package com.darshan.splitsmart.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing while flinging the groups home (200 groups) and one group's bills (200 bills). Each iteration starts a new
 * process, so the first scroll after launch is what gets measured. Selectors that must survive UI changes are listed in
 * docs/LOAD_TEST_REPORT.md.
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Before
    fun seed() = seedBenchmarkData()

    @Test
    fun homeScrollNoCompilation() = home(CompilationMode.None())

    @Test
    fun homeScrollPartial() = home(PARTIAL)

    @Test
    fun billsScrollNoCompilation() = bills(CompilationMode.None())

    @Test
    fun billsScrollPartial() = bills(PARTIAL)

    private fun home(compilationMode: CompilationMode) = scroll(compilationMode, setup = { })

    private fun bills(compilationMode: CompilationMode) = scroll(
        compilationMode,
        setup = {
            // Seed data is the stable selector here: the card's layout and tags may change, the group's name does not.
            val card = device.wait(Until.findObject(By.descContains(MAIN_GROUP)), WAIT_TIMEOUT_MS)
                ?: device.findObject(By.textContains(MAIN_GROUP))
                ?: error("No card for $MAIN_GROUP")
            card.click()
            check(device.wait(Until.hasObject(By.text(MAIN_GROUP)), WAIT_TIMEOUT_MS)) { "Group screen did not open" }
            device.waitForIdle()
        },
    )

    private fun scroll(compilationMode: CompilationMode, setup: MacrobenchmarkScope.() -> Unit) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        iterations = 5,
        setupBlock = {
            killProcess()
            pressHome()
            startActivityAndWait()
            setup()
        },
    ) {
        flingDownAndUp { tallestScrollable() }
    }

    private companion object {
        const val MAIN_GROUP = "Benchmark trip"
    }
}
