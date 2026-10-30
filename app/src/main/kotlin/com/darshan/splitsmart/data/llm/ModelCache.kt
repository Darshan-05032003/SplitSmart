package com.darshan.splitsmart.data.llm

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * Remembers which Gemini model id last answered, so after one "model not found" (404) the app does not pay for the
 * same failed round trip on every scan.
 */
interface ModelCache {
    suspend fun get(): String?
    suspend fun put(model: String)
}

class InMemoryModelCache(@Volatile private var model: String? = null) : ModelCache {
    override suspend fun get(): String? = model
    override suspend fun put(model: String) { this.model = model }
}

/** Persists the working model id in the app's preferences DataStore. Read/write failures never break extraction. */
class DataStoreModelCache(private val dataStore: DataStore<Preferences>) : ModelCache {
    override suspend fun get(): String? = runCatching { dataStore.data.first()[KEY] }.getOrNull()

    override suspend fun put(model: String) {
        runCatching { dataStore.edit { it[KEY] = model } }
    }

    private companion object {
        val KEY = stringPreferencesKey("llm_model")
    }
}
