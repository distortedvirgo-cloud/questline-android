package com.questline.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.questline.app.data.AppRepo
import com.questline.app.ui.onboarding.OnboardingGate
import com.questline.app.ui.onboarding.OnboardingScreen
import com.questline.app.ui.theme.Q
import com.questline.app.ui.assistant.AssistantScreen
import com.questline.app.ui.habits.HabitDetailScreen
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

    // Сидирование категорий при первом запуске + онбординг v3 (N-03): только
    // для новой установки с пустой БД; апгрейд с данными закрывается флагом.
    val context = androidx.compose.ui.platform.LocalContext.current
    var onboarding by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        val repo = AppRepo.get(context)
        repo.seedIfEmpty()
        onboarding = OnboardingGate.resolve(context, repo)
    }
    if (onboarding == true) {
        OnboardingScreen(onFinish = { onboarding = false })
        return
    }
    if (onboarding == null) {
        // Пока решение не получено — пустой фон схемы вместо мигающего «Сегодня»
        Box(Modifier.fillMaxSize().background(Q.bg))
        return
    }

    Scaffold(
        // Фон схемы, не прозрачный: под ним окно активности может быть светлым
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Экран привычки (N-02) — полноэкранный, как mirror
            val hideBar = currentRoute in setOf("settings", "mirror", "shop", "assistant", "operations", "stats") ||
                currentRoute?.startsWith("habit/") == true
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
                    onOpenHabits = { navController.navigate("habits") },
                )
            }
            composable("habits") {
                HabitsScreen(onOpenDetail = { id -> navController.navigate("habit/$id") })
            }
            composable(
                route = "habit/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                HabitDetailScreen(
                    habitId = entry.arguments?.getLong("id") ?: 0L,
                    onBack = { navController.popBackStack() },
                )
            }
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
