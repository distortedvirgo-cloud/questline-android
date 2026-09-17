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
    var autoEnabled by remember { mutableStateOf(AutoProcessPrefs.isEnabled(context)) }
    // Число попыток пакетного AI-разбора на карточку (модель иногда пропускает
    // пункты — даём вторую волну, но не спамим бесконечно).
    val aiAttempted = remember { HashMap<Long, Int>() }
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

    // Пакетный AI-разбор новых карточек: один запрос, хронология с временем.
    // Перезапускается только при изменении состава/статусов очереди.
    val pendingKey = pending.joinToString("|") { "${it.id}:${it.status}" }
    LaunchedEffect(autoEnabled, pendingKey, categories) {
        if (!autoEnabled || pending.isEmpty()) return@LaunchedEffect
        val fresh = pending.filter { it.type != "RECONCILE" && (aiAttempted[it.id] ?: 0) < 2 }
        if (fresh.isEmpty()) return@LaunchedEffect
        fresh.forEach { aiAttempted[it.id] = (aiAttempted[it.id] ?: 0) + 1 }
        android.util.Log.d("AiAuto", "batch: ${fresh.size} cards, enabled=$autoEnabled")
        val decisions = aiAutoProcess(context, fresh, categories)
        android.util.Log.d("AiAuto", "decisions: " + decisions.joinToString { "${it.pendingId}->${it.action}:${it.categoryId}" })
        if (decisions.isEmpty()) return@LaunchedEffect
        decisions.forEach { d ->
            android.util.Log.d("AiAuto", "apply ${d.pendingId}: ${d.action}")
            val item = fresh.firstOrNull { it.id == d.pendingId } ?: return@forEach
            when (d.action) {
                "confirm" -> {
                    val categoryId = d.categoryId
                    if (categoryId != null) {
                        val last4 = AccountsPrefs.findByLast4(context, item.text)?.last4
                        // Процесс мог умереть между insert и setStatus — тогда txn уже
                        // есть и повторная вставка дала бы дубль.
                        val alreadyThere = repo.txns.countByPendingId(item.id) > 0
                        if (!alreadyThere) {
                        repo.txns.insert(
                            Txn(
                                amountMinor = item.amountMinor,
                                type = item.type,
                                categoryId = categoryId,
                                epochDay = item.epochDay,
                                note = listOf(item.title.ifBlank { "Из уведомления" }, d.note)
                                    .filterNotNull().joinToString(" · "),
                                source = "BANK_PUSH",
                                pendingId = item.id,
                                accountLast4 = last4,
                                createdAtMillis = System.currentTimeMillis(),
                            ),
                        )
                        }
                        repo.pending.setStatus(item.id, "CONFIRMED")
                        detectAndUpdateBalance(context, repo, item)
                    }
                }
                "discard" -> repo.pending.setStatus(item.id, "DISCARDED")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Входящие операции",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = autoEnabled,
                onClick = {
                    AutoProcessPrefs.setEnabled(context, !autoEnabled)
                    autoEnabled = !autoEnabled
                },
                label = { Text(if (autoEnabled) "✨ Авто" else "Авто выкл") },
            )
        }
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
                            // Атрибуция карте-источнику — вычисляем ДО вставки Txn.
                            val last4 = AccountsPrefs.findByLast4(context, item.text)?.last4
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
                                        accountLast4 = last4,
                                        createdAtMillis = System.currentTimeMillis(),
                                    ),
                                )
                                repo.pending.setStatus(item.id, "CONFIRMED")
                            } else {
                                vm.confirm(item, categoryId, last4)
                            }
                            val unexplained = detectAndUpdateBalance(context, repo, item)
                            if (unexplained != null &&
                                pending.none { it.type == "RECONCILE" && it.text == item.text }
                            ) {
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

