package com.darshan.splitsmart.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates `app/src/main/generated/baselineProfiles/baseline-prof.txt` by driving the whole app the way a first evening with it
 * goes, on a fresh install (nothing seeds this build type):
 *
 * splash → intro (Skip) → groups (empty) → New group → group screen → two members → Scan receipt (camera permission page, back)
 * → a bill typed in → bill screen → Split this bill → Save split → back to the group → Settle up → Paid another way →
 * "Yes, mark as paid" (stamp, all square) → History → back home.
 *
 * Every step finds its target by the words on screen (labels listed in docs/LOAD_TEST_REPORT.md, "keep stable for the
 * benchmarks"), waits for it, and is skipped rather than failing when an earlier iteration already did it (the app's data
 * survives between iterations).
 *
 * Run on a device or emulator: `./gradlew :app:generateBaselineProfile`. Status: compiles; not run yet (needs a device).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = PACKAGE,
        includeInStartupProfile = true,
        maxIterations = 5,
    ) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.pkg(PACKAGE).depth(0)), WAIT_MS)

        // A fresh install opens on the three-page intro: skip it, like a returning user.
        find(By.text("Skip"), SHORT_MS)?.clickAndIdle()

        // Groups: the empty page's button opens the New group sheet. Later iterations find the group already there.
        if (find(By.descContains(GROUP), SHORT_MS) == null) {
            find(By.textContains("Create your first group"))?.clickAndIdle()
            type(0, GROUP)
            lowest(By.text("Create group"))?.clickAndIdle()
        }
        find(By.descContains(GROUP))?.clickAndIdle()
        device.wait(Until.hasObject(By.text(GROUP)), WAIT_MS)

        // Two people, once.
        if (find(By.descContains("Asha"), SHORT_MS) == null) {
            for (name in listOf("Asha", "Ravi")) {
                find(By.desc("Add member"))?.clickAndIdle()
                type(0, name)
                lowest(By.text("Add member"))?.clickAndIdle()
            }
        }

        // The scan entry point: the camera permission page (no camera needed), then back.
        find(By.text("Scan receipt"))?.clickAndIdle()
        if (find(By.text("Allow camera")) != null) device.pressBack()
        device.waitForIdle()

        // A bill typed in, paid by Asha.
        find(By.text("Enter manually"))?.clickAndIdle()
        type(0, BILL)
        type(1, "1200")
        find(By.text("Asha"))?.clickAndIdle()
        lowest(By.text("Add bill"))?.clickAndIdle()

        // Bill → split → save (back on the bill) → group.
        scrollTo(By.textContains(BILL))?.clickAndIdle()
        find(By.text("Split this bill"))?.clickAndIdle()
        find(By.text("Save split"))?.clickAndIdle()
        device.wait(Until.hasObject(By.text("Edit split")), WAIT_MS)
        device.pressBack()
        device.waitForIdle()

        // Settle up → paid another way → yes → history.
        find(By.text("Settle up"))?.clickAndIdle()
        find(By.text("Paid another way"))?.clickAndIdle()
        find(By.text("Yes, mark as paid"))?.clickAndIdle()
        find(By.text("History"))?.clickAndIdle()
        device.wait(Until.hasObject(By.textContains("so far")), WAIT_MS)
        device.findObjects(By.scrollable(true)).maxByOrNull { it.visibleBounds.height() }?.fling(Direction.DOWN)
        device.waitForIdle()

        repeat(BACKS) {
            device.pressBack()
            device.waitForIdle()
        }
    }

    private fun MacrobenchmarkScope.find(selector: BySelector, timeoutMs: Long = WAIT_MS): UiObject2? =
        device.wait(Until.findObject(selector), timeoutMs)

    /** The match lowest on screen: a sheet's button shares its words with the sheet's title above it. */
    private fun MacrobenchmarkScope.lowest(selector: BySelector): UiObject2? {
        device.wait(Until.hasObject(selector), WAIT_MS)
        return device.findObjects(selector).maxByOrNull { it.visibleBounds.top }
    }

    /** Types into the [index]th text field on screen (fields are Compose text fields, reported as EditText). */
    private fun MacrobenchmarkScope.type(index: Int, text: String) {
        device.wait(Until.hasObject(By.clazz(EDIT_TEXT)), WAIT_MS)
        device.findObjects(By.clazz(EDIT_TEXT)).sortedBy { it.visibleBounds.top }.getOrNull(index)?.text = text
    }

    /** Scrolls the screen's main list until [selector] shows (the group's bills sit below the hero, members and tiles). */
    private fun MacrobenchmarkScope.scrollTo(selector: BySelector): UiObject2? {
        find(selector, SHORT_MS)?.let { return it }
        val list = device.findObjects(By.scrollable(true)).maxByOrNull { it.visibleBounds.height() } ?: return null
        list.setGestureMargin(device.displayWidth / 5)
        return list.scrollUntil(Direction.DOWN, Until.findObject(selector))
    }

    private fun UiObject2.clickAndIdle() {
        click()
        device().waitForIdle()
    }

    private companion object {
        const val PACKAGE = "com.darshan.splitsmart"
        const val GROUP = "Baseline trip"
        const val BILL = "Baseline dinner"
        const val EDIT_TEXT = "android.widget.EditText"
        const val WAIT_MS = 5_000L
        const val SHORT_MS = 1_500L
        const val BACKS = 4
    }
}

private fun device() = androidx.test.uiautomator.UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
