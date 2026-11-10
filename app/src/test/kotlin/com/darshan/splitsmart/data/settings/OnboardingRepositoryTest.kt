package com.darshan.splitsmart.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Onboarding completion on a real preferences file, including a tampered or unreadable store. */
class OnboardingRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun store(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { folder.root.resolve("settings.preferences_pb") })

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `a fresh install has not seen the intro, finishing it persists`() = runBlocking {
        val ds = store()
        val repo = DataStoreOnboardingRepository(ds)
        assertThat(repo.completed.first()).isFalse()
        repo.setCompleted(true)
        assertThat(DataStoreOnboardingRepository(ds).completed.first()).isTrue()
        repo.setCompleted(false)
        assertThat(repo.completed.first()).isFalse()
    }

    @Test
    fun `a value of the wrong type, written by someone editing the file, reads as not done and never crashes`() = runBlocking {
        val ds = store()
        ds.edit { it[stringPreferencesKey(DataStoreOnboardingRepository.KEY_NAME)] = "yes" }
        val repo = DataStoreOnboardingRepository(ds)
        assertThat(repo.completed.first()).isFalse()
        // Finishing again repairs the entry with a real boolean.
        repo.setCompleted(true)
        assertThat(repo.completed.first()).isTrue()
    }

    @Test
    fun `an int where the flag should be is not mistaken for done`() = runBlocking {
        val ds = store()
        ds.edit { it[intPreferencesKey(DataStoreOnboardingRepository.KEY_NAME)] = 1 }
        assertThat(DataStoreOnboardingRepository(ds).completed.first()).isFalse()
    }

    @Test
    fun `an unreadable store reads as done, so the intro never blocks the app, and a failed write is dropped`() = runBlocking {
        val broken = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("disk") }
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw IOException("disk")
        }
        val repo = DataStoreOnboardingRepository(broken)
        assertThat(repo.completed.first()).isTrue()
        repo.setCompleted(true)
    }

    @Test
    fun `the in-memory version behaves the same`() = runBlocking {
        val repo = InMemoryOnboardingRepository()
        assertThat(repo.completed.first()).isFalse()
        repo.setCompleted(true)
        assertThat(repo.completed.first()).isTrue()
    }
}
