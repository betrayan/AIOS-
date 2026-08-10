package com.buddy.aios.feature.memory.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.buddy.aios.feature.memory.presentation.MemoryScreen

fun NavGraphBuilder.memoryNavGraph(navController: NavController) {
    composable(route = "memory") {
        MemoryScreen(onNavigateBack = { navController.popBackStack() })
    }
}
