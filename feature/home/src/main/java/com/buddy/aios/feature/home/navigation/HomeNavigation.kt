package com.buddy.aios.feature.home.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.buddy.aios.feature.home.presentation.HomeScreen

fun NavGraphBuilder.homeNavGraph(navController: NavController) {
    composable(route = "home") {
        HomeScreen(
            onNavigateToChat = { conversationId ->
                navController.navigate("chat/$conversationId")
            },
            onNavigateToMemory = { navController.navigate("memory") },
            onNavigateToSettings = { navController.navigate("settings") },
        )
    }
}
