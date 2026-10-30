package com.darshan.splitsmart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.darshan.splitsmart.ui.navigation.SplitSmartNavHost
import com.darshan.splitsmart.ui.theme.SplitSmartTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SplitSmartTheme { SplitSmartNavHost() } }
    }
}
