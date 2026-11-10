package com.darshan.splitsmart.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing over the hero flow on seeded data (the seed marks the intro done, so the app opens on the groups list):
 *
 * groups → "Benchmark trip" → Scan receipt → shutter → review ("Save bill") → bill → Split → "Save split" → group → Settle up →
 * Paid another way → "Yes, mark as paid" → History (fling) → back.
 *
 * The camera is whatever the device shows. If the photo has no readable text (an emulator's virtual scene often doesn't), the
 * scan step ends on its error page and the journey continues from a seeded bill instead, so the rest is still measured. Seeded
 * members have no UPI ID, so settling goes through "Paid another way" and never leaves the app.
 */
@RunWith(AndroidJUnit4::class)
class JourneyBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Before
    fun seed() {
        seedBenchmarkData()
        // The camera page would otherwise stop at the system permission dialog.
        device().executeShellCommand("pm grant $PACKAGE android.permission.CAMERA")
    }

    @Test
    fun scanSplitSettleNoCompilation() = journey(CompilationMode.None())

    @Test
    fun scanSplitSettlePartial() = journey(PARTIAL)

    private fun journey(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        iterations = 3,
        setupBlock = {
            killProcess()
            pressHome()
            startActivityAndWait()
        },
    ) {
        open(By.descContains(MAIN_GROUP))
        check(device.wait(Until.hasObject(By.text(MAIN_GROUP)), WAIT_TIMEOUT_MS)) { "Group screen did not open" }

        // Scan: shutter, then the review if the photo had text.
        open(By.text("Scan receipt"))
        find(By.desc("Take photo"))?.let { shutter ->
            shutter.click()
            val review = device.wait(Until.findObject(By.text("Save bill")), SCAN_TIMEOUT_MS)
            if (review != null) {
                review.click()
                device.wait(Until.hasObject(By.textContains("split")), WAIT_TIMEOUT_MS)
            }
        }
        // Back to the group (its hero's Settle up), from the saved bill, the scan error page or the camera: a few backs at most.
        repeat(MAX_BACKS) {
            if (device.hasObject(By.text("Settle up"))) return@repeat
            device.pressBack()
            device.wait(Until.hasObject(By.text("Settle up")), SHORT_WAIT_MS)
        }

        // A seeded bill (bills named "Dinner …" are split): split it again and save.
        scrollTo(By.textContains("Dinner"))?.click()
        open(By.text("Edit split"))
        open(By.text("Save split"))
        device.wait(Until.hasObject(By.text("Edit split")), WAIT_TIMEOUT_MS)
        device.pressBack()
        device.waitForIdle()

        // Settle one payment and look at the history.
        open(By.text("Settle up"))
        open(By.text("Paid another way"))
        open(By.text("Yes, mark as paid"))
        open(By.text("History"))
        flingDownAndUp { tallestScrollable() }
        device.pressBack()
        device.pressBack()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.find(selector: BySelector): UiObject2? = device.wait(Until.findObject(selector), WAIT_TIMEOUT_MS)

    private fun MacrobenchmarkScope.open(selector: BySelector) {
        val target = find(selector) ?: error("Not on screen: $selector")
        target.click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.scrollTo(selector: BySelector): UiObject2? {
        device.findObject(selector)?.let { return it }
        val list = device.tallestScrollable()
        list.setGestureMargin(device.displayWidth / 5)
        return list.scrollUntil(Direction.DOWN, Until.findObject(selector))
    }

    private companion object {
        const val MAIN_GROUP = "Benchmark trip"
        const val SCAN_TIMEOUT_MS = 20_000L
        const val SHORT_WAIT_MS = 2_000L
        const val MAX_BACKS = 3
    }
}

private fun device() = androidx.test.uiautomator.UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
