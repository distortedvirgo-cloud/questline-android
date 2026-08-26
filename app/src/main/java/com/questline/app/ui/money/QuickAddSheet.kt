package com.questline.app.ui.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.questline.app.data.AppRepo
import com.questline.app.data.Txn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Только загрузка FINANCE-категорий и сохранение транзакции */
class QuickAddViewModel(private val repo: AppRepo) : ViewModel() {

    val categories = repo.categories.observeFinance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(type: String, categoryId: Long, amountMinor: Long, note: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.txns.insert(
                Txn(
                    amountMinor = amountMinor,
                    type = type,
                    categoryId = categoryId,
                    epochDay = AppRepo.todayEpochDay,
                    note = note.trim(),
                    source = "MANUAL",
                    createdAtMillis = System.currentTimeMillis(),
                ),
            )
            onDone()
        }
    }
}

/**
 * Быстрый ввод: сумма, категория (FINANCE), Расход/Доход, необязательная заметка.
 * Сумма вводится в рублях → копейки внутри.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickAddSheet(onDismissRequest: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val vm: QuickAddViewModel = viewModel { QuickAddViewModel(AppRepo.get(context)) }

    val financeCategories by vm.categories.collectAsState()

    var isExpense by remember { mutableStateOf(true) }
    var amountText by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var noteText by remember { mutableStateOf("") }

    val amountMinor = MoneyFormat.parseRubles(amountText)
    val canSave = selectedCategoryId != null && amountMinor != null && amountMinor > 0L

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Быстрый ввод", style = MaterialTheme.typography.titleLarge)

            // Расход / Доход
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = isExpense,
                    onClick = { isExpense = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Расход") }
                SegmentedButton(
                    selected = !isExpense,
                    onClick = { isExpense = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Доход") }
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' } },
                label = { Text("Сумма") },
                suffix = { Text("\u20BD") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                isError = amountText.isNotBlank() && amountMinor == null,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Категория", style = MaterialTheme.typography.labelMedium)

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                financeCategories.forEach { category ->
                    FilterChip(
                        selected = selectedCategoryId == category.id,
                        onClick = { selectedCategoryId = category.id },
                        label = { Text("${category.emoji} ${category.name}") },
                    )
                }
            }

            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("Заметка (необязательно)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                enabled = canSave,
                onClick = {
                    vm.save(
                        type = if (isExpense) "EXPENSE" else "INCOME",
                        categoryId = selectedCategoryId!!,
                        amountMinor = amountMinor!!,
                        note = noteText,
                        onDone = onDismissRequest,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) { Text("Сохранить") }

            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Запись добавится сегодняшним днём",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
