package com.questline.app.ui.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.questline.app.data.AppRepo
import com.questline.app.data.Category
import com.questline.app.data.Task
import com.questline.app.ui.theme.Q

/**
 * Нижний лист создания/редактирования задачи.
 * editing == null → новая задача, иначе форма заполнена данными записи.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTaskSheet(
    editing: Task?,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (title: String, complexity: String, categoryId: Long?, dueEpochDay: Long?, repeatDaily: Boolean) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        var title by remember(editing?.id) { mutableStateOf(editing?.title.orEmpty()) }
        var complexity by remember(editing?.id) { mutableStateOf(editing?.complexity ?: "M") }
        var categoryId by remember(editing?.id) { mutableStateOf(editing?.categoryId) }
        var dueEpochDay by remember(editing?.id) { mutableStateOf(editing?.dueEpochDay) }
        var repeatDaily by remember(editing?.id) { mutableStateOf(editing?.repeatDaily ?: false) }

        val today = AppRepo.todayEpochDay

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (editing == null) "Новая задача" else "Редактировать",
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("Что нужно сделать?") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "Сложность",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("S", "M", "L").forEach { level ->
                    SelectableChip(
                        text = level,
                        selected = complexity == level,
                        modifier = Modifier.weight(1f),
                        onClick = { complexity = level },
                    )
                }
            }

            Text(
                text = "Категория (необязательно)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.forEach { category ->
                    val label = buildString {
                        if (category.emoji.isNotEmpty()) append(category.emoji + " ")
                        append(category.name)
                    }
                    SelectableChip(
                        text = label,
                        selected = categoryId == category.id,
                        onClick = {
                            categoryId = if (categoryId == category.id) null else category.id
                        },
                    )
                }
            }

            Text(
                text = "Срок",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectableChip(
                    text = "Сегодня",
                    selected = dueEpochDay == today,
                    onClick = { dueEpochDay = today },
                    modifier = Modifier.weight(1f),
                )
                SelectableChip(
                    text = "Завтра",
                    selected = dueEpochDay == today + 1,
                    onClick = { dueEpochDay = today + 1 },
                    modifier = Modifier.weight(1f),
                )
                SelectableChip(
                    text = "Без даты",
                    selected = dueEpochDay == null,
                    onClick = { dueEpochDay = null },
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Повторять ежедневно",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(checked = repeatDaily, onCheckedChange = { repeatDaily = it })
            }

            Spacer(Modifier.height(2.dp))
            Button(
                onClick = { onSave(title.trim(), complexity, categoryId, dueEpochDay, repeatDaily) },
                enabled = title.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text(if (editing == null) "Добавить" else "Сохранить")
            }
        }
    }
}

/** Нейтральная пилюля выбора: выбранная — surfaceAlt без второго цвета */
@Composable
internal fun SelectableChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Q.surfaceAlt else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Q.border),
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) Q.ink else Q.inkMuted,
            maxLines = 1,
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}
