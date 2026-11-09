package com.darshan.splitsmart.ui.designsystem.effects

import androidx.compose.runtime.staticCompositionLocalOf

enum class UiSoundEvent { Settled, Error, Tick }

/** Optional UI sounds, off by default. SplitSmart ships no audio yet, so the app's implementation is silent. */
interface UiSound {
    fun play(event: UiSoundEvent)
}

object NoOpUiSound : UiSound {
    override fun play(event: UiSoundEvent) = Unit
}

/** Applies the in-app "Sound" setting. */
class GuardedUiSound(private val delegate: UiSound, private val enabled: () -> Boolean) : UiSound {
    override fun play(event: UiSoundEvent) {
        if (enabled()) delegate.play(event)
    }
}

val LocalUiSound = staticCompositionLocalOf<UiSound> { NoOpUiSound }
