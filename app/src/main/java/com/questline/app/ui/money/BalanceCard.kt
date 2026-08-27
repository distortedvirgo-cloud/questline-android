package com.questline.app.ui.money

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.questline.app.data.AppRepo
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

    private fun prefs(ctx: android.content.Context) =
        ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
}

/** Карточка «Текущий баланс» вверху вкладки Деньги. Тап — указать актуальную сумму. */
@Composable
fun BalanceCard(repo: AppRepo) {
    val context = LocalContext.current
    var showEdit by remember { mutableStateOf(false) }
    var isSet by remember { mutableStateOf(BalancePrefs.isSet(context)) }
    var displayMinor by remember { mutableStateOf(0L) }

    LaunchedEffect(isSet) {
        if (!isSet) return@LaunchedEffect
        displayMinor = BalancePrefs.valueMinor(context)
        repo.txns.observeNetSince(BalancePrefs.anchorMillis(context)).collect { net ->
            displayMinor = BalancePrefs.valueMinor(context) + net
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Q.accentSoft, RoundedCornerShape(16.dp))
            .border(1.dp, Q.accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable { showEdit = true }
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(
            "Текущий баланс",
            style = MaterialTheme.typography.labelMedium,
            color = Q.inkMuted,
        )
        if (isSet) {
            Text(
                MoneyFormat.text(displayMinor),
                style = MaterialTheme.typography.displaySmall,
                fontFamily = FontFamily.Monospace,
            )
        } else {
            Text(
                "Укажи, сколько у тебя сейчас денег",
                style = MaterialTheme.typography.bodyMedium,
                color = Q.accent,
            )
        }
    }

    if (showEdit) {
        BalanceEditDialog(
            currentValue = if (isSet) displayMinor else null,
            onDismiss = { showEdit = false },
            onSave = { newMinor ->
                BalancePrefs.save(context, newMinor)
                isSet = true
                showEdit = false
            },
        )
    }
}

@Composable
private fun BalanceEditDialog(currentValue: Long?, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
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
