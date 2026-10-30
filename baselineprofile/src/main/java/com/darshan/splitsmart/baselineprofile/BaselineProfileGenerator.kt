package com.darshan.splitsmart.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates `app/src/main/generated/baselineProfiles/baseline-prof.txt` by driving the critical user journey:
 * cold start → groups list → create a group → open it → open the scan screen (permission rationale) → back.
 *
 * Run on a device / emulator: `./gradlew :app:generateBaselineProfile`
 * Status in this build: not run (no device); compiles and lints.
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
        device.wait(Until.hasObject(By.pkg(PACKAGE).depth(0)), 5_000)

        // Create a group if the list is empty, then open the first group.
        device.findObject(By.desc("Create group"))?.let { fab ->
            fab.click()
            device.wait(Until.hasObject(By.text("Group name")), 3_000)
            device.findObject(By.text("Group name"))?.parent?.setText("Baseline trip")
            device.findObject(By.text("Create"))?.click()
            device.waitForIdle()
        }
        device.findObject(By.textContains("Baseline trip"))?.click()
        device.wait(Until.hasObject(By.text("Members")), 3_000)

        // Scroll the group screen and open the scan entry point (permission rationale, no camera needed).
        device.findObject(By.text("Scan receipt"))?.click()
        device.wait(Until.hasObject(By.text("Allow camera")), 3_000)
        device.pressBack()
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
    }

    private companion object {
        const val PACKAGE = "com.darshan.splitsmart"
    }
}
