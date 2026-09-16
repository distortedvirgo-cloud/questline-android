package com.questline.app.ui.habits

/* Чип платной заморозки пропущенного дня (T-07). Компактный «❄ Заморозить»
 * на surfaceAlt; при нехватке монет задизейблен с подписью «Нужно 20 монет».
 * Тап открывает confirm-диалог «Заморозить день за 20 монет?»; списание,
 * гварды (нет чека, день не в будущем) — внутри AppRepo.freezeHabitDay.
 * Используется на карточке привычки и в ряде «Сегодня».
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.questline.app.data.AppRepo
import com.questline.app.ui.theme.Q

/** Хватает ли монет на платную заморозку дня */
internal fun canAffordFreeze(coins: Int): Boolean = coins >= AppRepo.FREEZE_COST

/** Чип заморозки: enabled — «❄ Заморозить», иначе «❄ Нужно 20 монет». */
@Composable
internal fun FreezeChip(canAfford: Boolean, onConfirm: () -> Unit) {
    var confirmOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Q.surfaceAlt)
            .clickable(enabled = canAfford) { confirmOpen = true }
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = if (canAfford) "❄ Заморозить" else "❄ Нужно ${AppRepo.FREEZE_COST} монет",
            style = MaterialTheme.typography.labelMedium,
            color = if (canAfford) Q.ink else Q.inkMuted,
        )
    }

    if (confirmOpen) {
        AlertDialog(
            onDismissRequest = { confirmOpen = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Заморозить день за ${AppRepo.FREEZE_COST} монет?") },
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
