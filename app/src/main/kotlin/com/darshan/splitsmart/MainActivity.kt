package com.darshan.splitsmart

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darshan.splitsmart.ui.designsystem.effects.UiPreferences
import com.darshan.splitsmart.ui.navigation.AppRoot
import com.darshan.splitsmart.ui.navigation.LaunchIntentPolicy
import com.darshan.splitsmart.ui.navigation.LocalStatusBarController
import com.darshan.splitsmart.ui.navigation.StatusBarController
import com.darshan.splitsmart.ui.navigation.SplitSmartNavHost
import com.darshan.splitsmart.ui.onboarding.AppStart
import com.darshan.splitsmart.ui.onboarding.AppStartViewModel
import com.darshan.splitsmart.ui.splash.holdUntilReadyThenHandOff
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var uiPreferences: UiPreferences

    private val appStart: AppStartViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Injected fields exist from here on. The first frame waits for the stored theme and for the first screen (intro or app),
        // so neither a wrong theme nor the wrong screen can show first.
        splash.holdUntilReadyThenHandOff(
            themeLoaded = { uiPreferences.loaded.value },
            startResolved = { appStart.start.value != AppStart.Loading },
            reduceMotion = { uiPreferences.settings.value.reduceMotion },
        )
        enableEdgeToEdge()
        // Before Navigation can read it: this activity is exported, so an intent's extras and data are an attacker's input.
        intent = LaunchIntentPolicy.sanitize(intent)
        setContent {
            val settings by uiPreferences.settings.collectAsStateWithLifecycle()
            val start by appStart.start.collectAsStateWithLifecycle()
            val statusBar = remember { StatusBarController() }
            // Marigold Mela app-wide: design-system tokens, motion level, haptics, and Material slots for screens not rebuilt yet.
            SplitSmartTheme(settings) {
                val themeDark = SplitSmartTheme.colors.isDark
                val darkIcons = statusBar.darkIcons(themeDark)
                // The app theme can differ from the system's, and a surface can differ from the theme: the icons follow both.
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = darkIcons
                        isAppearanceLightNavigationBars = !themeDark
                    }
                }
                CompositionLocalProvider(LocalStatusBarController provides statusBar) {
                    AppRoot(start, onFinishOnboarding = appStart::finish) { SplitSmartNavHost(onReplayIntro = appStart::replay) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        // Nothing reads later intents today; they are cleaned the same way so nothing ever can by accident.
        val clean = LaunchIntentPolicy.sanitize(intent)
        setIntent(clean)
        super.onNewIntent(clean)
    }
}
