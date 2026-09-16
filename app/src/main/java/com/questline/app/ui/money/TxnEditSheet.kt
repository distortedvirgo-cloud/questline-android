package com.questline.app.ui.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.questline.app.data.AppRepo
import com.questline.app.data.Txn
import com.questline.app.ui.theme.Q
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Правка и удаление существующей транзакции (T-09) */
class TxnEditViewModel(private val repo: AppRepo) : ViewModel() {

    val categories = repo.categories.observeFinance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(txn: Txn, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.updateTxn(txn)
            onDone()
        }
    }

    fun delete(txn: Txn, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.deleteTxn(txn)
            onDone()
        }
    }
}

/**
 * Правка операции: сумма (цифровая клавиатура), тип расход/доход,
 * категория-чипы, день стрелками, заметка. Кнопка удаления с подтверждением.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TxnEditSheet(
    txn: Txn,
    onDismissRequest: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val vm: TxnEditViewModel = viewModel { TxnEditViewModel(AppRepo.get(context)) }

    val financeCategories by vm.categories.collectAsState()

    var isExpense by remember(txn.id) { mutableStateOf(txn.type == "EXPENSE") }
    var amountText by remember(txn.id) { mutableStateOf(amountToText(txn.amountMinor)) }
    var selectedCategoryId by remember(txn.id) { mutableStateOf(txn.categoryId) }
    var epochDay by remember(txn.id) { mutableLongStateOf(txn.epochDay) }
    var noteText by remember(txn.id) { mutableStateOf(txn.note) }
    var confirmDelete by remember { mutableStateOf(false) }

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
            Text("Правка операции", style = MaterialTheme.typography.titleLarge)

            // Расход / Доход — как в быстром вводе
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

            // Дата: свитчер дня стрелками (будущее запрещено)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { epochDay -= 1 }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Предыдущий день")
                }
                Text(
                    text = MoneyFormat.dayWords(epochDay),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { epochDay += 1 },
                    enabled = epochDay < AppRepo.todayEpochDay,
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Следующий день")
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
                        txn.copy(
                            amountMinor = amountMinor ?: txn.amountMinor,
                            type = if (isExpense) "EXPENSE" else "INCOME",
                            categoryId = selectedCategoryId ?: txn.categoryId,
                            epochDay = epochDay,
                            note = noteText.trim(),
                        ),
                        onDone = onDismissRequest,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) { Text("Сохранить") }

            TextButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Удалить операцию", color = Q.danger) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить операцию?") },
            text = { Text("Операция будет удалена безвозвратно.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(txn, onDone = onDismissRequest)
                }) { Text("Удалить", color = Q.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Отмена") }
            },
        )
    }
}

/** Сумма из копеек в редактируемый текст: «1200» или «1200,50» */
private fun amountToText(minor: Long): String {
    val rubles = minor / 100
    val kopecks = minor % 100
    return if (kopecks == 0L) rubles.toString()
    else "$rubles," + kopecks.toString().padStart(2, '0')
}
