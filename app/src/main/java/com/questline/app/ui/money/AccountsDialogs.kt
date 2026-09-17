package com.questline.app.ui.money

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.questline.app.ui.theme.Q

/** Диалог правки баланса карты: ввод рублей, ✕ в заголовке — удалить карту. Есть режим «Перевод своей». */
@Composable
internal fun CardBalanceDialog(
    account: Account,
    onDismiss: () -> Unit,
    onSave: (newMinor: Long, transferTarget: Account?) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var text by remember(account.id) { mutableStateOf(rublesInput(account.balanceMinor)) }
    var confirmDelete by remember(account.id) { mutableStateOf(false) }
    val parsed = MoneyFormat.parseRubles(text)

    // Другие карты для режима «Перевод своей» (пусто — режим не показываем вовсе).
    val others = remember(account.id) {
        AccountsPrefs.list(context).filter { it.id != account.id }
    }
    var transferMode by remember(account.id) { mutableStateOf(false) }
    var targetId by remember(account.id) { mutableStateOf<Long?>(null) }
    val target = others.firstOrNull { it.id == targetId }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить карту?") },
            text = {
                Text("Карта и её остаток исчезнут из сводки. Действие необратимо.")
            },
            confirmButton = {
                TextButton(onClick = onDelete) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Отмена") }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = buildString {
                        if (account.name.isNotBlank()) {
                            append(account.name.trim())
                            append(" ")
                        }
                        append("••")
                        append(account.last4)
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Удалить карту",
                        tint = Q.accent,
                    )
                }
            }
        },
        text = {
            Column {
                Text(
                    "Укажи актуальный остаток по карте. Остаток из банковского пуша обновит его автоматически.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Q.inkMuted,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text("Сумма, ₽") },
                )
                if (others.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectableChip(
                            text = "Обновить",
                            selected = !transferMode,
                            onClick = { transferMode = false },
                            modifier = Modifier.weight(1f),
                        )
                        SelectableChip(
                            text = "Перевод своей",
                            selected = transferMode,
                            onClick = { transferMode = true },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (transferMode && others.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    others.forEach { candidate ->
                        SelectableChip(
                            text = "\uD83D\uDCB3 ••${candidate.last4}, остаток " +
                                MoneyFormat.text(candidate.balanceMinor),
                            selected = targetId == candidate.id,
                            onClick = {
                                targetId = if (targetId == candidate.id) null else candidate.id
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        "Разница между старым и новым остатком уйдёт на выбранную карту (или с неё), обе сойдутся.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Q.inkMuted,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null && !(transferMode && target == null),
                onClick = {
                    parsed?.let { minor -> onSave(minor, if (transferMode) target else null) }
                },
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

/**
 * Локальная копия SelectableChip из ui/tasks/AddTaskDialog.kt (тот internal и живёт
 * в другом пакете): нейтральная пилюля выбора, выбранная — surfaceAlt без второго цвета.
 */
@Composable
private fun SelectableChip(
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

/** Диалог добавления карты/счёта: имя (необязательно), 4 цифры (обязательно), баланс (необязательно). */
@Composable
internal fun AddCardDialog(
    prefillBalanceMinor: Long? = null,
    onDismiss: () -> Unit,
    onAdd: (name: String, last4: String, balanceMinor: Long?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var last4 by remember { mutableStateOf("") }
    var balance by remember(prefillBalanceMinor) {
        mutableStateOf(prefillBalanceMinor?.let(::rublesInput) ?: "")
    }
    val balanceParsed = MoneyFormat.parseRubles(balance)
    val last4Valid = last4.length == 4

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая карта/счёт") },
        text = {
            Column {
                Text(
                    "Последние 4 цифры нужны, чтобы распознавать остаток в банковских пушах.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Q.inkMuted,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Название (необязательно)") },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = last4,
                    onValueChange = { input ->
                        if (input.length <= 4 && input.all(Char::isDigit)) last4 = input
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("Последние 4 цифры") },
                    supportingText = { Text("Например, 5129 из «•• 5129»") },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = balance,
                    onValueChange = { balance = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text("Текущий баланс, ₽ (необязательно)") },
                )
                if (prefillBalanceMinor != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Перенесли текущий баланс — правь под реальный остаток",
                        style = MaterialTheme.typography.bodySmall,
                        color = Q.inkMuted,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = last4Valid,
                onClick = {
                    onAdd(
                        name.trim(),
                        last4,
                        if (balance.isBlank()) null else balanceParsed,
                    )
                },
            ) { Text("Добавить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

/** «548,04 ₽» → «548,04» — стартовое значение поля ввода рублей. */
private fun rublesInput(minor: Long): String =
    MoneyFormat.text(minor).replace(" ₽", "").replace('\u20BD', ' ').trim()
