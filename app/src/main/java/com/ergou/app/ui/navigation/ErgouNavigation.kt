package com.ergou.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ergou.app.ui.chat.ChatScreen
import com.ergou.app.ui.memory.MemoryScreen
import com.ergou.app.ui.settings.SettingsScreen

@Composable
fun ErgouNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "chat") {
        composable("chat") {
            ChatScreen(
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
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
