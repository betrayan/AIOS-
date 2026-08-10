package com.buddy.aios.feature.chat.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.buddy.aios.feature.chat.presentation.ChatScreen

private const val ROUTE = "chat/{conversationId}"
private const val ARG_CONVERSATION_ID = "conversationId"

fun NavGraphBuilder.chatNavGraph(navController: NavController) {
    composable(
        route = ROUTE,
        arguments = listOf(
            navArgument(ARG_CONVERSATION_ID) { type = NavType.StringType },
        ),
    ) { backStackEntry ->
        val conversationId = backStackEntry.arguments?.getString(ARG_CONVERSATION_ID) ?: return@composable
        ChatScreen(
            conversationId = conversationId,
            onNavigateBack = { navController.popBackStack() },
        )
    }
}

fun NavController.navigateToChat(conversationId: String) {
    navigate("chat/$conversationId")
}
