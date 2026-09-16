package com.questline.app.ui.today

/* Совет дня (T-12): rule-совет из NudgeEngine по данным пользователя.
 * Кнопка «Взять» исполняет совет: START_HABIT — привычка из каталога в один
 * тап; SHRINK/GROW — понижение/повышение сложности привычки; TASK_INFO —
 * задача на сегодня (закроется как квест); FINANCE — переход в «Деньги»;
 * ANCHOR — только подсказка, без действия. После «Взять» — подтверждение,
 * новый совет на следующий день. Тени запрещены, границы — STYLE.md.
 */

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.questline.app.data.AppRepo
import com.questline.app.domain.advice.Tip
import com.questline.app.domain.advice.TipKind
import com.questline.app.ui.theme.Q
import android.widget.Toast

@Composable
internal fun TodayAdviceCard(onOpenMoney: () -> Unit = {}) {
    val context = LocalContext.current
    val repo = remember { AppRepo.get(context) }
    val vm: AdviceViewModel = viewModel(initializer = { AdviceViewModel(repo) })
    val ui by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(ui.event) {
        ui.event?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            vm.consumeEvent()
        }
    }

    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = CARD_SHAPE, colors = cardColors()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Совет дня", style = MaterialTheme.typography.titleMedium, color = Q.inkMuted)
                Spacer(Modifier.weight(1f))
                ui.tip?.let { KindChip(it.kind) }
            }
            Spacer(Modifier.height(10.dp))
            val tip = ui.tip
            if (tip == null) {
                Text(
                    "Собираю совет по твоим данным…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Q.inkMuted,
                )
            } else {
                Text(tip.title, style = MaterialTheme.typography.titleMedium, color = Q.ink)
                Spacer(Modifier.height(6.dp))
                Text(tip.body, style = MaterialTheme.typography.bodyMedium, color = Q.ink)
                Spacer(Modifier.height(12.dp))
                AdviceAction(tip, ui.confirmedId, ui.busy, vm, onOpenMoney)
            }
        }
    }
}

@Composable
private fun AdviceAction(
    tip: Tip,
    confirmedId: String?,
    busy: Boolean,
    vm: AdviceViewModel,
    onOpenMoney: () -> Unit,
) {
    when {
        tip.kind == TipKind.ANCHOR_HABIT -> Unit // подсказка без действия
        confirmedId == tip.id -> Confirmation(tip.kind)
        tip.kind == TipKind.FINANCE -> TextButton(
            onClick = onOpenMoney,
            colors = ButtonDefaults.textButtonColors(contentColor = Q.accent),
        ) { Text("Открыть «Деньги»") }
        else -> TextButton(
            onClick = { vm.take(tip) },
            enabled = !busy,
            colors = ButtonDefaults.textButtonColors(contentColor = Q.accent),
        ) { Text("Взять") }
    }
}

@Composable
private fun Confirmation(kind: TipKind) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("✓", color = Q.success, style = MaterialTheme.typography.bodyMedium)
        Text(confirmText(kind), color = Q.success, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun confirmText(kind: TipKind): String = when (kind) {
    TipKind.START_HABIT -> "Взял! Привычка создана"
    TipKind.SHRINK_HABIT -> "Готово — привычка упрощена"
    TipKind.GROW_HABIT -> "Готово — планка поднята"
    TipKind.TASK_INFO -> "Взял! Задача добавлена"
    else -> "Взял!"
}

@Composable
private fun KindChip(kind: TipKind) {
    Surface(color = Q.surfaceAlt, shape = CHIP_SHAPE) {
        Text(
            chipLabel(kind),
            style = MaterialTheme.typography.labelMedium,
            color = Q.inkMuted,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

private fun chipLabel(kind: TipKind): String = when (kind) {
    TipKind.START_HABIT -> "Новая привычка"
    TipKind.SHRINK_HABIT -> "Упростить"
    TipKind.ANCHOR_HABIT -> "Закрепить"
    TipKind.GROW_HABIT -> "Усложнить"
    TipKind.FINANCE -> "Деньги"
    TipKind.TASK_INFO -> "Шаг"
}
