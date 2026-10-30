package com.darshan.splitsmart.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.darshan.splitsmart.data.llm.DataStoreModelCache
import com.darshan.splitsmart.data.llm.InMemoryModelCache
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The preferences DataStore behind the model cache and the profile name, on a real file. */
class SettingsStoresTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun store(name: String = "settings"): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { folder.root.resolve("$name.preferences_pb") })

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `model cache starts empty, persists the working model and overwrites it`() = runBlocking {
        val ds = store()
        val cache = DataStoreModelCache(ds)
        assertThat(cache.get()).isNull()
        cache.put("gemini-3.1-flash-lite")
        assertThat(cache.get()).isEqualTo("gemini-3.1-flash-lite")
        cache.put("gemini-flash-lite-latest")
        assertThat(DataStoreModelCache(ds).get()).isEqualTo("gemini-flash-lite-latest")
    }

    @Test
    fun `in-memory model cache round trips`() = runBlocking {
        val cache = InMemoryModelCache()
        assertThat(cache.get()).isNull()
        cache.put("m")
        assertThat(cache.get()).isEqualTo("m")
    }

    @Test
    fun `profile name is trimmed, persisted and cleared by blank`() = runBlocking {
        val repo = DataStoreProfileRepository(store())
        assertThat(repo.yourName.first()).isNull()
        repo.setYourName("  Asha ")
        assertThat(repo.yourName.first()).isEqualTo("Asha")
        repo.setYourName("   ")
        assertThat(repo.yourName.first()).isNull()
        repo.setYourName("Ravi")
        repo.setYourName(null)
        assertThat(repo.yourName.first()).isNull()
    }

    @Test
    fun `model cache and profile share one store without clobbering each other`() = runBlocking {
        val ds = store()
        DataStoreModelCache(ds).put("a")
        DataStoreProfileRepository(ds).setYourName("Asha")
        assertThat(DataStoreModelCache(ds).get()).isEqualTo("a")
        assertThat(DataStoreProfileRepository(ds).yourName.first()).isEqualTo("Asha")
    }

    @Test
    fun `in-memory profile normalises blanks`() = runBlocking {
        val repo = InMemoryProfileRepository("x")
        assertThat(repo.yourName.first()).isEqualTo("x")
        repo.setYourName(" ")
        assertThat(repo.yourName.first()).isNull()
    }
}
