package com.darshan.splitsmart.ui.designsystem.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/** Every duration in the app, in milliseconds. Screens name a [MotionToken]; these numbers stay here. */
object MotionDurations {
    /** Finger-down feedback. Starts on the next frame and lands within this. */
    const val Press = 90
    const val Micro = 120
    const val MicroSlow = 150
    const val Small = 220
    const val SmallSlow = 280
    const val ScreenFast = 340
    const val Screen = 380
    const val ScreenSlow = 460

    /** Delay between neighbouring items of a staggered entrance. */
    const val Stagger = 35

    /** A long list stops adding delay here, so item 200 does not arrive seconds late. */
    const val StaggerMax = 280

    /** Delay between neighbouring digit columns of a rolling amount: a ripple, not a queue. */
    const val DigitStagger = 18

    /** Every essential transition becomes a fade this short under reduced motion. */
    const val ReducedFade = 150

    const val Shimmer = 1200
    const val Ambient = 2200
    const val Shake = 360

    /** One full confetti burst, from pop to the last piece leaving. */
    const val Confetti = 1600

    /** How long an undo snackbar waits before the change becomes final. */
    const val UndoWindow = 5000

    /** Gap between the steps of a sequence that plays one item at a time, such as AI lines resolving. */
    const val SequenceStep = 260

    /** Two accepted taps on the same button are at least this far apart. */
    const val TapDebounce = 500
}

/** The only easing curves. There is deliberately no linear curve: UI motion always has weight. */
enum class MotionEasing(val curve: Easing) {
    /** Default for things that move within the screen. */
    Emphasized(CubicBezierEasing(0.2f, 0f, 0f, 1f)),

    /** Things arriving: quick start, long settle. */
    EmphasizedDecelerate(CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)),

    /** Things leaving: gentle start, quick exit. */
    EmphasizedAccelerate(CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)),

    /** Fades and ambient loops. */
    Standard(CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
}

/** A spring by its physical values, kept apart from Compose's generic `SpringSpec` so tests can compare it. */
data class SpringToken(val stiffness: Float, val dampingRatio: Float) {
    fun <T> spec(): SpringSpec<T> = spring(dampingRatio = dampingRatio, stiffness = stiffness)
    fun <T> spec(visibilityThreshold: T): SpringSpec<T> = spring(dampingRatio, stiffness, visibilityThreshold)
}

/** Marigold Mela springs: bouncy but quick. Anything touched or dragged uses one of these. */
object MotionSprings {
    /** Press-in, small flips. Barely overshoots. */
    val Snappy = SpringToken(stiffness = 1400f, dampingRatio = 0.85f)

    /** Release, arrivals, bumps: the visible bounce that gives SplitSmart its personality. */
    val Bouncy = SpringToken(stiffness = 600f, dampingRatio = 0.55f)

    /** Big things coming to rest: sheets, rolling digits. */
    val Gentle = SpringToken(stiffness = 260f, dampingRatio = 0.8f)
}

/** How much a motion matters, which decides what happens to it under reduced motion. */
enum class MotionRole {
    /** Confirms a touch. Stays, but short and without overshoot. */
    Feedback,

    /** Explains a change of state or place. Becomes a short fade. */
    Essential,

    /** Pure atmosphere and delight. Turned off. */
    Decorative,
}

/** What a [MotionToken] resolves to. Pure data: unit tests assert on it without a clock. */
sealed interface MotionPlan {
    data class Spring(val spring: SpringToken) : MotionPlan
    data class Tween(val durationMs: Int, val easing: MotionEasing) : MotionPlan
    data class Fade(val durationMs: Int) : MotionPlan
    data object None : MotionPlan
}

/** The motion vocabulary. Components and screens name one of these; [SplitSmartMotion] decides how it plays. */
enum class MotionToken(val role: MotionRole, val fullPlan: MotionPlan) {
    /** Finger down on anything tappable: the squash. */
    Press(MotionRole.Feedback, MotionPlan.Spring(MotionSprings.Snappy)),

    /** Finger up: springs back past rest, which is the stretch. */
    Release(MotionRole.Feedback, MotionPlan.Spring(MotionSprings.Bouncy)),

    /** A small state flip: a chip, a check. */
    Snap(MotionRole.Essential, MotionPlan.Spring(MotionSprings.Snappy)),

    /** Something arriving with energy: snackbar, avatars fanning out, the bottom bar blob. */
    Bounce(MotionRole.Essential, MotionPlan.Spring(MotionSprings.Bouncy)),

    /** A counter or badge reacting to a new value. */
    Bump(MotionRole.Essential, MotionPlan.Spring(MotionSprings.Bouncy)),

    /** A large element coming to rest: sheet, digits. */
    Settle(MotionRole.Essential, MotionPlan.Spring(MotionSprings.Gentle)),

    Micro(MotionRole.Essential, MotionPlan.Tween(MotionDurations.Micro, MotionEasing.Emphasized)),
    Small(MotionRole.Essential, MotionPlan.Tween(MotionDurations.Small, MotionEasing.Emphasized)),

    /** Screen-sized change arriving. */
    Screen(MotionRole.Essential, MotionPlan.Tween(MotionDurations.Screen, MotionEasing.EmphasizedDecelerate)),

    /** Screen-sized change leaving. */
    ScreenExit(MotionRole.Essential, MotionPlan.Tween(MotionDurations.Small, MotionEasing.EmphasizedAccelerate)),

    /** Error wiggle on an input. */
    Shake(MotionRole.Essential, MotionPlan.Tween(MotionDurations.Shake, MotionEasing.Standard)),

    /** Bobbing illustrations, the loader's petals. */
    Ambient(MotionRole.Decorative, MotionPlan.Tween(MotionDurations.Ambient, MotionEasing.Standard)),
    Shimmer(MotionRole.Decorative, MotionPlan.Tween(MotionDurations.Shimmer, MotionEasing.Standard)),

    /** Confetti and other delight, for real achievements only. */
    Celebrate(MotionRole.Decorative, MotionPlan.Tween(MotionDurations.Confetti, MotionEasing.EmphasizedDecelerate)),
}
