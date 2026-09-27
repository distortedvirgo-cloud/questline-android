package com.questline.app.ui.today

/* Секция задач «Сегодня» (v2, как есть): чек-круги задач — закрытие создаёт
 * USER-квест с XP (кор-луп), повторяющиеся отмечаются последним днём.
 * Закрытие празднуется как у квестов: конфетти QuestCompletionOverlay,
 * «+XP»-пульс и хаптик; снятие — тихо, без конфетти.
 * Пустое состояние без наказания. Ниже — быстрое добавление (AddTaskSheet)
 * и строка «Все задачи ›» в полный список.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.questline.app.data.AppRepo
import com.questline.app.data.Category
import com.questline.app.data.Task
import com.questline.app.domain.ProgressionEngine
import com.questline.app.ui.tasks.AddTaskSheet
import com.questline.app.ui.tasks.CheckCircle
import com.questline.app.ui.theme.Q

@Composable
internal fun TodayTasksSection(
    tasks: List<Task>,
    vm: TodayViewModel,
    categories: List<Category>,
    onOpenAllTasks: () -> Unit,
) {
    var addOpen by remember { mutableStateOf(false) }

    if (tasks.isEmpty()) {
        OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = CARD_SHAPE, colors = cardColors()) {
            Text(
                "Задач на сегодня нет. Добавь ниже или в «Все задачи»",
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Q.inkMuted,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = CARD_SHAPE, colors = cardColors()) {
            Column {
                tasks.forEachIndexed { index, task ->
                    if (index > 0) HorizontalDivider(color = Q.border, thickness = 1.dp)
                    TaskRow(task, vm)
                }
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "+ Добавить задачу",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .weight(1f)
                .clip(CHIP_SHAPE)
                .background(Q.surfaceAlt)
                .clickable { addOpen = true }
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
        Text(
            "Все задачи ›",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .weight(1f)
                .clip(CHIP_SHAPE)
                .background(Q.surfaceAlt)
                .clickable { onOpenAllTasks() }
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }

    if (addOpen) {
        AddTaskSheet(
            editing = null,
            categories = categories,
            onDismiss = { addOpen = false },
            onSave = { title, complexity, categoryId, dueEpochDay, repeatIntervalDays ->
                vm.addTask(title, complexity, categoryId, dueEpochDay, repeatIntervalDays)
                addOpen = false
            },
        )
    }
}

@Composable
private fun TaskRow(task: Task, vm: TodayViewModel) {
    val checked = task.repeatIntervalDays > 0 && task.lastDoneEpochDay == AppRepo.todayEpochDay
    val haptics = LocalHapticFeedback.current
    val burst = rememberQuestBurstState(seed = task.id * 31 + 7)
    var completing by remember { mutableStateOf(false) } // анимация закрытия
    var rowTopLeft by remember { mutableStateOf(Offset.Zero) }
    var buttonCenter by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { rowTopLeft = it.boundsInWindow().topLeft },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckCircle(
                checked = checked,
                // Клик живёт в обоих состояниях: снятие отметки — без конфетти
                enabled = true,
                onClick = {
                    when {
                        completing -> Unit // анимация уже идёт
                        checked -> vm.toggleTask(task, false)
                        else -> {
                            // Эффекты сначала, реальное закрытие — по onFinished (как у квестов)
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            completing = true
                        }
                    }
                },
                modifier = Modifier.onGloballyPositioned { buttonCenter = it.boundsInWindow().center },
            )
            Spacer(Modifier.width(12.dp))
            Text(
                task.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = if (checked) Q.inkMuted else Q.ink,
                textDecoration = if (checked) TextDecoration.LineThrough else null,
                maxLines = 2,
            )
        }
        QuestCompletionOverlay(
            visible = completing,
            xpText = "+${ProgressionEngine.xpFor(task.complexity)} XP",
            origin = buttonCenter - rowTopLeft,
            onFinished = {
                vm.toggleTask(task, true)
                completing = false
            },
            state = burst,
        )
    }
}
