package com.darshan.splitsmart.data.analytics

import android.os.Bundle
import com.darshan.splitsmart.domain.analytics.AnalyticsEvent
import com.darshan.splitsmart.domain.analytics.AnalyticsPolicy
import com.darshan.splitsmart.domain.analytics.AnalyticsTracker
import com.darshan.splitsmart.domain.analytics.CrashReporter
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/** Firebase Analytics behind [AnalyticsTracker]; every event passes [AnalyticsPolicy] before it becomes a Bundle. */
class FirebaseAnalyticsTracker(private val analytics: FirebaseAnalytics) : AnalyticsTracker {
    override fun track(event: AnalyticsEvent) {
        val params = runCatching { AnalyticsPolicy.sanitize(event) }.getOrElse { return }
        val bundle = Bundle()
        params.forEach { (key, value) ->
            when (value) {
                is Int -> bundle.putLong(key, value.toLong())
                is Long -> bundle.putLong(key, value)
                else -> bundle.putString(key, value.toString())
            }
        }
        analytics.logEvent(event.name, bundle)
    }
}

/** Crashlytics behind [CrashReporter]: custom keys are policy-checked one by one, log lines are scrubbed. */
class FirebaseCrashReporter(private val crashlytics: FirebaseCrashlytics) : CrashReporter {
    override fun log(message: String) = crashlytics.log(scrub(message))

    override fun setKey(key: String, value: Int) {
        if (runCatching { AnalyticsPolicy.checkKey(key) }.isSuccess) crashlytics.setCustomKey(key, value)
    }

    override fun setKey(key: String, value: String) {
        if (runCatching { AnalyticsPolicy.checkKey(key); AnalyticsPolicy.checkValue(key, value) }.isSuccess) crashlytics.setCustomKey(key, value)
    }

    override fun recordNonFatal(throwable: Throwable, keys: Map<String, Any>) {
        keys.forEach { (key, value) ->
            val checked = runCatching { AnalyticsPolicy.checkKey(key); AnalyticsPolicy.checkValue(key, value) }.getOrNull() ?: return@forEach
            when (checked) {
                is Int -> crashlytics.setCustomKey(key, checked)
                is Long -> crashlytics.setCustomKey(key, checked)
                else -> crashlytics.setCustomKey(key, checked.toString())
            }
        }
        crashlytics.recordException(throwable)
    }

    private companion object {
        private val ADDRESS = Regex("\\S+@\\S+")
        private val DIGITS = Regex("\\d{7,}")
        fun scrub(message: String): String = DIGITS.replace(ADDRESS.replace(message, "[redacted]"), "[digits]").take(1_000)
    }
}
