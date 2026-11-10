package com.darshan.splitsmart.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darshan.splitsmart.domain.repository.OnboardingRepository
import com.darshan.splitsmart.ui.splash.SplashPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where a launch begins. [Loading] keeps the splash up; [Replaying] is the intro shown again over the app from the Me tab. */
enum class AppStart { Loading, Onboarding, Ready, Replaying }

sealed interface AppStartEvent {
    /** The stored flag, as first read. */
    data class Stored(val completed: Boolean) : AppStartEvent

    /** The splash cap passed with no answer. */
    data object TimedOut : AppStartEvent

    /** The intro was finished or skipped. */
    data object Finished : AppStartEvent

    /** "Show the intro again" from the Me tab. */
    data object Replay : AppStartEvent
}

/**
 * Pure rules for the first screen. Only the first answer decides: after that the flag is ours to write, and a late answer must
 * never pull the intro over a screen someone is already using.
 */
object AppStartLogic {
    fun reduce(state: AppStart, event: AppStartEvent): AppStart = when (event) {
        is AppStartEvent.Stored -> if (state == AppStart.Loading) (if (event.completed) AppStart.Ready else AppStart.Onboarding) else state
        AppStartEvent.TimedOut -> if (state == AppStart.Loading) AppStart.Ready else state
        AppStartEvent.Finished -> AppStart.Ready
        AppStartEvent.Replay -> if (state == AppStart.Ready) AppStart.Replaying else state
    }
}

/**
 * Decides between the intro and the app, once per launch, and remembers when the intro is done. Activity-scoped: the splash
 * reads [start] to know when the first screen is decided.
 */
@HiltViewModel
class AppStartViewModel @Inject constructor(private val onboarding: OnboardingRepository) : ViewModel() {
    private val _start = MutableStateFlow(AppStart.Loading)
    val start: StateFlow<AppStart> = _start.asStateFlow()

    init {
        viewModelScope.launch { on(AppStartEvent.Stored(onboarding.completed.first())) }
        viewModelScope.launch {
            delay(SplashPolicy.MAX_HOLD_MS)
            on(AppStartEvent.TimedOut)
        }
    }

    /** Finish or skip: into the app now, and remembered. Repeated taps write once. */
    fun finish() {
        if (_start.value == AppStart.Ready) return
        on(AppStartEvent.Finished)
        viewModelScope.launch { onboarding.setCompleted(true) }
    }

    /** Shows the intro again. The stored flag stays done, so leaving halfway does not bring it back next launch. */
    fun replay() = on(AppStartEvent.Replay)

    private fun on(event: AppStartEvent) = _start.update { AppStartLogic.reduce(it, event) }
}
