package com.questline.app.ui.money

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.questline.app.data.AppRepo
import com.questline.app.data.Txn
import com.questline.app.ui.theme.Q

/**
 * Операции (T-09): все транзакции месяца, группировка по дням,
 * поиск по заметке/категории, фильтры типа, месяц стрелками.
 * Тап по строке — правка в TxnEditSheet.
 */
@Composable
fun OperationsScreen(onBack: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val vm: OperationsViewModel = viewModel { OperationsViewModel(AppRepo.get(context)) }

    val month by vm.month.collectAsState()
    val query by vm.query.collectAsState()
    val typeFilter by vm.typeFilter.collectAsState()
    val groups by vm.groups.collectAsState()

    var editing by remember { mutableStateOf<Txn?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Назад")
            }
            Text(
                text = "Операции",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
        }

        // Шапка месяца ‹ › — как на вкладке «Деньги»
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.shiftMonth(-1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Предыдущий месяц")
            }
            Text(
                text = MoneyFormat.monthTitle(month),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { vm.shiftMonth(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Следующий месяц")
            }
        }

        Spacer(Modifier.padding(2.dp))

        OutlinedTextField(
            value = query,
            onValueChange = vm::setQuery,
            label = { Text("Поиск: заметка или категория") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.padding(4.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            OperationTypeFilter.entries.forEach { filter ->
                FilterChip(
                    selected = typeFilter == filter,
                    onClick = { vm.setTypeFilter(filter) },
                    label = { Text(filter.label) },
                )
            }
        }

        Spacer(Modifier.padding(6.dp))

        if (groups.isEmpty()) {
            Text(
                text = if (query.isBlank()) "В этом месяце операций нет" else "Ничего не найдено",
                style = MaterialTheme.typography.bodyMedium,
                color = Q.inkMuted,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                groups.forEach { group ->
                    item(key = "day_${group.epochDay}") {
                        Text(
                            text = dayTitle(group.epochDay),
                            style = MaterialTheme.typography.labelMedium,
                            color = Q.inkMuted,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                    group.rows.forEach { row ->
                        item(key = "txn_${row.txn.id}") {
                            OperationItem(
                                row = row,
                                onClick = { editing = row.txn },
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { txn ->
        TxnEditSheet(
            txn = txn,
            onDismissRequest = { editing = null },
        )
    }
}

@Composable
private fun OperationItem(row: OperationRow, onClick: () -> Unit) {
    val isExpense = row.txn.type == "EXPENSE"
    val amountText = (if (isExpense) "\u2212" else "+") + MoneyFormat.text(row.txn.amountMinor)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(row.category?.emoji ?: "\uD83D\uDCE6", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.padding(4.dp))
        Column(Modifier.weight(1f)) {
            Text(row.title, style = MaterialTheme.typography.bodyLarge)
            if (row.txn.note.isNotBlank()) {
                Text(
                    text = row.txn.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = Q.inkMuted,
                )
            }
        }
        Text(
            text = amountText,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace,
            color = if (isExpense) Q.ink else Q.success,
        )
    }
}

/** Заголовок группы дня: «Сегодня», «Вчера» или «14 августа» */
private fun dayTitle(epochDay: Long): String {
    val today = AppRepo.todayEpochDay
    return when (epochDay) {
        today -> "Сегодня"
        today - 1 -> "Вчера"
        else -> MoneyFormat.dayWords(epochDay)
    }
}
