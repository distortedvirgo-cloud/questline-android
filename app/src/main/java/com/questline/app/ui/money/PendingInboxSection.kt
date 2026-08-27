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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.questline.app.notify.BankParser
import com.questline.app.notify.BankPrefs
import com.questline.app.data.AppRepo
import com.questline.app.data.Category
import com.questline.app.data.PendingTxn
import com.questline.app.data.Txn
import com.questline.app.ui.theme.Q
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/**
 * Инбокс банковских пушей: карточки PENDING с подтверждением в транзакцию.
 * Появляется над контентом вкладки «Обзор», когда есть неразобранные операции.
 */
@Composable
fun PendingInboxSection(repo: AppRepo) {
    val vm: PendingInboxViewModel = viewModel(key = "pending_inbox", factory = pendingInboxFactory(repo))
    val pending by vm.pending.collectAsStateWithLifecycle()
    val categories by vm.financeCategories.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (pending.isEmpty()) {
        // Молчаливый отказ — худший сценарий: пуш не пришёл и пользователь не знает,
        // что доступ к уведомлениям не выдан. Показываем подсказку только тогда,
        // когда автоучёт включён, а слушатель запрещён системой.
        val granted = remember {
            androidx.core.app.NotificationManagerCompat
                .getEnabledListenerPackages(context).contains(context.packageName)
        }
        if (BankPrefs.isEnabled(context) && !granted) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .background(Q.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, Q.border, RoundedCornerShape(16.dp))
                    .padding(14.dp),
            ) {
                Text("Пуши Сбера не попадут во входящие: не выдан доступ к уведомлениям.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }) { Text("Открыть настройки") }
            }
        }
        return
    }

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
                onConfirm = { categoryId ->
                    scope.launch {
                        if (item.type == "INCOME") {
                            // Доход подтверждается без пикера: категория уже выбрана в карточке.
                            // Вставляем напрямую, чтобы порядок был строго «сначала Txn,
                            // потом обновление баланса» (см. updateBalanceFromPush).
                            repo.txns.insert(
                                Txn(
                                    amountMinor = item.amountMinor,
                                    type = "INCOME",
                                    categoryId = categoryId,
                                    epochDay = Instant.ofEpochMilli(item.receivedMillis)
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDate()
                                        .toEpochDay(),
                                    note = item.title.ifBlank { "Из уведомления" },
                                    source = "BANK_PUSH",
                                    pendingId = item.id,
                                    createdAtMillis = System.currentTimeMillis(),
                                ),
                            )
                            repo.pending.setStatus(item.id, "CONFIRMED")
                        } else {
                            vm.confirm(item, categoryId)
                        }
                        updateBalanceFromPush(context, item)
                    }
                },
                onDiscard = { vm.discard(item) },
            )
        }
    }
}

/**
 * Пуш часто несёт актуальный остаток («Баланс: 548,04 ₽») — обновляем им
 * текущий баланс приложения. Вызывается ПОСЛЕ вставки Txn: якорь баланса
 * ставится позже createdAt транзакции, поэтому подтверждённая операция
 * не учитывается в движении дважды.
 */
private fun updateBalanceFromPush(context: android.content.Context, item: PendingTxn) {
    val linked = if (item.title.isBlank()) item.text else item.title + "\n" + item.text
    val balanceMinor = BankParser.parse(linked)?.balanceMinor ?: return
    val now = System.currentTimeMillis()
    val account = AccountsPrefs.findByLast4(context, item.text)
    if (account != null) {
        AccountsPrefs.upsertBalance(context, account.id, balanceMinor, now)
    } else {
        BalancePrefs.setValue(context, balanceMinor, now)
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
    val isIncome = item.type == "INCOME"
    // Доход не спрашивает категорию: первая INCOME-категория, иначе любая финансовая (kind != QUEST).
    val incomeCategoryId = remember(categories) {
        categories.firstOrNull { it.isIncome }?.id ?: categories.firstOrNull()?.id
    }
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
                color = if (isIncome) Q.success else Color.Unspecified,
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
            if (isIncome) {
                TextButton(
                    enabled = incomeCategoryId != null,
                    onClick = { incomeCategoryId?.let(onConfirm) },
                ) { Text("Подтвердить доход") }
            } else {
                TextButton(onClick = { showPicker = true }) { Text("Подтвердить") }
            }
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
