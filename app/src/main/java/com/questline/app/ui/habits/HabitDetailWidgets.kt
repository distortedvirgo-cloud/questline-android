package com.questline.app.ui.habits

/* Виджеты экрана привычки (N-02), вынесенные из HabitDetailScreen ради лимита
 * 300 строк: ряд метрик, кнопки заморозки/редактирования/архива и диалоги.
 */

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.questline.app.data.AppRepo
import com.questline.app.ui.theme.Q

/** Четыре метрики: стрик, лучший, всего отметок, % за 30 дней */
@Composable
internal fun MetricsRow(state: HabitDetailViewModel.HabitDetailUi, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricChip("🔥 ${state.streakCurrent}", "Стрик", Modifier.weight(1f))
        MetricChip("🏆 ${state.streakBest}", "Лучший", Modifier.weight(1f))
        MetricChip("✅ ${state.totalChecks}", "Отметок", Modifier.weight(1f))
        MetricChip("📈 ${state.month30Percent}%", "30 дней", Modifier.weight(1f))
    }
}

@Composable
private fun MetricChip(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(12.dp), color = Q.surfaceAlt, modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        ) {
            Text(text = value, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = Q.inkMuted)
        }
    }
}

/** Кнопка платной заморозки сегодня: задизейблена, если не применима */
@Composable
internal fun FreezeTodayButton(
    enabled: Boolean,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Q.surfaceAlt)
            .clickable(enabled = enabled) { confirmOpen = true }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = "❄ Заморозить сегодня · ${AppRepo.FREEZE_COST} монет",
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) Q.ink else Q.inkMuted,
        )
    }

    if (confirmOpen) {
        AlertDialog(
            onDismissRequest = { confirmOpen = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Заморозить сегодня за ${AppRepo.FREEZE_COST} монет?") },
            text = { Text("День будет перекрыт снежинкой: стрик не рвётся и не удлиняется.") },
            confirmButton = {
                TextButton(onClick = { confirmOpen = false; onConfirm() }) { Text("Заморозить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmOpen = false }) { Text("Отмена") }
            },
        )
    }
}

/** Плоская кнопка действия; акцентная — «Редактировать» */
@Composable
internal fun ActionButton(
    text: String,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (emphasized) Q.accentSoft else Q.surfaceAlt,
        border = if (emphasized) null else BorderStroke(1.dp, Q.border),
        modifier = modifier,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 12.dp)) {
            Text(text = text, style = MaterialTheme.typography.labelLarge, color = Q.ink)
        }
    }
}

/** Архивация мягче удаления: прогресс и стрик остаются в базе */
@Composable
internal fun ArchiveHabitDialog(habitTitle: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
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
