package com.questline.app.ui.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.questline.app.data.AppRepo
import com.questline.app.data.habits.Habit
import com.questline.app.domain.habits.HabitEngine
import com.questline.app.ui.tasks.SelectableChip
import com.questline.app.ui.theme.Q

/**
 * Редактор привычки (T-05): название, эмодзи-пресеты, характеристика-чипы,
 * 4 варианта расписания, количественная цель, сложность. existing == null →
 * создание; для существующей — архив/удаление с подтверждением.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HabitEditorSheet(
    existing: Habit?,
    onDismiss: () -> Unit,
    onSave: (Habit) -> Unit,
    onArchive: (Habit) -> Unit,
    onDelete: (Habit) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
        var emoji by remember(existing?.id) { mutableStateOf(existing?.emoji ?: habitEmojiPresets.first()) }
        var characteristic by remember(existing?.id) { mutableStateOf(existing?.characteristic ?: "DISCIPLINE") }
        var scheduleType by remember(existing?.id) {
            mutableStateOf(existing?.scheduleType ?: HabitEngine.SCHEDULE_DAILY)
        }
        var weekdaysMask by remember(existing?.id) { mutableStateOf(existing?.weekdaysMask ?: 31) }
        var timesPerWeek by remember(existing?.id) { mutableStateOf(existing?.timesPerWeek?.takeIf { it > 0 } ?: 3) }
        var intervalDays by remember(existing?.id) { mutableStateOf(existing?.intervalDays?.takeIf { it > 0 } ?: 3) }
        var hasTarget by remember(existing?.id) { mutableStateOf(existing?.targetValue != null) }
        var targetText by remember(existing?.id) {
            mutableStateOf(existing?.targetValue?.let { fmtValue(it) }.orEmpty())
        }
        var unit by remember(existing?.id) { mutableStateOf(existing?.unit.orEmpty()) }
        var complexity by remember(existing?.id) { mutableStateOf(existing?.complexity ?: "M") }
        var reminderMin by remember(existing?.id) { mutableStateOf(existing?.reminderMinOfDay) }
        var confirm by remember { mutableStateOf<ConfirmKind?>(null) }

        val targetParsed = if (hasTarget) targetText.replace(',', '.').toDoubleOrNull() else null
        val valid = title.isNotBlank() &&
            (scheduleType != HabitEngine.SCHEDULE_WEEKDAYS || weekdaysMask != 0) &&
            (scheduleType != HabitEngine.SCHEDULE_INTERVAL || intervalDays >= 2) &&
            (!hasTarget || (targetParsed != null && targetParsed > 0.0))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (existing == null) "Новая привычка" else "Редактировать",
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("Например: чтение перед сном") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            FieldLabel("Эмодзи")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(habitEmojiPresets) { preset ->
                    EmojiPreset(
                        emoji = preset,
                        selected = emoji == preset,
                        onClick = { emoji = preset },
                    )
                }
            }

            FieldLabel("Характеристика")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                characteristicLabels.forEach { (key, label) ->
                    SelectableChip(
                        text = label,
                        selected = characteristic == key,
                        onClick = { characteristic = key },
                    )
                }
            }

            FieldLabel("Расписание")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                scheduleSegments.forEach { (key, label) ->
                    SelectableChip(
                        text = label,
                        selected = scheduleType == key,
                        onClick = { scheduleType = key },
                    )
                }
            }
            when (scheduleType) {
                HabitEngine.SCHEDULE_WEEKDAYS -> WeekdayPicker(weekdaysMask) { weekdaysMask = it }
                HabitEngine.SCHEDULE_TIMES_PER_WEEK -> TimesPerWeekRow(timesPerWeek) { timesPerWeek = it }
                HabitEngine.SCHEDULE_INTERVAL -> IntervalRow(intervalDays) { intervalDays = it }
            }

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(text = "Количественная цель", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Например: 30 мин, 8 стаканов",
                        style = MaterialTheme.typography.labelSmall,
                        color = Q.inkMuted,
                    )
                }
                Switch(checked = hasTarget, onCheckedChange = { hasTarget = it })
            }
            if (hasTarget) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = targetText,
                        onValueChange = { targetText = it },
                        placeholder = { Text("Число, напр. 30") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        placeholder = { Text("мин") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            FieldLabel("Сложность")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("S" to "S · лёгкая", "M" to "M · средняя", "L" to "L · большая").forEach { (key, label) ->
                    SelectableChip(
                        text = label,
                        selected = complexity == key,
                        modifier = Modifier.weight(1f),
                        onClick = { complexity = key },
                    )
                }
            }
            Text(
                text = "XP за отметку: +${HabitEngine.xpForHabit(complexity)} · дневной кап +20 XP",
                style = MaterialTheme.typography.labelSmall,
                color = Q.inkMuted,
            )

            ReminderField(
                minOfDay = reminderMin,
                onMinOfDay = { reminderMin = it },
            )

            Button(
                onClick = {
                    onSave(
                        Habit(
                            id = existing?.id ?: 0L,
                            title = title.trim(),
                            emoji = emoji,
                            colorIndex = existing?.colorIndex ?: 0,
                            characteristic = characteristic,
                            scheduleType = scheduleType,
                            weekdaysMask = if (scheduleType == HabitEngine.SCHEDULE_WEEKDAYS) weekdaysMask else 0,
                            timesPerWeek = if (scheduleType == HabitEngine.SCHEDULE_TIMES_PER_WEEK) timesPerWeek else 0,
                            intervalDays = if (scheduleType == HabitEngine.SCHEDULE_INTERVAL) intervalDays else 0,
                            targetValue = targetParsed?.takeIf { it > 0.0 },
                            unit = if (hasTarget) unit.trim().ifEmpty { null } else null,
                            complexity = complexity,
                            createdAt = existing?.createdAt ?: AppRepo.todayEpochDay,
                            archivedAt = existing?.archivedAt,
                            reminderMinOfDay = reminderMin,
                        ),
                    )
                },
                enabled = valid,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text(if (existing == null) "Добавить" else "Сохранить")
            }

            existing?.let { habit ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { confirm = ConfirmKind.ARCHIVE }) {
                        Text("Архивировать")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { confirm = ConfirmKind.DELETE }) {
                        Text("Удалить", color = Q.danger)
                    }
                }
            }
        }

        when (confirm) {
            ConfirmKind.ARCHIVE -> EditorConfirmDialog(
                title = "Убрать в архив?",
                text = "«${existing?.title}» исчезнет из списка, но отметки и стрик сохранятся.",
                confirmLabel = "Архивировать",
                danger = false,
                onConfirm = { existing?.let(onArchive); confirm = null },
                onDismiss = { confirm = null },
            )
            ConfirmKind.DELETE -> EditorConfirmDialog(
                title = "Удалить привычку?",
                text = "«${existing?.title}» и все отметки будут удалены безвозвратно.",
                confirmLabel = "Удалить",
                danger = true,
                onConfirm = { existing?.let(onDelete); confirm = null },
                onDismiss = { confirm = null },
            )
            null -> Unit
        }
    }
}

private enum class ConfirmKind { ARCHIVE, DELETE }

private val scheduleSegments = linkedMapOf(
    HabitEngine.SCHEDULE_DAILY to "Ежедневно",
    HabitEngine.SCHEDULE_WEEKDAYS to "Дни недели",
    HabitEngine.SCHEDULE_TIMES_PER_WEEK to "N× в неделю",
    HabitEngine.SCHEDULE_INTERVAL to "Каждые N дн",
)

/** Число цели без хвостовых нулей: 30.0 → «30», 2.5 → «2.5» */
internal fun fmtValue(x: Double): String = if (x % 1.0 == 0.0) x.toLong().toString() else x.toString()
