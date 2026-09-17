package com.questline.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.questline.app.ui.assistant.AssistantScreen
import com.questline.app.ui.habits.HabitsScreen
import com.questline.app.ui.money.MoneyScreen
import com.questline.app.ui.money.OperationsScreen
import com.questline.app.ui.money.StatsScreen
import com.questline.app.ui.mirror.WeekMirrorScreen
import com.questline.app.ui.profile.ProfileScreen
import com.questline.app.ui.settings.SettingsScreen
import com.questline.app.ui.shop.ShopScreen
import com.questline.app.ui.tasks.TasksScreen
import com.questline.app.ui.today.TodayScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("today", "Сегодня", Icons.Filled.Today),
    Tab("habits", "Привычки", Icons.Filled.Repeat),
    Tab("money", "Деньги", Icons.Filled.Savings),
    Tab("profile", "Я", Icons.Filled.Person),
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
        // Фон схемы, не прозрачный: под ним окно активности может быть светлым
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            val hideBar = currentRoute in setOf("settings", "mirror", "shop", "assistant", "operations", "stats")
            if (!hideBar) {
                NavigationBar(containerColor = com.questline.app.ui.theme.Q.surfaceAlt) {
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
                        label = { Text(tab.label, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                    )
                }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "today",
            modifier = Modifier.padding(padding),
        ) {
            composable("today") {
                TodayScreen(
                    onOpenAllTasks = { navController.navigate("tasks") },
                    onOpenMoney = { navController.navigate("money") },
                    onOpenMirror = { navController.navigate("mirror") },
                )
            }
            composable("habits") { HabitsScreen() }
            composable("tasks") { TasksScreen() }
            composable("money") {
                MoneyScreen(
                    onOpenOperations = { navController.navigate("operations") },
                    onOpenStats = { navController.navigate("stats") },
                    onOpenAssistant = { navController.navigate("assistant") },
                )
            }
            composable("operations") { OperationsScreen(onBack = { navController.popBackStack() }) }
            composable("stats") { StatsScreen(onBack = { navController.popBackStack() }) }
            composable("profile") {
                ProfileScreen(
                    onNavigateToMoney = { navController.navigate("money") },
                    onNavigateToSettings = { navController.navigate("settings") },
                    onNavigateToMirror = { navController.navigate("mirror") },
                    onNavigateToShop = { navController.navigate("shop") },
                    onNavigateToAssistant = { navController.navigate("assistant") },
                )
            }
            composable("assistant") { AssistantScreen() }
            composable("settings") {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenMirror = { navController.navigate("mirror") },
                    onOpenShop = { navController.navigate("shop") },
                )
            }
            composable("mirror") {
                WeekMirrorScreen(onBack = { navController.popBackStack() })
            }
            composable("shop") {
                ShopScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
