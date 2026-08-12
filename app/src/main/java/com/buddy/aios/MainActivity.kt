package com.buddy.aios

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.buddy.aios.core.ui.island.AIOSIsland
import com.buddy.aios.core.ui.island.AIOSIslandStateManager
import com.buddy.aios.core.ui.theme.BuddyTheme
import com.buddy.aios.navigation.AppNavGraph
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single-activity architecture entry point.
 *
 * Responsibilities:
 * - Edge-to-edge display (full immersive experience)
 * - Applies [BuddyTheme] as root composable wrapper
 * - Hosts the top-level [AppNavGraph]
 * - Overlays the [AIOSIsland] at the top of the screen (in-app, no window permission required)
 *
 * Nothing else belongs here. Business logic lives in ViewModels.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var islandStateManager: AIOSIslandStateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BuddyTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Main navigation content
                        AppNavGraph()

                        // AIOS Dynamic Island overlay — drawn above all content
                        // Positioned at top-center using status bar inset padding
                        val islandState by islandStateManager.displayState.collectAsStateWithLifecycle()
                        AIOSIsland(
                            displayState = islandState,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .windowInsetsPadding(WindowInsets.statusBars),
                        )
                    }
                }
            }
        }
    }
}
