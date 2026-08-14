package com.buddy.aios.feature.voice.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.buddy.aios.feature.voice.presentation.VoiceRecordingsScreen

const val VOICE_RECORDINGS_ROUTE = "voice_recordings"

fun NavGraphBuilder.voiceNavGraph(navController: NavController) {
    composable(VOICE_RECORDINGS_ROUTE) {
        VoiceRecordingsScreen(
            onNavigateBack = { navController.navigateUp() },
        )
    }
}
