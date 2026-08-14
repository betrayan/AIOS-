package com.buddy.aios.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.buddy.aios.feature.settings.presentation.SettingsScreen

fun NavGraphBuilder.settingsNavGraph(navController: NavController) {
    composable(route = "settings") {
        SettingsScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToMemory = { navController.navigate("memory") }
        )
    }
}
