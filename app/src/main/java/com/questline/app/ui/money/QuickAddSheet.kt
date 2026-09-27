package com.questline.app.ui.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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

    fun save(
        type: String,
        categoryId: Long,
        amountMinor: Long,
        note: String,
        accountLast4: String?,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            repo.txns.insert(
                Txn(
                    amountMinor = amountMinor,
                    type = type,
                    categoryId = categoryId,
                    epochDay = AppRepo.todayEpochDay,
                    note = note.trim(),
                    source = "MANUAL",
                    accountLast4 = accountLast4,
                    createdAtMillis = System.currentTimeMillis(),
                ),
            )
            onDone()
        }
    }
}

/**
 * Быстрый ввод: сумма, категория (FINANCE), Расход/Доход, необязательная заметка.
 * Сумма вводится в рублях → копейки внутри. [onSaved] отдаёт готовый текст
 * «Записано: −100 ₽ · Продукты» для снекбара на MoneyScreen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickAddSheet(
    onDismissRequest: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val vm: QuickAddViewModel = viewModel { QuickAddViewModel(AppRepo.get(context)) }
    val haptics = LocalHapticFeedback.current

    val financeCategories by vm.categories.collectAsState()

    var isExpense by remember { mutableStateOf(true) }
    var amountText by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var noteText by remember { mutableStateOf("") }
    val accounts = remember { AccountsPrefs.list(context) }
    // Одна карта — атрибутируем автоматически; иначе выбор чипом (необязательный).
    var accountLast4 by remember { mutableStateOf(accounts.singleOrNull()?.last4) }
    // Панель «не терять ввод» вместо диалога: BACK и тап мимо ловятся здесь же
    var confirmDiscard by remember { mutableStateOf(false) }

    val amountMinor = MoneyFormat.parseRubles(amountText)
    val canSave = selectedCategoryId != null && amountMinor != null && amountMinor > 0L
    // Грязная форма: сумма или заметка непустые (исходные пусты — непустое ввода ≠ исходного)
    val isDirty = amountText.isNotBlank() || noteText.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = { if (isDirty) confirmDiscard = true else onDismissRequest() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        // BACK перехватываем ВНУТРИ окна шторки, раньше её predictive-back:
        // иначе API 35 прячет шторку молча, а onDismissRequest срабатывает уже после.
        BackHandler(enabled = true) {
            when {
                confirmDiscard -> confirmDiscard = false
                isDirty -> confirmDiscard = true
                else -> onDismissRequest()
            }
        }
        if (confirmDiscard) {
            // Подтверждение — сменой содержимого шторки, без окна поверх неё
            DiscardPanel(
                onContinue = { confirmDiscard = false },
                onDiscard = onDismissRequest,
            )
            return@ModalBottomSheet
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
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

            // Привязка к карте: показывается, только если карт не ровно одна
            // (тогда запись относится к единственной карте автоматически).
            if (accounts.size != 1) {
                Text("Карта", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FilterChip(
                        selected = accountLast4 == null,
                        onClick = { accountLast4 = null },
                        label = { Text("Без карты") },
                    )
                    accounts.forEach { card ->
                        FilterChip(
                            selected = accountLast4 == card.last4,
                            onClick = { accountLast4 = card.last4 },
                            label = { Text("💳 ••${card.last4}") },
                        )
                    }
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
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    val sign = if (isExpense) "−" else "+"
                    val categoryName = financeCategories
                        .firstOrNull { it.id == selectedCategoryId }
                        ?.name.orEmpty()
                    val message = "Записано: $sign${MoneyFormat.text(amountMinor!!)} · $categoryName"
                    vm.save(
                        type = if (isExpense) "EXPENSE" else "INCOME",
                        categoryId = selectedCategoryId!!,
                        amountMinor = amountMinor!!,
                        note = noteText,
                        accountLast4 = accountLast4,
                        onDone = {
                            onSaved(message)
                            onDismissRequest()
                        },
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

/** Панель «Не сохранять?» — содержимое шторки, не окно поверх неё */
@Composable
private fun DiscardPanel(
    onContinue: () -> Unit,
    onDiscard: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 16.dp)
            .padding(top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Не сохранять?", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "Введённое пропадёт",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onDiscard,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(52.dp),
        ) { Text("Закрыть без сохранения") }
        TextButton(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Продолжить ввод") }
    }
}
