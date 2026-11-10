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
 * cold start → (skip the intro) → groups list → create a group from the empty page → open it → open the scan screen (permission
 * rationale) → back.
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

        // A fresh install opens on the three-page intro (nothing seeds this run): skip it, like a returning user.
        device.findObject(By.text("Skip"))?.let { skip ->
            skip.click()
            device.waitForIdle()
        }

        // A fresh install has no groups: the empty page's button opens the New group sheet. Then open the group (its card reads
        // its name through its description).
        device.findObject(By.textContains("Create your first group"))?.let { button ->
            button.click()
            device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 3_000)
            device.findObject(By.clazz("android.widget.EditText"))?.setText("Baseline trip")
            device.findObject(By.textContains("Create group"))?.click()
            device.waitForIdle()
        }
        device.wait(Until.hasObject(By.descContains("Baseline trip")), 3_000)
        device.findObject(By.descContains("Baseline trip"))?.click()
        device.wait(Until.hasObject(By.textContains("Members")), 3_000)

        // Open the scan entry point from the group's add-a-bill tiles (permission rationale, no camera needed).
        device.findObject(By.textContains("Scan receipt"))?.click()
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
