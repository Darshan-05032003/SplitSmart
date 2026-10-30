package com.darshan.splitsmart.testutil

import com.darshan.splitsmart.domain.analytics.AnalyticsEvent
import com.darshan.splitsmart.domain.analytics.AnalyticsPolicy
import com.darshan.splitsmart.domain.analytics.AnalyticsTracker
import com.darshan.splitsmart.domain.analytics.CrashReporter
import java.util.Collections

/** Records events after running them through the same [AnalyticsPolicy] the Firebase tracker uses. */
class RecordingAnalyticsTracker : AnalyticsTracker {
    val events: MutableList<Pair<String, Map<String, Any>>> = Collections.synchronizedList(mutableListOf())
    override fun track(event: AnalyticsEvent) {
        events += event.name to AnalyticsPolicy.sanitize(event)
    }
    fun names() = events.map { it.first }
    fun last(name: String) = events.last { it.first == name }.second
}

class RecordingCrashReporter : CrashReporter {
    val logs = Collections.synchronizedList(mutableListOf<String>())
    val keys = Collections.synchronizedMap(linkedMapOf<String, Any>())
    val nonFatals = Collections.synchronizedList(mutableListOf<Pair<Throwable, Map<String, Any>>>())
    override fun log(message: String) { logs += message }
    override fun setKey(key: String, value: Int) { keys[key] = value }
    override fun setKey(key: String, value: String) { keys[key] = value }
    override fun recordNonFatal(throwable: Throwable, keys: Map<String, Any>) { nonFatals += throwable to keys; this.keys.putAll(keys) }
}
