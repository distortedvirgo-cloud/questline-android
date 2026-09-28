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
import com.questline.app.domain.finance.findMirror
import com.questline.app.domain.finance.unexplainedDelta
import com.questline.app.ui.theme.Q
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlinx.coroutines.flow.first
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
    // Диалоги инбокса: запись перевода «между своими» (TRANSFER) и вопрос о
    // необъяснённой дельте баланса после подтверждения обычного пуша.
    var transferItem by remember { mutableStateOf<PendingTxn?>(null) }
    var deltaQuestion by remember { mutableStateOf<DeltaQuestion?>(null) }

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
        // TRANSFER подтверждает пользователь в диалоге «Перевод между своими» —
        // авто-разбор не должен записывать такие пуши в расход.
        val fresh = pending.filter {
            it.type != "RECONCILE" && it.type != "TRANSFER" && (aiAttempted[it.id] ?: 0) < 2
        }
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
            } else if (item.type == "TRANSFER") {
                // Перевод себе: подтверждение — диалог с выбором карт, Txn не создаётся.
                TransferPendingCard(
                    item = item,
                    onConfirm = { transferItem = item },
                    onDiscard = { vm.discard(item) },
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
                            if (unexplained != null && last4 != null) {
                                // Расхождение не объясняется операцией — спрашиваем
                                // пользователя (новые средства / перевод с другой карты).
                                // Карточку-сверку больше не заводим.
                                val card = AccountsPrefs.list(context).firstOrNull { it.last4 == last4 }
                                if (card != null) deltaQuestion = DeltaQuestion(card, unexplained)
                            }
                        }
                    },
                    onDiscard = { vm.discard(item) },
                )
            }
        }
    }

    // Диалог «Перевод между своими»: списание с одной карты и зачисление на другую
    // без создания транзакции — двигаем только балансы.
    transferItem?.let { item ->
        TransferBetweenOwnDialog(
            item = item,
            onDismiss = { transferItem = null },
            onRecord = { source, target ->
                scope.launch {
                    val now = System.currentTimeMillis()
                    val amount = abs(item.amountMinor)
                    AccountsPrefs.upsertBalance(
                        context, source.id, displayedBalance(repo, source) - amount, now,
                    )
                    AccountsPrefs.upsertBalance(
                        context, target.id, displayedBalance(repo, target) + amount, now,
                    )
                    // Пуш пишет last4 счёта, а карта заведена last4 карты — запоминаем
                    // соответствие, чтобы следующие пуши матчились сами.
                    val pushLast4s = extractPushLast4s(item.title + "\n" + item.text)
                    pushLast4s.getOrNull(0)?.takeIf { it != source.last4 }?.let {
                        AccountsPrefs.rememberAccountMap(context, it, source.last4)
                    }
                    pushLast4s.getOrNull(1)?.takeIf { it != target.last4 }?.let {
                        AccountsPrefs.rememberAccountMap(context, it, target.last4)
                    }
                    // Механизм тот же, что у обычного confirm: статус — и карточка уходит.
                    repo.pending.setStatus(item.id, "CONFIRMED")
                }
            },
        )
    }

    // Вопрос о необъяснённой дельте: новые средства, перевод с другой карты
    // или оставить метку до разбора позже.
    deltaQuestion?.let { q ->
        TransferQuestionDialog(
            cardLast4 = q.card.last4,
            unexplainedMinor = q.unexplained,
            others = AccountsPrefs.list(context).filter { it.id != q.card.id },
            onDismiss = { deltaQuestion = null },
            onNewMoney = {
                // Новые средства: метку не ставим, старую метку карты снимаем.
                AccountsPrefs.clearUnexplainedDelta(context, q.card.last4)
                deltaQuestion = null
            },
            onTransferFrom = { source ->
                scope.launch {
                    val now = System.currentTimeMillis()
                    AccountsPrefs.upsertBalance(
                        context, source.id, displayedBalance(repo, source) - q.unexplained, now,
                    )
                    // У источника могла остаться свежая зеркальная метка с его пуша —
                    // перевод её объясняет, снимаем.
                    val mirror = findMirror(
                        q.unexplained,
                        AccountsPrefs.unexplainedDeltas(context),
                        MIRROR_WINDOW_MS,
                        now,
                    )
                    if (mirror == source.last4) {
                        AccountsPrefs.clearUnexplainedDelta(context, source.last4)
                    }
                    deltaQuestion = null
                }
            },
            onKeepAsIs = {
                // Разбор позже: метка дельты пригодится при поиске зеркал.
                AccountsPrefs.putUnexplainedDelta(
                    context, q.card.last4, q.unexplained, System.currentTimeMillis(),
                )
                deltaQuestion = null
            },
        )
    }
}

/**
 * Пуш часто несёт актуальный остаток («Баланс: 548,04 ₽») — обновляем им
 * баланс карты-источника. Вызывается ПОСЛЕ вставки Txn: якорь баланса
 * ставится позже createdAt транзакции, поэтому подтверждённая операция
 * не учитывается в движении дважды.
 *
 * Заодно детектим расхождение: разница между пришедшим остатком, отображаемым
 * балансом и суммой только что подтверждённых записей карты может объясняться
 * переводом «между своими» (банк такие пуши не шлёт). Необъяснённая дельта
 * возвращается со знаком — вызывающий код показывает диалог-вопрос
 * (TransferQuestionDialog).
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
    // Отображаемый баланс на момент пуша: чистый поток после якоря здесь
    // недоступен (это Flow), для свежего пуша абсолют и есть отображаемый.
    val displayedMinor = account.balanceMinor
    // Сумма только что подтверждённых записей этой карты — сама операция из пуша.
    val confirmedNet = if (item.type == "INCOME") item.amountMinor else -item.amountMinor
    val unexplained = unexplainedDelta(balanceMinor, displayedMinor, confirmedNet)
    // Источник всегда синхронизируем пришедшим остатком — даже без расхождения.
    AccountsPrefs.upsertBalance(context, account.id, balanceMinor, now)
    // Нескалиброванную карту с нулевым балансом не проверяем;
    // |дельта| < 1 ₽ считаем копеечным шумом.
    return if (abs(unexplained) >= 100 && displayedMinor != 0L) unexplained else null
}

/**
 * Текущий отображаемый баланс карты: абсолют с якоря + чистый поток операций
 * после якоря — та же величина, что показывает AccountsBar.
 */
private suspend fun displayedBalance(repo: AppRepo, account: Account): Long =
    account.balanceMinor +
        repo.txns.observeNetForAccount(account.last4, account.anchorMillis).first()

/** Открытый вопрос о необъяснённой дельте баланса подтверждённой карты. */
private data class DeltaQuestion(val card: Account, val unexplained: Long)

/** Карточка TRANSFER-пуша: без категорий, подтверждение — диалог перевода. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransferPendingCard(
    item: PendingTxn,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
) {
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
                "⇄ Перевод своей",
                style = MaterialTheme.typography.labelMedium,
                color = Q.accent,
            )
            Spacer(Modifier.width(8.dp))
            Text(time, style = MaterialTheme.typography.labelSmall, color = Q.inkMuted)
            Spacer(Modifier.weight(1f))
            Text(
                MoneyFormat.text(abs(item.amountMinor)),
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onConfirm) { Text("Записать перевод") }
            TextButton(onClick = onDiscard) { Text("Отбросить", color = Q.inkMuted) }
        }
    }
}

