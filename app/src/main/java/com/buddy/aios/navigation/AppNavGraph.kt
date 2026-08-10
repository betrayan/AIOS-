package com.buddy.aios.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.buddy.aios.core.ui.theme.BuddyColors
import com.buddy.aios.feature.chat.navigation.chatNavGraph
import com.buddy.aios.feature.home.navigation.homeNavGraph
import com.buddy.aios.feature.memory.navigation.memoryNavGraph
import com.buddy.aios.feature.onboarding.navigation.onboardingNavGraph
import com.buddy.aios.feature.settings.navigation.settingsNavGraph

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController(),
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute != null && !currentRoute.startsWith(AppDestinations.ONBOARDING)) {
                AIOSBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(AppDestinations.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        },
        containerColor = BuddyColors.BackgroundDeep,
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestinations.ONBOARDING,
            enterTransition = { fadeIn(animationSpec = tween(250)) + slideInHorizontally(initialOffsetX = { 80 }) },
            exitTransition = { fadeOut(animationSpec = tween(250)) + slideOutHorizontally(targetOffsetX = { -80 }) },
            popEnterTransition = { fadeIn(animationSpec = tween(250)) + slideInHorizontally(initialOffsetX = { -80 }) },
            popExitTransition = { fadeOut(animationSpec = tween(250)) + slideOutHorizontally(targetOffsetX = { 80 }) },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            onboardingNavGraph(navController)
            homeNavGraph(navController)
            chatNavGraph(navController)
            memoryNavGraph(navController)
            settingsNavGraph(navController)
        }
    }
}

object AppDestinations {
    const val ONBOARDING = "onboarding"
    const val HOME       = "home"
    const val CHAT       = "chat/{conversationId}"
    const val MEMORY     = "memory"
    const val SETTINGS   = "settings"

    fun chatRoute(conversationId: String) = "chat/$conversationId"
}
