package com.darshan.splitsmart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darshan.splitsmart.ui.designsystem.effects.UiPreferences
import com.darshan.splitsmart.ui.navigation.SplitSmartNavHost
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var uiPreferences: UiPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by uiPreferences.settings.collectAsStateWithLifecycle()
            // The design-system tokens, motion level and haptics are live app-wide; Material widgets keep the pre-overhaul
            // look until the screens are rebuilt on the design system.
            SplitSmartTheme(settings) { SplitSmartNavHost() }
        }
    }
}
