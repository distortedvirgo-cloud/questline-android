package com.questline.app.ui.money

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.questline.app.ui.theme.Q

/**
 * Хранение «текущего баланса»: пользователь один раз указывает, сколько
 * денег у него сейчас; дальше баланс считается как указанное значение
 * плюс чистое движение всех операций, внесённых после этого момента.
 */
object BalancePrefs {
    private const val PREFS = "balance_prefs"
    private const val KEY_VALUE = "valueMinor"
    private const val KEY_ANCHOR = "anchorMillis"

    fun isSet(ctx: android.content.Context) = prefs(ctx).getLong(KEY_ANCHOR, 0L) > 0L
    fun valueMinor(ctx: android.content.Context) = prefs(ctx).getLong(KEY_VALUE, 0L)
    fun anchorMillis(ctx: android.content.Context) = prefs(ctx).getLong(KEY_ANCHOR, 0L)

    fun save(ctx: android.content.Context, valueMinor: Long) {
        prefs(ctx).edit()
            .putLong(KEY_VALUE, valueMinor)
            .putLong(KEY_ANCHOR, System.currentTimeMillis())
            .apply()
    }

    /** Программная установка баланса (например, актуальным остатком из пуша банка). */
    fun setValue(ctx: android.content.Context, valueMinor: Long, anchorMillis: Long = System.currentTimeMillis()) {
        prefs(ctx).edit()
            .putLong(KEY_VALUE, valueMinor)
            .putLong(KEY_ANCHOR, anchorMillis)
            .apply()
    }

    private fun prefs(ctx: android.content.Context) =
        ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
}

/**
 * Существующий диалог правки общего баланса. Большая карточка «Текущий баланс»
 * удалена из композиции — диалог переиспользуется компактной строкой баланса
 * (MoneyAccountsHeader в AccountsBar.kt).
 */
@Composable
internal fun BalanceEditDialog(currentValue: Long?, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var text by remember { mutableStateOf(currentValue?.let { MoneyFormat.text(it).replace(" ₽", "").replace('\u20BD', ' ').trim() } ?: "") }
    val parsed = MoneyFormat.parseRubles(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сколько денег сейчас?") },
        text = {
            Column {
                Text(
                    "Укажи актуальный остаток. Все операции, внесённые после этого момента, будут вычитаться/прибавляться автоматически.",
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
            }
        },
        confirmButton = {
            TextButton(enabled = parsed != null, onClick = { parsed?.let(onSave) }) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
