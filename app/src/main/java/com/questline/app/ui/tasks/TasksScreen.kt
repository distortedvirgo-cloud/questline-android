package com.questline.app.ui.tasks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.questline.app.data.AppRepo
import com.questline.app.data.Task
import com.questline.app.ui.theme.Q
import com.questline.app.ui.tasks.TasksViewModel.Tab

/** Экран «Задачи»: сегменты Инбокс/Сегодня, список, создание и закрытие */
@Composable
fun TasksScreen() {
    val context = LocalContext.current
    val vm: TasksViewModel = viewModel(
        key = "tasks_screen",
        factory = viewModelFactory {
            initializer { TasksViewModel(AppRepo.get(context)) }
        },
    )
    val tab by vm.tab.collectAsStateWithLifecycle()
    val inbox by vm.inboxTasks.collectAsStateWithLifecycle()
    val todayTasks by vm.todayTasks.collectAsStateWithLifecycle()
    val categories by vm.questCategories.collectAsStateWithLifecycle()

    var editorOpen by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Task?>(null) }
    var deleteTarget by remember { mutableStateOf<Task?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Text(
                text = "Задачи",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )
            TabSegments(selected = tab, onSelect = vm::selectTab)

            val tasks = if (tab == Tab.INBOX) inbox else todayTasks
            if (tasks.isEmpty()) {
                EmptyHint(isInbox = tab == Tab.INBOX, modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 112.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(tasks, key = { it.id }) { task ->
                        val emoji = categories.firstOrNull { it.id == task.categoryId }?.emoji
                        val checked = task.done ||
                            (task.repeatDaily && task.lastDoneEpochDay == vm.todayEpochDay)
                        TaskCard(
                            task = task,
                            emoji = emoji?.takeIf { it.isNotEmpty() },
                            checked = checked,
                            todayEpochDay = vm.todayEpochDay,
                            onComplete = { vm.complete(task) },
                            onOpen = { editTarget = task; editorOpen = true },
                            onDelete = { deleteTarget = task },
                        )
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
    }

    if (editorOpen) {
        AddTaskSheet(
            editing = editTarget,
            categories = categories,
            onDismiss = { editorOpen = false },
            onSave = { title, complexity, categoryId, due, repeatDaily ->
                vm.save(editTarget?.id, title, complexity, categoryId, due, repeatDaily)
                editorOpen = false
            },
        )
    }

    deleteTarget?.let { target ->
        DeleteConfirmDialog(
            taskTitle = target.title,
            onConfirm = {
                vm.delete(target.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

/** Два чипа-сегмента сверху: Инбокс / Сегодня */
@Composable
private fun TabSegments(selected: Tab, onSelect: (Tab) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        TabSelectable(Tab.INBOX, "Инбокс", selected, onSelect, Modifier.weight(1f))
        TabSelectable(Tab.TODAY, "Сегодня", selected, onSelect, Modifier.weight(1f))
    }
}

@Composable
private fun TabSelectable(
    tab: Tab,
    label: String,
    selected: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier,
) {
    SelectableChip(
        text = label,
        selected = selected == tab,
        onClick = { onSelect(tab) },
        modifier = modifier,
    )
}

/** Спокойная пустота без наказания — просто подсказка */
@Composable
private fun EmptyHint(isInbox: Boolean, modifier: Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.size(24.dp))
            Text(
                text = "🗒️",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = if (isInbox) "Задач пока нет." else "На сегодня всё чисто.",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = if (isInbox) "Нажмите «+», чтобы добавить первую." else "Хороший момент для отдыха или новых задач.",
                style = MaterialTheme.typography.bodySmall,
                color = Q.inkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
    }
}

/** Удаление только через подтверждение */
@Composable
private fun DeleteConfirmDialog(taskTitle: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Удалить задачу?") },
        text = { Text("«$taskTitle» будет удалена безвозвратно.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Удалить", color = Q.danger)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}
