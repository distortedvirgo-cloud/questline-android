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
import kotlin.math.abs
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
    val accounts = remember { AccountsPrefs.list(context) }

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
            if (item.type == "RECONCILE") {
                ReconcileCard(
                    item = item,
                    accounts = accounts,
                    onResolved = {
                        // Ничего дополнительно не нужно: список pending — StateFlow,
                        // обновится сам после setStatus внутри карточки.
                    },
                    onDiscardLike = { vm.discard(item) },
                )
            } else {
                PendingCard(
                    item = item,
                    categories = categories,
                    onConfirm = { categoryId ->
                        scope.launch {
                            if (item.type == "INCOME") {
                                // Доход подтверждается без пикера: категория уже выбрана в карточке.
                                // Вставляем напрямую, чтобы порядок был строго «сначала Txn,
                                // потом обновление баланса» (см. detectAndUpdateBalance).
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
                            val unexplained = detectAndUpdateBalance(context, repo, item)
                            if (unexplained != null &&
                                pending.none { it.type == "RECONCILE" && it.text == item.text }
                            ) {
                                val last4 = AccountsPrefs.findByLast4(context, item.text)?.last4
                                if (last4 != null) {
                                    repo.pending.insert(
                                        PendingTxn(
                                            bankPackage = item.bankPackage,
                                            title = "⚖ Сверка ••" + last4,
                                            text = item.text,
                                            amountMinor = unexplained,
                                            type = "RECONCILE",
                                            epochDay = item.epochDay,
                                            receivedMillis = System.currentTimeMillis(),
                                        ),
                                    )
                                }
                            }
                        }
                    },
                    onDiscard = { vm.discard(item) },
                )
            }
        }
    }
}

/**
 * Пуш часто несёт актуальный остаток («Баланс: 548,04 ₽») — обновляем им
 * баланс карты-источника. Вызывается ПОСЛЕ вставки Txn: якорь баланса
 * ставится позже createdAt транзакции, поэтому подтверждённая операция
 * не учитывается в движении дважды.
 *
 * Заодно детектим расхождение: разница между пришедшим остатком и ожидаемым
 * может не объясняться подтверждаемой операцией (переводы внутри приложения
 * банка пуши не порождают). Такая необъяснённая дельта возвращается со знаком —
 * вызывающий код заводит карточку-сверку.
 */
private fun detectAndUpdateBalance(
    context: android.content.Context,
    repo: AppRepo,
    item: PendingTxn,
): Long? {
    val linked = item.title + "\n" + item.text
    val balanceMinor = BankParser.parse(linked)?.balanceMinor ?: return null
    val now = System.currentTimeMillis()
    val account = AccountsPrefs.findByLast4(context, item.text)
    if (account == null) {
        // Легаси-путь: карта не заведена — глобальный баланс из пуша.
        BalancePrefs.setValue(context, balanceMinor, now)
        return null
    }
    val expected = account.balanceMinor
    val delta = balanceMinor - expected
    val explained = if (item.type == "INCOME") item.amountMinor else -item.amountMinor
    val unexplained = delta - explained
    // Источник всегда синхронизируем пришедшим остатком — даже без расхождения.
    AccountsPrefs.upsertBalance(context, account.id, balanceMinor, now)
    // Нескалиброванную карту с нулевым балансом не проверяем;
    // |дельта| < 1 ₽ считаем копеечным шумом.
    return if (abs(unexplained) >= 100 && expected != 0L) unexplained else null
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

/**
 * Карточка-сверка: пуш принёс остаток, который не объясняется подтверждёнными
 * операциями (обычно перевод между своими картами). Три исхода: расписать дельту
 * как доход/расход по категории, перенести на другую свою карту или признать
 * «так и было». Баланс карты-источника уже синхронизирован при создании карточки.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReconcileCard(
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
