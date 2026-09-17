package com.questline.app.ui.money

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
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
import com.questline.app.notify.BankParser
import com.questline.app.data.AppRepo
import com.questline.app.data.Category
import com.questline.app.data.PendingTxn
import com.questline.app.data.Txn
import com.questline.app.ui.theme.Q
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Карточка-сверка: пуш принёс остаток, который не объясняется подтверждёнными
 * операциями (обычно перевод между своими картами). Три исхода: расписать дельту
 * как доход/расход по категории, перенести на другую свою карту или признать
 * «так и было». Баланс карты-источника уже синхронизирован при создании карточки.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReconcileCard(
    item: PendingTxn,
    accounts: List<Account>,
    onResolved: () -> Unit,
    onDiscardLike: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { AppRepo.get(context) }
    val scope = rememberCoroutineScope()
    val categories by remember { repo.categories.observeFinance() }.collectAsState(initial = emptyList())
    var showPicker by remember { mutableStateOf(false) }
    var showTransfer by remember { mutableStateOf(false) }
    // last4 карты-источника — из заголовка карточки («⚖ Сверка ••5129»).
    val sourceLast4 = accounts.firstOrNull { item.title.contains("••" + it.last4) }?.last4.orEmpty()
    val positive = item.amountMinor > 0
    val pushedBalance = remember(item.title, item.text) {
        BankParser.parse(item.title + "\n" + item.text)?.balanceMinor
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Q.accentSoft, RoundedCornerShape(16.dp))
            .border(1.dp, Q.accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Text(item.title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Баланс изменился на " + MoneyFormat.text(item.amountMinor),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Пришёл пуш с балансом, который не объясняется подтверждёнными операциями. " +
                "Обычно так проявляются переводы внутри приложения банка.",
            style = MaterialTheme.typography.bodySmall,
            color = Q.inkMuted,
        )
        pushedBalance?.let { pushed ->
            Spacer(Modifier.height(4.dp))
            Text(
                "Остаток из пуша: " + MoneyFormat.text(pushed),
                style = MaterialTheme.typography.labelSmall,
                color = Q.inkMuted,
            )
        }
        Spacer(Modifier.height(8.dp))
        // FlowRow: на узком экране кнопки переносятся на следующую строку.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { showPicker = true }) {
                Text(if (positive) "Это доход" else "Это расход")
            }
            TextButton(onClick = { showTransfer = true }) { Text("Перевод своей") }
            TextButton(onClick = {
                scope.launch {
                    repo.pending.setStatus(item.id, "CONFIRMED")
                    onResolved()
                }
            }) { Text("Так и было") }
            TextButton(onClick = onDiscardLike) { Text("Отбросить", color = Q.inkMuted) }
        }
    }

    if (showPicker) {
        var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(if (positive) "Категория дохода" else "Категория расхода") },
            text = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategoryId == cat.id,
                            onClick = { selectedCategoryId = cat.id },
                            label = { Text("${cat.emoji} ${cat.name}") },
                        )
                    }
                }
            },
            confirmButton = {
                val categoryId = selectedCategoryId
                TextButton(
                    enabled = categoryId != null,
                    onClick = {
                        showPicker = false
                        if (categoryId != null) {
                            scope.launch {
                                repo.txns.insert(
                                    Txn(
                                        amountMinor = abs(item.amountMinor),
                                        type = if (positive) "INCOME" else "EXPENSE",
                                        categoryId = categoryId,
                                        epochDay = item.epochDay,
                                        note = "Сверка ••" + sourceLast4,
                                        source = "BANK_PUSH",
                                        pendingId = item.id,
                                        accountLast4 = sourceLast4.ifEmpty { null },
                                        createdAtMillis = System.currentTimeMillis(),
                                    ),
                                )
                                repo.pending.setStatus(item.id, "CONFIRMED")
                                onResolved()
                            }
                        }
                    },
                ) { Text("Подтвердить") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Отмена") }
            },
        )
    }

    if (showTransfer) {
        // Другие карты: чьё «••last4» не упомянуто в заголовке карточки-сверки.
        val others = accounts.filter { !item.title.contains("••" + it.last4) }
        AlertDialog(
            onDismissRequest = { showTransfer = false },
            title = { Text("Перевод на свою карту") },
            text = {
                Column {
                    if (others.isEmpty()) {
                        Text(
                            "Других карт не заведено.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Q.inkMuted,
                        )
                    }
                    others.forEach { target ->
                        TextButton(onClick = {
                            showTransfer = false
                            scope.launch {
                                // Читаем карту заново: между открытием диалога и тапом
                                // баланс мог обновиться более свежим пушем.
                                val fresh = AccountsPrefs.list(context)
                                    .firstOrNull { it.id == target.id } ?: return@launch
                                // Ушли с источника (amountMinor < 0) — на целике прибавляем,
                                // пришли на источник — списываем: знак целика = −amountMinor.
                                AccountsPrefs.upsertBalance(
                                    context,
                                    fresh.id,
                                    fresh.balanceMinor - item.amountMinor,
                                    System.currentTimeMillis(),
                                )
                                repo.pending.setStatus(item.id, "CONFIRMED")
                                onResolved()
                            }
                        }) {
                            Text("${target.name} ••${target.last4} · ${MoneyFormat.text(target.balanceMinor)}")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTransfer = false }) { Text("Отмена") }
            },
        )
    }
}
