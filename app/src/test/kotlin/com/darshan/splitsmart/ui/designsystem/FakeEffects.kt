package com.darshan.splitsmart.ui.designsystem

import com.darshan.splitsmart.ui.designsystem.effects.HapticEvent
import com.darshan.splitsmart.ui.designsystem.effects.Haptics
import com.darshan.splitsmart.ui.designsystem.effects.UiSound
import com.darshan.splitsmart.ui.designsystem.effects.UiSoundEvent

/** Records every haptic. Pass it to `SplitSmartTheme(haptics = ...)` and assert on [events]. */
class FakeHaptics : Haptics {
    val events = mutableListOf<HapticEvent>()
    override fun perform(event: HapticEvent) {
        events += event
    }
}

/** Records every sound request. */
class FakeUiSound : UiSound {
    val events = mutableListOf<UiSoundEvent>()
    override fun play(event: UiSoundEvent) {
        events += event
    }
}
