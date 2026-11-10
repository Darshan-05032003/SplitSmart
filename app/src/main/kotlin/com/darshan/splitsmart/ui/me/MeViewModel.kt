package com.darshan.splitsmart.ui.me

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darshan.splitsmart.di.AiReceiptReading
import com.darshan.splitsmart.domain.model.NameError
import com.darshan.splitsmart.domain.model.NameValidation
import com.darshan.splitsmart.domain.model.NameValidator
import com.darshan.splitsmart.domain.repository.ProfileRepository
import com.darshan.splitsmart.ui.designsystem.effects.ThemeMode
import com.darshan.splitsmart.ui.designsystem.effects.UiPreferences
import com.darshan.splitsmart.ui.designsystem.effects.UiSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The name being edited in the sheet. */
@Immutable
data class NameEdit(val name: String = "", val error: NameError? = null)

@Immutable
data class MeUiState(
    val name: String? = null,
    val settings: UiSettings = UiSettings(),
    /** True when this build carries a Gemini API key, so scanned receipts are read by AI; otherwise the built-in parser. */
    val aiReading: Boolean = false,
    val nameDraft: NameEdit? = null,
)

/** The Me tab: your name, how the app feels (haptics, reduced motion, theme) and plain facts about what the app does. */
@HiltViewModel
class MeViewModel @Inject constructor(
    private val profile: ProfileRepository,
    private val prefs: UiPreferences,
    @AiReceiptReading private val aiReading: Boolean,
) : ViewModel() {
    private val draft = MutableStateFlow<NameEdit?>(null)

    val uiState: StateFlow<MeUiState> = combine(profile.yourName, prefs.settings, draft) { name, settings, edit ->
        MeUiState(name = name, settings = settings, aiReading = aiReading, nameDraft = edit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MeUiState(aiReading = aiReading))

    fun setHaptics(on: Boolean) = write { prefs.setHapticsEnabled(on) }
    fun setReduceMotion(on: Boolean) = write { prefs.setReduceMotion(on) }
    fun setTheme(theme: ThemeMode) = write { prefs.setTheme(theme) }

    fun openNameEditor() {
        if (draft.value != null) return
        viewModelScope.launch { draft.update { it ?: NameEdit(profile.yourName.first().orEmpty()) } }
    }

    fun onNameChange(name: String) = draft.update { it?.copy(name = name.take(NAME_INPUT_LIMIT), error = null) }

    fun dismissNameEditor() = draft.update { null }

    fun saveName() {
        val current = draft.value ?: return
        when (val result = NameValidator.validate(current.name)) {
            is NameValidation.Invalid -> draft.update { it?.copy(error = result.error) }
            is NameValidation.Valid -> {
                draft.value = null
                write { profile.setYourName(result.name) }
            }
        }
    }

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() } }
    }

    companion object {
        /** Typing stops here: comfortably above the 60-character name limit so the validator, not the field, explains it. */
        const val NAME_INPUT_LIMIT = 80
        private const val STOP_TIMEOUT_MS = 5_000L // token-audit: allow (flow sharing timeout, not motion)
    }
}
