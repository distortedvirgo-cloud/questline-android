package com.questline.app.ui.habits

/* Экран привычки (N-02): шапка и композиция; heatmap и список отметок — в
 * HabitDetailParts.kt, метрики и кнопки — в HabitDetailWidgets.kt.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.questline.app.data.AppRepo
import com.questline.app.data.habits.Habit
import com.questline.app.ui.money.colorForIndex
import com.questline.app.ui.theme.Q

/** Экран привычки: детали, heatmap, действия (N-02) */
@Composable
fun HabitDetailScreen(habitId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: HabitDetailViewModel = viewModel(
        key = "habit_detail_$habitId",
        factory = viewModelFactory {
            initializer { HabitDetailViewModel(AppRepo.get(context), habitId) }
        },
    )
    val ui by vm.ui.collectAsStateWithLifecycle()

    var editorOpen by remember { mutableStateOf(false) }
    var archiveOpen by remember { mutableStateOf(false) }
    var hadData by remember { mutableStateOf(false) }
    LaunchedEffect(ui) {
        when {
            ui != null -> hadData = true
            hadData -> onBack() // привычку удалили — экран не существует без данных
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(end = 16.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Назад")
            }
            Text(text = "Привычка", style = MaterialTheme.typography.titleLarge)
        }

        ui?.let { state ->
            HabitHeader(habit = state.habit, modifier = Modifier.padding(horizontal = 16.dp))
            MetricsRow(
                state = state,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp),
            )

            DetailSection(
                title = "Последние 18 недель",
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 20.dp),
            )
            HabitHeatmap(
                habit = state.habit,
                checks = state.checks,
                today = vm.todayEpochDay,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp),
            )

            DetailSection(
                title = "Последние отметки",
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 20.dp),
            )
            RecentChecksList(
                habit = state.habit,
                recent = state.recent,
                today = vm.todayEpochDay,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp),
            )

            FreezeTodayButton(
                enabled = state.canFreezeToday,
                onConfirm = { vm.freezeToday() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 20.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp),
            ) {
                ActionButton(
                    text = "Редактировать",
                    emphasized = true,
                    modifier = Modifier.weight(1f),
                    onClick = { editorOpen = true },
                )
                ActionButton(
                    text = "Архивировать",
                    emphasized = false,
                    modifier = Modifier.weight(1f),
                    onClick = { archiveOpen = true },
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    ui?.let { state ->
        if (editorOpen) {
            HabitEditorSheet(
                existing = state.habit,
                onDismiss = { editorOpen = false },
                onSave = { habit ->
                    vm.save(habit)
                    editorOpen = false
                },
                onArchive = { habit ->
                    vm.archive(habit)
                    editorOpen = false
                    onBack()
                },
                onDelete = { _ ->
                    vm.delete() // закроет экран: привычка исчезнет из потока
                    editorOpen = false
                },
            )
        }

        if (archiveOpen) {
            ArchiveHabitDialog(
                habitTitle = state.habit.title,
                onConfirm = {
                    vm.archive(state.habit)
                    archiveOpen = false
                    onBack()
                },
                onDismiss = { archiveOpen = false },
            )
        }
    }
}

/** Шапка: эмодзи в цветном круге, название, характеристика и расписание */
@Composable
private fun HabitHeader(habit: Habit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(colorForIndex(habit.colorIndex).copy(alpha = 0.16f)),
        ) {
            Text(
                text = habit.emoji.ifEmpty { "🔁" },
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = habit.title,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "${characteristicLabels[habit.characteristic] ?: habit.characteristic} · ${scheduleLabel(habit)}",
                style = MaterialTheme.typography.bodySmall,
                color = Q.inkMuted,
            )
            if (habit.targetValue != null) {
                Text(
                    text = "Цель: ${fmtNum(habit.targetValue ?: 0.0)}${unitSuffix(habit.unit)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Q.inkMuted,
                )
            }
        }
    }
}

private fun unitSuffix(unit: String?): String = unit?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""

private fun fmtNum(x: Double): String = if (x % 1.0 == 0.0) x.toLong().toString() else x.toString()

@Composable
private fun DetailSection(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = Q.inkMuted,
        modifier = modifier,
    )
}
