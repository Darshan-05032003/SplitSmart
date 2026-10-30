package com.darshan.splitsmart.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.darshan.splitsmart.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Stores the user's display name in the app preferences DataStore (local only, never sent anywhere). */
class DataStoreProfileRepository(private val dataStore: DataStore<Preferences>) : ProfileRepository {
    override val yourName: Flow<String?> = dataStore.data
        .map { it[KEY]?.takeIf(String::isNotBlank) }
        .catch { emit(null) }
        .distinctUntilChanged()

    override suspend fun setYourName(name: String?) {
        dataStore.edit { prefs -> if (name.isNullOrBlank()) prefs.remove(KEY) else prefs[KEY] = name.trim() }
    }

    private companion object {
        val KEY = stringPreferencesKey("your_name")
    }
}

class InMemoryProfileRepository(initial: String? = null) : ProfileRepository {
    private val state = MutableStateFlow(initial)
    override val yourName: Flow<String?> = state
    override suspend fun setYourName(name: String?) { state.value = name?.trim()?.takeIf(String::isNotEmpty) }
}
