package com.questline.app.ui.today

/* Главный экран «Сегодня» v3 — план дня одним скроллом (T-06).
 * Секции сверху вниз: утро-блок (дата/приветствие, уровень/XP-шкала, «Прогресс
 * дня: N/M», стики, мини-радар характеристик T-13), квест дня (карточки v2 с
 * конфетти), совет дня (rule-движок T-12), привычки дня (чеки через
 * repo.checkHabit, XP-полёт, чип заморозки), задачи сегодня с быстрым
 * добавлением, мини-деньги. Компоненты секций — в Today*.kt, состояние — в
 * TodayViewModel.kt. Вехи стрика — полноэкранное празднование
 * MilestoneCelebration (T-07).
 */

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.questline.app.data.AppRepo
import com.questline.app.domain.BudgetQuestEngine
import com.questline.app.domain.QuestGenerator
import com.questline.app.ui.theme.Q

private const val MAX_TODAY_TASKS = 5

// ------------------ Экран ------------------

@Composable
fun TodayScreen(onOpenAllTasks: () -> Unit = {}, onOpenMoney: () -> Unit = {}) {
    val context = LocalContext.current
    val repo = remember { AppRepo.get(context) }
    val vm: TodayViewModel = viewModel(initializer = { TodayViewModel(repo) })

    // Генерация авто-квестов дня + первичный расчёт уровня/стрика.
    LaunchedEffect(Unit) {
        QuestGenerator.ensureTodayQuests(repo, AppRepo.todayEpochDay)
        BudgetQuestEngine.ensureBudgetQuests(repo, AppRepo.todayEpochDay)
        BudgetQuestEngine.resolveBudgetQuests(repo, AppRepo.todayEpochDay)
        // Разовая уборка: подтверждённые/отброшенные пуши и SMS старше недели
        repo.pending.cleanupOld(System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000)
        vm.loadProgressOnce()
    }

    val quests by vm.questsOfDay.collectAsStateWithLifecycle()
    val tasks by vm.tasksToday.collectAsStateWithLifecycle()
    val coins by vm.coins.collectAsStateWithLifecycle()
    val keyEmoji by vm.keyEmoji.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val busyIds by vm.busyQuestIds.collectAsStateWithLifecycle()
    val habits by vm.habits.collectAsStateWithLifecycle()
    val checksToday by vm.habitChecksToday.collectAsStateWithLifecycle()
    val streaks by vm.habitStreaks.collectAsStateWithLifecycle()
    val characteristics by vm.characteristics.collectAsStateWithLifecycle()
    val questCategories by vm.questCategories.collectAsStateWithLifecycle()
    val freezeOffers by vm.freezeOfferDays.collectAsStateWithLifecycle()
    val milestone by vm.milestone.collectAsStateWithLifecycle()

    val (dayDone, dayTotal) = dayProgress(habits, checksToday, tasks, AppRepo.todayEpochDay)

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        ) {
            MorningHeader()
            Spacer(Modifier.height(12.dp))

            ProgressCard(progress ?: ProgressSnapshot())
            Spacer(Modifier.height(10.dp))
            DayProgressRow(done = dayDone, total = dayTotal)
            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(Modifier.weight(1f), emoji = "🔥", value = "${progress?.streakDays ?: 0} дней")
                StatCard(Modifier.weight(1f), emoji = "🪙", value = "$coins монет")
            }
            Spacer(Modifier.height(10.dp))
            MiniRadarCard(characteristics)
            Spacer(Modifier.height(20.dp))

            // Квест дня: карточки v2 (AUTO + BUDGET) с закрытием и конфетти.
            AiQuestButton(repo)
            if (quests.isNotEmpty()) {
                Text("Квест дня", style = MaterialTheme.typography.titleMedium, color = Q.inkMuted)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    quests.forEach { quest ->
                        QuestCard(quest, emoji = keyEmoji[quest.questKey].orEmpty(), busy = quest.id in busyIds, vm = vm)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            // Совет дня: rule-советы NudgeEngine по данным пользователя (T-12).
            TodayAdviceCard(onOpenMoney)
            Spacer(Modifier.height(20.dp))

            // Привычки дня: компактные ряды с чеками (+XP полёт, чип заморозки).
            TodayHabitsSection(habits, checksToday, streaks, freezeOffers, coins, AppRepo.todayEpochDay, vm)
            Spacer(Modifier.height(20.dp))

            // Задачи сегодня: чекбоксы v2 + быстрое добавление + «Все задачи ›».
            Text("Задачи сегодня", style = MaterialTheme.typography.titleMedium, color = Q.inkMuted)
            Spacer(Modifier.height(8.dp))
            TodayTasksSection(tasks.take(MAX_TODAY_TASKS), vm, questCategories, onOpenAllTasks)
            Spacer(Modifier.height(20.dp))

            // Мини-деньги: одна тихая строка → вкладка «Деньги».
            TodayMoneyRow(repo) { onOpenMoney() }
        }

        // Веха стрика: конфетти на весь экран (T-07), поверх скролла.
        MilestoneCelebration(event = milestone, onFinished = { vm.clearMilestone() })
    }
}
