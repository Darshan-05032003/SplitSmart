package com.darshan.splitsmart.ui.designsystem

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.darshan.splitsmart.ui.designsystem.effects.DataStoreUiPreferences
import com.darshan.splitsmart.ui.designsystem.effects.InMemoryUiPreferences
import com.darshan.splitsmart.ui.designsystem.effects.ThemeMode
import com.darshan.splitsmart.ui.designsystem.effects.UiSettings
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UiPreferencesTest {
    @get:Rule val folder = TemporaryFolder()

    private fun <T> withStore(file: File = File(folder.root, "ui.preferences_pb"), block: suspend (DataStoreUiPreferences, androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>) -> T): T {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            return runBlocking { withTimeout(10_000) { block(DataStoreUiPreferences(store, scope), store) } }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `defaults are haptics on, sound off, full motion, system theme`() = withStore { prefs, _ ->
        prefs.loaded.first { it }
        assertThat(prefs.settings.value).isEqualTo(UiSettings(hapticsEnabled = true, soundEnabled = false, reduceMotion = false, theme = ThemeMode.System))
    }

    @Test
    fun `every setting persists`() = withStore { prefs, _ ->
        prefs.setHapticsEnabled(false)
        prefs.setSoundEnabled(true)
        prefs.setReduceMotion(true)
        prefs.setTheme(ThemeMode.Dark)
        val stored = prefs.settings.first { it.theme == ThemeMode.Dark && it.reduceMotion && it.soundEnabled && !it.hapticsEnabled }
        assertThat(stored).isEqualTo(UiSettings(hapticsEnabled = false, soundEnabled = true, reduceMotion = true, theme = ThemeMode.Dark))
    }

    @Test
    fun `an unknown stored theme falls back to system`() = withStore { prefs, store ->
        store.edit { it[stringPreferencesKey("ui_theme")] = "Sepia" }
        prefs.setReduceMotion(true)
        val settings = prefs.settings.first { it.reduceMotion }
        assertThat(settings.theme).isEqualTo(ThemeMode.System)
    }

    @Test
    fun `the in-memory version behaves the same`() = runBlocking {
        val prefs = InMemoryUiPreferences()
        prefs.setReduceMotion(true)
        prefs.setTheme(ThemeMode.Light)
        assertThat(prefs.settings.value.reduceMotion).isTrue()
        assertThat(prefs.settings.value.theme).isEqualTo(ThemeMode.Light)
        assertThat(prefs.loaded.value).isTrue()
    }
}
