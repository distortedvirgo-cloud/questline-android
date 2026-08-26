package com.questline.app.ui.money

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.questline.app.data.AppRepo
import com.questline.app.data.Category
import com.questline.app.data.PendingTxn
import com.questline.app.ui.theme.Q
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Инбокс банковских пушей: карточки PENDING с подтверждением в транзакцию.
 * Появляется над контентом вкладки «Обзор», когда есть неразобранные операции.
 */
@Composable
fun PendingInboxSection(repo: AppRepo) {
    val vm: PendingInboxViewModel = viewModel(key = "pending_inbox", factory = pendingInboxFactory(repo))
    val pending by vm.pending.collectAsStateWithLifecycle()
    val categories by vm.financeCategories.collectAsStateWithLifecycle()

    if (pending.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        Text(
            "Входящие операции",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(6.dp))
        pending.forEach { item ->
            PendingCard(
                item = item,
                categories = categories,
                onConfirm = { categoryId -> vm.confirm(item, categoryId) },
                onDiscard = { vm.discard(item) },
            )
        }
    }
}

@Composable
private fun PendingCard(
    item: PendingTxn,
    categories: List<Category>,
    onConfirm: (Long) -> Unit,
    onDiscard: () -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val time = remember(item.receivedMillis) {
        Instant.ofEpochMilli(item.receivedMillis).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Q.accentSoft, RoundedCornerShape(16.dp))
            .border(1.dp, Q.accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (item.type == "INCOME") "↑ Доход" else "↓ Расход",
                style = MaterialTheme.typography.labelMedium,
                color = if (item.type == "INCOME") Q.success else Q.danger,
            )
            Spacer(Modifier.width(8.dp))
            Text(time, style = MaterialTheme.typography.labelSmall, color = Q.inkMuted)
            Spacer(Modifier.weight(1f))
            Text(
                MoneyFormat.text(item.amountMinor),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            item.text.take(120),
            style = MaterialTheme.typography.bodySmall,
            color = Q.inkMuted,
            maxLines = 2,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { showPicker = true }) { Text("Подтвердить") }
            TextButton(onClick = onDiscard) {
                Text("Отбросить", color = Q.inkMuted)
            }
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Категория расхода") },
            text = {
                Column {
                    categories.forEach { cat ->
                        TextButton(onClick = { showPicker = false; onConfirm(cat.id) }) {
                            Text("${cat.emoji} ${cat.name}")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Отмена") }
            },
        )
    }
}
