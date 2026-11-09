package com.darshan.splitsmart.ui.designsystem.effects

import android.annotation.SuppressLint
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * SplitSmart's tactile vocabulary, one event per kind of moment:
 * - [Tick]: a detent. The bottom bar blob landing on a tab, an avatar stack fanning out, a chip toggling.
 * - [Light]: an ordinary accepted tap, an item gulped by an avatar, Undo.
 * - [Confirm]: a real achievement: a debt settled, a split saved.
 * - [Reject]: something refused: an invalid amount, a field with an error.
 * - [Heavy]: a weighty, deliberate moment: deleting a group, the shutter.
 */
enum class HapticEvent { Tick, Light, Confirm, Reject, Heavy }

interface Haptics {
    fun perform(event: HapticEvent)
}

fun Haptics.tick() = perform(HapticEvent.Tick)
fun Haptics.light() = perform(HapticEvent.Light)
fun Haptics.confirm() = perform(HapticEvent.Confirm)
fun Haptics.reject() = perform(HapticEvent.Reject)
fun Haptics.heavy() = perform(HapticEvent.Heavy)

/** Pure mapping, tested per API level. CONFIRM and REJECT exist from API 30; older phones get the closest older constant. */
@SuppressLint("InlinedApi")
fun feedbackConstantFor(event: HapticEvent, sdkInt: Int): Int = when (event) {
    HapticEvent.Tick -> HapticFeedbackConstants.CLOCK_TICK
    HapticEvent.Light -> HapticFeedbackConstants.KEYBOARD_TAP
    HapticEvent.Confirm -> if (sdkInt >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
    HapticEvent.Reject -> if (sdkInt >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
    HapticEvent.Heavy -> HapticFeedbackConstants.LONG_PRESS
}

/**
 * Plays through the view's haptic channel. It needs no `VIBRATE` permission and follows the phone's own touch-feedback setting;
 * on API 30+ the system backs CONFIRM and REJECT with vibration primitives where the hardware has them.
 */
class AndroidHaptics(private val view: View, private val sdkInt: Int = Build.VERSION.SDK_INT) : Haptics {
    override fun perform(event: HapticEvent) {
        view.performHapticFeedback(feedbackConstantFor(event, sdkInt))
    }
}

/** Applies the in-app "Haptics" setting. [enabled] is read on every event, so a change applies at once. */
class GuardedHaptics(private val delegate: Haptics, private val enabled: () -> Boolean) : Haptics {
    override fun perform(event: HapticEvent) {
        if (enabled()) delegate.perform(event)
    }
}

object NoOpHaptics : Haptics {
    override fun perform(event: HapticEvent) = Unit
}

val LocalHaptics = staticCompositionLocalOf<Haptics> { NoOpHaptics }
