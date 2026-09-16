package com.questline.app.ui.habits

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.questline.app.ui.tasks.SelectableChip
import com.questline.app.ui.theme.Q

/* Части редактора привычки (T-05), вынесенные из HabitEditorSheet.kt:
   подписи полей, эмодзи-пресет, ряды чипов расписания, диалог подтверждения. */

@Composable
internal fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = Q.inkMuted,
    )
}

/** Пресет эмодзи: выбранный — акцентной рамкой */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EmojiPreset(emoji: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(42.dp)
            .background(
                color = if (selected) Q.accentSoft else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Q.accent else Q.border,
                shape = RoundedCornerShape(12.dp),
            )
            .combinedClickable(onClick = onClick),
    ) {
        Text(text = emoji, style = MaterialTheme.typography.titleMedium)
    }
}

/** Ряд чипов Пн–Вс: кратные биты маски 0..6 (бит 0 = ПН) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WeekdayPicker(mask: Int, onMask: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        weekdayShort.forEachIndexed { index, day ->
            val bit = 1 shl index
            SelectableChip(
                text = day,
                selected = mask and bit != 0,
                textPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                onClick = { onMask(if (mask and bit != 0) mask and bit.inv() else mask or bit) },
            )
        }
    }
}

/** Чипы 1–7 для гибкой недельной цели */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TimesPerWeekRow(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..7).forEach { n ->
            SelectableChip(
                text = "$n×",
                selected = selected == n,
                textPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                onClick = { onSelect(n) },
            )
        }
    }
}

/** Чипы интервалов 2/3/5/7/14/30 (1 = ежедневно, поэтому минимальный — 2) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IntervalRow(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(2, 3, 5, 7, 14, 30).forEach { n ->
            SelectableChip(
                text = "$n дн",
                selected = selected == n,
                textPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                onClick = { onSelect(n) },
            )
        }
    }
}

@Composable
internal fun EditorConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    danger: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title) },
        text = { Text(text, textAlign = TextAlign.Start) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (danger) Q.danger else Q.ink)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
