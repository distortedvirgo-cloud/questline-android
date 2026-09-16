package com.questline.app.ui.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.questline.app.data.Goal
import com.questline.app.ui.theme.Q

/** Кнопка создания копилки (ведёт в редактор с пустой целью) */
@Composable
internal fun NewGoalButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) { Text("Новая копилка") }
}

@Composable
internal fun DepositDialog(
    goal: Goal,
    onDismiss: () -> Unit,
    onDeposit: (Long) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val minor = MoneyFormat.parseRubles(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Внести в «${goal.name}»") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    text = input.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }
                },
                label = { Text("Сумма") },
                suffix = { Text("\u20BD") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                isError = text.isNotBlank() && minor == null,
                supportingText = {
                    Text(
                        text = "Осталось ${MoneyFormat.text((goal.targetMinor - goal.savedMinor).coerceAtLeast(0L))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        },
        confirmButton = {
            Button(
                enabled = minor != null && minor > 0L,
                onClick = { onDeposit(minor ?: return@Button) },
            ) { Text("Внести") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

@Composable
internal fun GoalEditorDialog(
    goal: Goal,
    onDismiss: () -> Unit,
    onSave: (Goal) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(goal.name) }
    var targetText by remember { mutableStateOf((goal.targetMinor / 100).toString()) }
    var status by remember { mutableStateOf(goal.status) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val target = MoneyFormat.parseRubles(targetText)
    val isNew = goal.id == 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Новая копилка" else "Копилка") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { input ->
                        targetText = input.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }
                    },
                    label = { Text("Цель") },
                    suffix = { Text("\u20BD") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    isError = targetText.isNotBlank() && (target == null || target <= 0L),
                    supportingText = {
                        Text(
                            text = "Накоплено ${MoneyFormat.text(goal.savedMinor)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                if (!isNew) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = status == "ACTIVE",
                            onClick = { status = "ACTIVE" },
                            label = { Text("Активна") },
                        )
                        FilterChip(
                            selected = status == "DONE",
                            onClick = { status = "DONE" },
                            label = { Text("Достигнута") },
                        )
                    }
                    TextButton(onClick = { confirmingDelete = true }) {
                        Text("Удалить копилку", color = Q.danger)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && target != null && target > 0L,
                onClick = {
                    onSave(
                        goal.copy(
                            name = name.trim(),
                            targetMinor = target ?: return@Button,
                            status = if (isNew) goal.status else status,
                        ),
                    )
                },
            ) { Text(if (isNew) "Создать" else "Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Удалить копилку?") },
            text = { Text("Прогресс «${goal.name}» будет потерян безвозвратно.") },
            confirmButton = {
                TextButton(onClick = onDelete) { Text("Удалить", color = Q.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Оставить") }
            },
        )
    }
}
