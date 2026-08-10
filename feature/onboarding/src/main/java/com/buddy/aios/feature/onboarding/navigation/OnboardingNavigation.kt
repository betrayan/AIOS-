package com.buddy.aios.feature.onboarding.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.buddy.aios.feature.onboarding.presentation.OnboardingScreen

fun NavGraphBuilder.onboardingNavGraph(navController: NavController) {
    composable(route = "onboarding") {
        OnboardingScreen(
            onOnboardingComplete = {
                navController.navigate("home") {
                    popUpTo("onboarding") { inclusive = true }
                }
            },
        )
    }
}
