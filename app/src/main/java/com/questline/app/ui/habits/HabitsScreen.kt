package com.questline.app.ui.habits

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.questline.app.data.AppRepo
import com.questline.app.data.habits.Habit
import com.questline.app.domain.habits.HabitEngine
import com.questline.app.ui.theme.Q
import com.questline.app.ui.today.MilestoneCelebration

/** Вкладка «Привычки» (T-04): «Сегодня» по расписанию + «Все привычки».
 *  Тап по карточке — экран привычки (N-02); чек-кнопка остаётся отметкой. */
@Composable
fun HabitsScreen(onOpenDetail: (Long) -> Unit = {}) {
    val context = LocalContext.current
    val vm: HabitsViewModel = viewModel(
        key = "habits_screen",
        factory = viewModelFactory {
            initializer { HabitsViewModel(AppRepo.get(context)) }
        },
    )
    val cards by vm.cards.collectAsStateWithLifecycle()
    val archived by vm.archived.collectAsStateWithLifecycle()
    val pulse by vm.pulse.collectAsStateWithLifecycle()
    val coins by vm.coins.collectAsStateWithLifecycle()
    val milestone by vm.milestone.collectAsStateWithLifecycle()

    var editorOpen by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Habit?>(null) }
    var archiveTarget by remember { mutableStateOf<Habit?>(null) }
    var archiveExpanded by remember { mutableStateOf(false) }

    val dueToday = cards.filter { HabitEngine.isDue(it.habit, vm.todayEpochDay) }
    val rest = cards.filterNot { HabitEngine.isDue(it.habit, vm.todayEpochDay) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Text(
                text = "Привычки",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )
            // Пусто только когда нет и активных, и архивных — иначе архив недоступен
            if (cards.isEmpty() && archived.isEmpty()) {
                EmptyHint(Modifier.weight(1f))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 112.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    if (dueToday.isNotEmpty()) {
                        item { SectionHeader("Сегодня") }
                        items(dueToday, key = { it.habit.id }) { card ->
                            HabitCard(
                                card = card,
                                pulse = pulse?.takeIf { it.habitId == card.habit.id },
                                canFreeze = canAffordFreeze(coins),
                                onCheck = { vm.check(card) },
                                onUncheck = { vm.uncheck(card) },
                                onOpenDetail = { onOpenDetail(card.habit.id) },
                                onArchive = { archiveTarget = card.habit },
                                onFreeze = { vm.freeze(card) },
                            )
                        }
                    }
                    if (rest.isNotEmpty()) {
                        item { SectionHeader("Все привычки") }
                        items(rest, key = { it.habit.id }) { card ->
                            HabitCard(
                                card = card,
                                pulse = pulse?.takeIf { it.habitId == card.habit.id },
                                canFreeze = canAffordFreeze(coins),
                                onCheck = { vm.check(card) },
                                onUncheck = { vm.uncheck(card) },
                                onOpenDetail = { onOpenDetail(card.habit.id) },
                                onArchive = { archiveTarget = card.habit },
                                onFreeze = { vm.freeze(card) },
                            )
                        }
                    }
                    if (archived.isNotEmpty()) {
                        item(key = "archive_header") {
                            ArchiveHeader(
                                count = archived.size,
                                expanded = archiveExpanded,
                                onToggle = { archiveExpanded = !archiveExpanded },
                            )
                        }
                        if (archiveExpanded) {
                            items(archived, key = { "archived-${it.id}" }) { habit ->
                                ArchivedHabitRow(
                                    habit = habit,
                                    onRestore = { vm.unarchive(habit) },
                                )
                            }
                        }
                    }
                }
            }
        }

        // Единственный акцентный элемент экрана — кнопка добавления
        Surface(
            onClick = { editTarget = null; editorOpen = true },
            shape = CircleShape,
            color = Q.accent,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .size(58.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = "+", style = MaterialTheme.typography.headlineMedium)
            }
        }

        // Веха стрика: конфетти на весь экран (T-07)
        MilestoneCelebration(event = milestone, onFinished = { vm.clearMilestone() })
    }

    if (editorOpen) {
        HabitEditorSheet(
            existing = editTarget,
            onDismiss = { editorOpen = false },
            onSave = { habit ->
                vm.save(habit)
                editorOpen = false
            },
            onArchive = { habit ->
                vm.archive(habit)
                editorOpen = false
            },
            onDelete = { habit ->
                vm.delete(habit.id)
                editorOpen = false
            },
        )
    }

    archiveTarget?.let { target ->
        ArchiveConfirmDialog(
            habitTitle = target.title,
            onConfirm = {
                vm.archive(target)
                archiveTarget = null
            },
            onDismiss = { archiveTarget = null },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = Q.inkMuted,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** Заголовок секции «Архив»: по тапу разворачивается список архивных привычек */
@Composable
private fun ArchiveHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(top = 8.dp, bottom = 2.dp),
    ) {
        Text(
            text = "Архив ($count)",
            style = MaterialTheme.typography.titleSmall,
            color = Q.inkMuted,
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = if (expanded) "▾" else "▸",
            style = MaterialTheme.typography.titleSmall,
            color = Q.inkMuted,
        )
    }
}

/** Компактная строка архивной привычки: эмодзи + название + «Вернуть» */
@Composable
private fun ArchivedHabitRow(habit: Habit, onRestore: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Q.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Text(
                text = habit.emoji.ifEmpty { "🔁" },
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = habit.title,
                style = MaterialTheme.typography.bodyMedium,
                color = Q.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRestore) { Text("Вернуть") }
        }
    }
}

/** Спокойная пустота без наказания — просто подсказка */
@Composable
private fun EmptyHint(modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.size(24.dp))
            Text(text = "🎯", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.size(8.dp))
            Text(
                text = "Привычек пока нет.",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = "Нажмите «+», чтобы завести первую. Каждый день — хоть немного.",
                style = MaterialTheme.typography.bodySmall,
                color = Q.inkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
    }
}

/** Архивация мягче удаления: прогресс и стрик остаются в базе */
@Composable
private fun ArchiveConfirmDialog(habitTitle: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Убрать в архив?") },
        text = { Text("«$habitTitle» исчезнет из списка, но отметки и стрик сохранятся.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Архивировать") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
