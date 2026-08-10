package com.buddy.aios.feature.home.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.buddy.aios.feature.home.presentation.HomeScreen

fun NavGraphBuilder.homeNavGraph(navController: NavController) {
    composable(route = "home") {
        HomeScreen(
            onOpenConversation = { conversationId ->
                navController.navigate("chat/$conversationId")
            },
            onOpenSettings = { navController.navigate("settings") },
            onOpenMemory  = { navController.navigate("memory") },
        )
    }
}
