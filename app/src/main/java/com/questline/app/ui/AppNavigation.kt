package com.questline.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.questline.app.data.AppRepo
import com.questline.app.ui.money.MoneyScreen
import com.questline.app.ui.profile.ProfileScreen
import com.questline.app.ui.tasks.TasksScreen
import com.questline.app.ui.today.TodayScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("today", "Сегодня", Icons.Filled.Today),
    Tab("tasks", "Задачи", Icons.Filled.Checklist),
    Tab("money", "Деньги", Icons.Filled.Savings),
    Tab("profile", "Профиль", Icons.Filled.Person),
)

@Composable
fun QuestlineApp() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // Сидирование дефолтных категорий при первом запуске
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { AppRepo.get(context).seedIfEmpty() }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        bottomBar = {
            NavigationBar(containerColor = androidx.compose.ui.theme.Q.surfaceAlt) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            if (currentRoute != tab.route) {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.startDestinationId)
                                    launchSingleTop = true
                                }
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "today",
            modifier = Modifier.padding(padding),
        ) {
            composable("today") { TodayScreen() }
            composable("tasks") { TasksScreen() }
            composable("money") { MoneyScreen() }
            composable("profile") { ProfileScreen(onNavigateToMoney = { navController.navigate("money") }) }
        }
    }
}
