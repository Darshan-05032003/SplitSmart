package com.darshan.splitsmart.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.darshan.splitsmart.ui.designsystem.motion.LocalMotion
import com.darshan.splitsmart.ui.designsystem.motion.MotionToken
import com.darshan.splitsmart.ui.designsystem.motion.SplitSmartMotion
import com.darshan.splitsmart.ui.onboarding.AppStart
import com.darshan.splitsmart.ui.onboarding.OnboardingScreen
import com.darshan.splitsmart.ui.theme.SplitSmartTheme

/** The app grows in from slightly smaller as the intro leaves: a door opening, not a page turning. */
private const val APP_ARRIVAL_SCALE = 0.94f

/**
 * Root of the UI: nothing while the splash decides ([AppStart.Loading]), then the intro or the app.
 *
 * On a first run the app is not composed until the intro is done. A replay from the Me tab ([AppStart.Replaying]) lays the intro over
 * the app instead: the app keeps its screen, back stack and ViewModels, is hidden from TalkBack while covered, and back on the
 * intro's first page closes the replay rather than moving the hidden app. Status-bar ownership waits for each side of a hand-off
 * to finish arriving ([LocalSurfaceSettled]).
 */
@Composable
fun AppRoot(start: AppStart, onFinishOnboarding: () -> Unit, app: @Composable () -> Unit) {
    val replaying = start == AppStart.Replaying
    val motion = LocalMotion.current
    val background = SplitSmartTheme.colors.bg
    // Painted behind everything: mid-fade the window shows through, and the window follows the system theme, not the app's.
    Box(Modifier.fillMaxSize().drawBehind { drawRect(background) }) {
        val firstScreen = if (replaying) AppStart.Ready else start
        AnimatedContent(
            targetState = firstScreen,
            transitionSpec = { rootTransition(motion, initialState, targetState) },
            label = "appRoot",
        ) { state ->
            val scope = this
            CompositionLocalProvider(LocalSurfaceSettled provides { scope.hasArrived() }) {
                when (state) {
                    AppStart.Loading -> Box(Modifier.fillMaxSize())
                    AppStart.Onboarding -> OnboardingScreen(onFinish = onFinishOnboarding)
                    AppStart.Ready, AppStart.Replaying -> Box(Modifier.fillMaxSize().then(if (replaying) Modifier.clearAndSetSemantics { } else Modifier)) { app() }
                }
            }
        }
        AnimatedVisibility(visible = replaying, enter = motion.enter(), exit = motion.exit()) {
            val scope = this
            CompositionLocalProvider(LocalSurfaceSettled provides { scope.hasArrived() }) {
                BackHandler(onBack = onFinishOnboarding)
                OnboardingScreen(onFinish = onFinishOnboarding)
            }
        }
    }
}

/** Out of the splash nothing animates (the splash plays its own exit); intro to app grows the app in. */
internal fun rootTransition(motion: SplitSmartMotion, from: AppStart, to: AppStart): ContentTransform = when {
    from == AppStart.Loading -> ContentTransform(EnterTransition.None, ExitTransition.None)
    motion.reduced -> motion.fadeThrough()
    to == AppStart.Ready -> (fadeIn(motion.spec(MotionToken.Screen)) + scaleIn(motion.spec(MotionToken.Settle), initialScale = APP_ARRIVAL_SCALE)) togetherWith
        fadeOut(motion.spec(MotionToken.ScreenExit))
    else -> motion.fadeThrough()
}
