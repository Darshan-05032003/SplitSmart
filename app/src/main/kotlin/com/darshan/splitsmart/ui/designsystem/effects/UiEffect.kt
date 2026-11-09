package com.darshan.splitsmart.ui.designsystem.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * One-off UI effects, kept out of `StateFlow<UiState>` because replaying them after a rotation would be wrong.
 *
 * Convention: a ViewModel owns one [UiEffectChannel], calls `effects.send(UiEffect.Haptic(HapticEvent.Confirm))` from its logic,
 * and exposes `effects.effects`. The screen collects it with [CollectUiEffects]. Tests assert the right effect per event by
 * collecting the same flow, or at screen level through `FakeHaptics`. Feature effects (navigate, open the UPI app) implement this
 * interface and arrive through `onOther`.
 */
interface UiEffect {
    data class Haptic(val event: HapticEvent) : UiEffect
    data class Sound(val event: UiSoundEvent) : UiEffect

    /** A snackbar; with [undoable] the screen shows an Undo action and reports the choice back to its ViewModel. */
    data class Message(val text: String, val emoji: String? = null, val undoable: Boolean = false) : UiEffect
}

/**
 * A bounded, single-consumer channel. Effects sent while nobody collects (during a rotation) wait for the next collector. A flood
 * drops the oldest, so a broken caller cannot grow memory.
 */
class UiEffectChannel(capacity: Int = DEFAULT_CAPACITY) {
    private val channel = Channel<UiEffect>(capacity, BufferOverflow.DROP_OLDEST)
    val effects: Flow<UiEffect> = channel.receiveAsFlow()

    fun send(effect: UiEffect) {
        channel.trySend(effect)
    }

    private companion object {
        const val DEFAULT_CAPACITY = 16
    }
}

fun UiEffect.dispatchTo(haptics: Haptics, sound: UiSound, onMessage: (UiEffect.Message) -> Unit, onOther: (UiEffect) -> Unit) {
    when (this) {
        is UiEffect.Haptic -> haptics.perform(event)
        is UiEffect.Sound -> sound.play(event)
        is UiEffect.Message -> onMessage(this)
        else -> onOther(this)
    }
}

/** Screen side of the convention: haptics and sounds go to the theme's channels, the rest to the callbacks. */
@Composable
fun CollectUiEffects(
    effects: Flow<UiEffect>,
    onMessage: (UiEffect.Message) -> Unit = {},
    onOther: (UiEffect) -> Unit = {},
) {
    val haptics = LocalHaptics.current
    val sound = LocalUiSound.current
    val message = rememberUpdatedState(onMessage)
    val other = rememberUpdatedState(onOther)
    LaunchedEffect(effects) {
        effects.collect { effect -> effect.dispatchTo(haptics, sound, onMessage = { message.value(it) }, onOther = { other.value(it) }) }
    }
}
