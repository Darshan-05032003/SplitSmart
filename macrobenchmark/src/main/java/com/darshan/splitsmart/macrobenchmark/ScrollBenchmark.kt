package com.darshan.splitsmart.macrobenchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing while flinging the group detail list (members, bills, balances).
 * Run on a device: `./gradlew :macrobenchmark:connectedBenchmarkAndroidTest`
 * Status in this build: not run (no device); compiles.
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun scrollGroupDetailNoCompilation() = scroll(CompilationMode.None())

    @Test
    fun scrollGroupDetailBaselineProfile() = scroll(CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require))

    private fun scroll(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.findObject(By.clazz("android.widget.ScrollView"))?.let { } // groups list is a LazyColumn; open the first group card
            device.wait(Until.hasObject(By.pkg(PACKAGE).depth(0)), 5_000)
            device.findObjects(By.clickable(true)).firstOrNull { it.contentDescription?.contains("member") == true }?.click()
            device.wait(Until.hasObject(By.text("Members")), 3_000)
        },
    ) {
        val list = device.findObject(By.scrollable(true)) ?: return@measureRepeated
        list.setGestureMargin(device.displayWidth / 5)
        repeat(3) { list.fling(Direction.DOWN) }
        repeat(3) { list.fling(Direction.UP) }
    }

    private companion object {
        const val PACKAGE = "com.darshan.splitsmart"
    }
}
