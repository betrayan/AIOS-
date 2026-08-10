package com.buddy.aios

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.buddy.aios.core.ui.theme.BuddyTheme
import com.buddy.aios.navigation.AppNavGraph
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity architecture entry point.
 *
 * Responsibilities:
 * - Edge-to-edge display (full immersive experience)
 * - Applies [BuddyTheme] as root composable wrapper
 * - Hosts the top-level [AppNavGraph]
 *
 * Nothing else belongs here. Business logic lives in ViewModels.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BuddyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    AppNavGraph()
                }
            }
        }
    }
}
