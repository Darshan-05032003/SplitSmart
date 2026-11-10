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
            // Marigold Mela app-wide: design-system tokens, motion level, haptics, and Material slots for screens not rebuilt yet.
            SplitSmartTheme(settings) { SplitSmartNavHost() }
        }
    }
}
