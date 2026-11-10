package com.darshan.splitsmart.ui.navigation

import android.content.Intent

/**
 * MainActivity is exported as the launcher entry, so any app can start it with any intent, and Navigation reads that intent for
 * deep links, including its private "explicit deep link" extras that name destinations and arguments. SplitSmart has no deep
 * links, so the launch intent is cut down to a plain launcher intent (same component, no data, no extras) before Navigation sees
 * it. A crafted intent can then neither open a screen out of order nor crash the app with missing arguments.
 */
object LaunchIntentPolicy {
    fun sanitize(intent: Intent?): Intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        component = intent?.component
    }
}
