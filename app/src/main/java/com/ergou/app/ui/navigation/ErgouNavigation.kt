package com.ergou.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ergou.app.ui.chat.ChatScreen
import com.ergou.app.ui.memory.MemoryScreen

@Composable
fun ErgouNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "chat") {
        composable("chat") {
            ChatScreen(
                onNavigateToMemory = { navController.navigate("memory") }
            )
        }
        composable("memory") {
            MemoryScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
