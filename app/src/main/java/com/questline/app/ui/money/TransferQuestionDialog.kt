package com.questline.app.ui.money

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.questline.app.data.PendingTxn
import com.questline.app.domain.finance.findMirror
import com.questline.app.ui.theme.Q
import kotlin.math.abs

/** «Свежая» зеркальная дельта — записанная не позже суток назад. */
internal const val MIRROR_WINDOW_MS = 24L * 60 * 60 * 1000

// Паттерны last4 из текста пуша — те же, что в AccountsPrefs.findByLast4
// (там они private), но с порядком следования: первый — источник, второй — цель.
private val PUSH_LAST4_PATTERNS = listOf(
    Regex("[•*·]\\s*(\\d{4})(?!\\d)"),
    Regex("(?:счёт|счет|карт[ауые])\\s*\\**\\s*(\\d{4})(?!\\d)", RegexOption.IGNORE_CASE),
)

/** Все last4 из текста пуша по порядку: «со счёта ••1205 … на счёт ••5129» → [1205, 5129]. */
internal fun extractPushLast4s(text: String): List<String> =
    PUSH_LAST4_PATTERNS
        .flatMap { regex ->
            regex.findAll(text).map { it.range.first to it.groupValues[1] }.toList()
        }
        .sortedBy { it.first }
        .map { it.second }
        .distinct()

/**
 * Карта по last4 из пуша: пуши пишут last4 СЧЁТА, а карты заведены last4 КАРТЫ —
 * прямое совпадение либо выученный маппинг AccountsPrefs.mapAccountToCard.
 */
private fun resolvePushAccount(context: Context, last4: String, accounts: List<Account>): Account? {
    val mapped = AccountsPrefs.mapAccountToCard(context, last4) ?: last4
    return accounts.firstOrNull { it.last4 == mapped }
}

/**
 * Диалог «Перевод между своими» для TRANSFER-пуша: два выпадающих списка карт,
 * источник предзаполнен сматченной по last4 картой. Запись делает вызывающий код
 * (без создания транзакции) через [onRecord]. Карт меньше двух — только «Отменить».
 */
@Composable
internal fun TransferBetweenOwnDialog(
    item: PendingTxn,
    onDismiss: () -> Unit,
    onRecord: (source: Account, target: Account) -> Unit,
) {
    val context = LocalContext.current
    val accounts = remember { AccountsPrefs.list(context) }
    if (accounts.size < 2) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Перевод между своими") },
            text = { Text("Добавь вторую карту, чтобы записать перевод.") },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("Отменить") } },
        )
        return
    }

    // Источник — карта по первому last4 из пуша, цель — по второму;
    // не сматчилось — берём первую отличную от источника.
    val matched = remember(item.id) {
        extractPushLast4s(item.title + "\n" + item.text)
            .mapNotNull { last4 -> resolvePushAccount(context, last4, accounts) }
            .distinctBy { it.id }
    }
    val initialSource = matched.getOrNull(0) ?: accounts.first()
    val initialTarget = matched.getOrNull(1)?.takeIf { it.id != initialSource.id }
        ?: accounts.first { it.id != initialSource.id }
    var sourceId by remember(item.id) { mutableStateOf(initialSource.id) }
    var targetId by remember(item.id) { mutableStateOf(initialTarget.id) }
    val source = accounts.first { it.id == sourceId }
    val target = accounts.first { it.id == targetId }
    val amount = abs(item.amountMinor)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Перевод между своими") },
        text = {
            Column {
                CardDropdown(
                    label = "Откуда",
                    selected = source,
                    options = accounts.filter { it.id != targetId },
                    onSelect = { sourceId = it.id },
                )
                Spacer(Modifier.height(10.dp))
                CardDropdown(
                    label = "Куда",
                    selected = target,
                    options = accounts.filter { it.id != sourceId },
                    onSelect = { targetId = it.id },
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Спишем ${MoneyFormat.text(amount)} с остатка источника и зачислим на цель. " +
                        "Транзакция не создаётся.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Q.inkMuted,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onRecord(source, target) }) { Text("Записать перевод") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

/** Выпадающий список карт: свёрнут — выбранная карта, развёрнут — остальные. */
@Composable
private fun CardDropdown(
    label: String,
    selected: Account,
    options: List<Account>,
    onSelect: (Account) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Q.inkMuted)
        Spacer(Modifier.height(4.dp))
        Box {
            Surface(
                onClick = { expanded = true },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, Q.border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "💳 ••${selected.last4}, остаток ${MoneyFormat.text(selected.balanceMinor)}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { candidate ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                "💳 ••${candidate.last4}, " +
                                    "остаток ${MoneyFormat.text(candidate.balanceMinor)}",
                            )
                        },
                        onClick = {
                            expanded = false
                            onSelect(candidate)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Вопрос о необъяснённой дельте после подтверждения пуша: баланс ••XXXX уехал
 * мимо операции — это новые средства или перевод с другой карты? Если у другой
 * карты есть свежая зеркальная метка, она выбрана по умолчанию.
 */
@Composable
internal fun TransferQuestionDialog(
    cardLast4: String,
    unexplainedMinor: Long,
    others: List<Account>,
    onDismiss: () -> Unit,
    onNewMoney: () -> Unit,
    onTransferFrom: (Account) -> Unit,
    onKeepAsIs: () -> Unit,
) {
    val context = LocalContext.current
    val mirrorLast4 = remember(cardLast4, unexplainedMinor) {
        findMirror(
            unexplainedMinor,
            AccountsPrefs.unexplainedDeltas(context),
            MIRROR_WINDOW_MS,
            System.currentTimeMillis(),
        )
    }
    var chosenId by remember(cardLast4, unexplainedMinor) {
        mutableStateOf(others.firstOrNull { it.last4 == mirrorLast4 }?.id)
    }
    val chosen = others.firstOrNull { it.id == chosenId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Баланс ••$cardLast4 изменился на ${MoneyFormat.text(unexplainedMinor)}") },
        text = {
            Column {
                Text(
                    "Это новые средства или перевод с другой карты?",
                    style = MaterialTheme.typography.bodySmall,
                    color = Q.inkMuted,
                )
                if (others.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    others.forEach { candidate ->
                        SelectableCardChip(
                            text = "💳 ••${candidate.last4}, " +
                                "остаток ${MoneyFormat.text(candidate.balanceMinor)}",
                            selected = chosenId == candidate.id,
                            onClick = {
                                chosenId = if (chosenId == candidate.id) null else candidate.id
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = chosen != null,
                onClick = { chosen?.let(onTransferFrom) },
            ) { Text("Перевод с карты…") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onNewMoney) { Text("Новые средства") }
                TextButton(onClick = onKeepAsIs) { Text("Оставить как есть") }
            }
        },
    )
}

/**
 * Локальная копия SelectableChip из AccountsDialogs.kt (тот private): нейтральная
 * пилюля выбора, выбранная — surfaceAlt без второго цвета.
 */
@Composable
private fun SelectableCardChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    textPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Q.surfaceAlt else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Q.border),
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = textStyle,
            color = if (selected) Q.ink else Q.inkMuted,
            maxLines = 1,
            modifier = Modifier.padding(textPadding),
        )
    }
}
