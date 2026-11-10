package com.darshan.splitsmart.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.darshan.splitsmart.domain.repository.OnboardingRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Stored as `onboarding_done` in the app's settings DataStore. Only a real `true` means done: a value of another type (someone
 * editing the file on a rooted phone) reads as not done, which only shows the intro again; finishing it rewrites a real boolean.
 * The raw map is read so a wrong type can never reach a cast.
 */
class DataStoreOnboardingRepository(private val dataStore: DataStore<Preferences>) : OnboardingRepository {
    override val completed: Flow<Boolean> = dataStore.data
        .map { prefs -> prefs.asMap()[KEY] == true }
        .catch { failure -> if (failure is IOException) emit(true) else throw failure }
        .distinctUntilChanged()

    override suspend fun setCompleted(completed: Boolean) {
        try {
            dataStore.edit { it[KEY] = completed }
        } catch (_: IOException) {
            // The intro shows once more next launch; nothing else depends on this flag.
        }
    }

    companion object {
        const val KEY_NAME = "onboarding_done"
        private val KEY = booleanPreferencesKey(KEY_NAME)
    }
}

/** For tests and previews. */
class InMemoryOnboardingRepository(completed: Boolean = false) : OnboardingRepository {
    private val state = MutableStateFlow(completed)
    override val completed: Flow<Boolean> = state.asStateFlow()
    override suspend fun setCompleted(completed: Boolean) {
        state.value = completed
    }
}
