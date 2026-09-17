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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PendingCard(
    item: PendingTxn,
    categories: List<Category>,
    onConfirm: (Long) -> Unit,
    onDiscard: () -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // AI-категория подбирается один раз на карточку; без ключа остаётся null —
    // карточка работает по-старому.
    var aiCategoryId by remember(item.id) { mutableStateOf<Long?>(null) }
    var aiTried by remember(item.id) { mutableStateOf(false) }
    // Категории грузятся из БД асинхронно: на первом composition список ещё пуст,
    // поэтому эффект перезапускается по categories, а aiTried не даёт дублировать запрос.
    LaunchedEffect(item.id, categories) {
        if (!aiTried && categories.isNotEmpty()) {
            aiTried = true
            val raw = listOf(item.title, item.text).filter { it.isNotBlank() }.joinToString(" ")
            aiCategoryId = aiSuggestCategoryId(context, raw, categories)
        }
    }
    val isIncome = item.type == "INCOME"
    // Доход не спрашивает категорию: AI-подбор, иначе первая INCOME-категория.
    val incomeCategoryId = remember(categories, aiCategoryId) {
        aiCategoryId ?: categories.firstOrNull { it.isIncome }?.id ?: categories.firstOrNull()?.id
    }
    val aiCategoryName = aiCategoryId?.let { id -> categories.firstOrNull { it.id == id }?.name }
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isIncome) {
                TextButton(
                    enabled = incomeCategoryId != null,
                    onClick = { incomeCategoryId?.let(onConfirm) },
                ) { Text("Подтвердить доход") }
            } else if (aiCategoryId != null) {
                // Категория подобрана ИИ: подтверждение одним тапом, «Своя…» — ручной пикер.
                TextButton(onClick = { aiCategoryId?.let(onConfirm) }) { Text("✨ $aiCategoryName") }
                TextButton(onClick = { showPicker = true }) { Text("Своя…", color = Q.inkMuted) }
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
