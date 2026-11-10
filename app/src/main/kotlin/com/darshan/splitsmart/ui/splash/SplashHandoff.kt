package com.darshan.splitsmart.ui.splash

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.os.SystemClock
import android.view.View
import androidx.compose.animation.core.Easing
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
import com.darshan.splitsmart.ui.designsystem.motion.MotionDurations
import com.darshan.splitsmart.ui.designsystem.motion.MotionEasing
import com.darshan.splitsmart.ui.designsystem.motion.MotionLevel
import com.darshan.splitsmart.ui.designsystem.motion.resolveMotionLevel

/** How the splash leaves. */
enum class SplashExit {
    /** Reduced motion: gone at once, the first screen is simply there. */
    Remove,

    /** The mark squashes like a pressed button, then pops towards the user while the marigold fades. */
    SquashAndPop,
}

/** The splash's rules, without the Android classes. */
object SplashPolicy {
    /** A settings read that hangs must not trap anyone on the splash. The first screen decides by itself after that. */
    const val MAX_HOLD_MS = 2_000L // token-audit: allow (product timing: longest splash hold, not motion)

    /** How long a finished launch may wait for the icon animation to end. It never delays a slow launch further. */
    const val MAX_ICON_WAIT_MS = MotionDurations.ScreenFast.toLong()

    /** The squash: wider and flatter, like a chunky button under a thumb. */
    const val SQUASH_X = 1.12f
    const val SQUASH_Y = 0.88f

    /** The pop: the mark grows past the screen edge of attention as it fades. */
    const val POP_SCALE = 1.35f

    fun keepOnScreen(themeLoaded: Boolean, startResolved: Boolean, heldForMs: Long): Boolean =
        (!themeLoaded || !startResolved) && heldForMs < MAX_HOLD_MS

    /** Lets a running icon animation finish, so the receipt is never cut off mid-tear. */
    fun exitDelayMs(nowMs: Long, iconStartMs: Long, iconDurationMs: Long): Long =
        (iconStartMs + iconDurationMs - nowMs).coerceIn(0, MAX_ICON_WAIT_MS)

    fun exit(reduceMotionSetting: Boolean, animatorsEnabled: Boolean): SplashExit {
        val level = resolveMotionLevel(reduceMotionSetting, if (animatorsEnabled) 1f else 0f)
        return if (level == MotionLevel.Reduced) SplashExit.Remove else SplashExit.SquashAndPop
    }
}

/**
 * Holds the splash until the app knows its theme and its first screen (onboarding or groups), then hands off: the mark squashes
 * and pops while the marigold fades over the first real frame. Reduced motion removes it at once. If the system never hands the
 * splash over, as happens on warm starts, the listener simply doesn't run and nothing waits for it.
 */
fun SplashScreen.holdUntilReadyThenHandOff(themeLoaded: () -> Boolean, startResolved: () -> Boolean, reduceMotion: () -> Boolean) {
    val start = SystemClock.uptimeMillis()
    setKeepOnScreenCondition { SplashPolicy.keepOnScreen(themeLoaded(), startResolved(), SystemClock.uptimeMillis() - start) }
    setOnExitAnimationListener { provider ->
        when (SplashPolicy.exit(reduceMotion(), ValueAnimator.areAnimatorsEnabled())) {
            SplashExit.Remove -> provider.remove()
            SplashExit.SquashAndPop -> provider.playExit()
        }
    }
}

private fun SplashScreenViewProvider.playExit() {
    val delay = SplashPolicy.exitDelayMs(SystemClock.uptimeMillis(), iconAnimationStartMillis, iconAnimationDurationMillis)
    playSplashExit(view, iconView, delay, onDone = ::remove)
}

/** Separate from the provider, whose constructor is private to the library, so a test can run the real animation. */
internal fun playSplashExit(splash: View, icon: View, delayMs: Long, onDone: () -> Unit) {
    val squash = MotionDurations.Press.toLong()
    val pop = MotionDurations.Small.toLong()
    icon.animate()
        .scaleX(SplashPolicy.SQUASH_X)
        .scaleY(SplashPolicy.SQUASH_Y)
        .setStartDelay(delayMs)
        .setDuration(squash)
        .setInterpolator(MotionEasing.Emphasized.curve.asInterpolator())
        .withEndAction {
            icon.animate()
                .scaleX(SplashPolicy.POP_SCALE)
                .scaleY(SplashPolicy.POP_SCALE)
                .alpha(0f)
                .setStartDelay(0)
                .setDuration(pop)
                .setInterpolator(MotionEasing.EmphasizedAccelerate.curve.asInterpolator())
                .start()
        }
        .start()
    splash.animate()
        .alpha(0f)
        .setStartDelay(delayMs + squash)
        .setDuration(pop)
        .setInterpolator(MotionEasing.Standard.curve.asInterpolator())
        .withEndAction(onDone)
        .start()
}

private fun Easing.asInterpolator() = TimeInterpolator { transform(it) }
