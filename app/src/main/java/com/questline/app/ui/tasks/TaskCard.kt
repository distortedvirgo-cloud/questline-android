package com.questline.app.ui.tasks

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.questline.app.data.Task
import com.questline.app.ui.theme.Q
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dueFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))

/** Дата дью словами: сегодня / завтра / «12 августа» */
internal fun dueLabel(dueEpochDay: Long, todayEpochDay: Long): String {
    val diff = dueEpochDay - todayEpochDay
    return when {
        diff == 0L -> "сегодня"
        diff == 1L -> "завтра"
        else -> LocalDate.ofEpochDay(dueEpochDay).format(dueFormatter)
    }
}

/**
 * Карточка задачи: поверхность с границей вместо теней, чекбокс слева,
 * справа иконка удаления; долгий тап тоже удаляет (через confirm на экране).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskCard(
    task: Task,
    emoji: String?,
    checked: Boolean,
    todayEpochDay: Long,
    onComplete: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Q.border),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onDelete),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckCircle(checked = checked, enabled = !checked, onClick = onComplete)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ComplexityBadge(label = task.complexity)
                    if (!emoji.isNullOrEmpty()) {
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (task.repeatIntervalDays > 0) {
                        Spacer(Modifier.size(8.dp))
                        Icon(
                            imageVector = Icons.Filled.Repeat,
                            contentDescription = if (task.repeatIntervalDays == 1) "Ежедневно"
                            else "Раз в ${task.repeatIntervalDays} дн.",
                            tint = Q.inkMuted,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    task.dueEpochDay?.let { due ->
                        Text(
                            text = dueLabel(due, todayEpochDay),
                            style = MaterialTheme.typography.bodySmall,
                            color = Q.inkMuted,
                        )
                    }
                }
            }
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "Удалить",
                tint = Q.inkMuted,
                modifier = Modifier
                    .size(38.dp)
                    .padding(all = 10.dp)
                    .combinedClickable(onClick = onDelete),
            )
        }
    }
}

/** Мягкий галочкой confirm: заливка появляется со scale 0.9 → 1, 200 мс */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun CheckCircle(checked: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "checkConfirm",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(26.dp)
            .border(width = 2.dp, color = if (checked) Q.success else Q.border, shape = CircleShape)
            .combinedClickable(enabled = enabled, onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    alpha = progress
                    val scale = 0.9f + 0.1f * progress
                    scaleX = scale
                    scaleY = scale
                }
                .background(color = Q.success, shape = CircleShape),
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Q.surface,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/** Бейдж сложности S/M/L — акцентный контейнер по STYLE.md */
@Composable
private fun ComplexityBadge(label: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Q.accentSoft,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Q.accent,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
