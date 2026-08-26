package com.questline.app.ui.money

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.questline.app.data.AppRepo
import com.questline.app.data.Category
import com.questline.app.domain.ProgressionEngine
import com.questline.app.ui.theme.Q
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Категория с бюджетом и потраченное за месяц */
data class BudgetRow(val category: Category, val spentMinor: Long) {
    val budgetMinor: Long get() = category.budgetMonthlyMinor ?: 0L

    /** Доля расходов 0..1+ для цвета статуса (не обрезаем: OVER виден цветом) */
    val fraction: Float
        get() = if (budgetMinor <= 0L) 0f else spentMinor.toFloat() / budgetMinor
}

class BudgetsViewModel(private val repo: AppRepo) : ViewModel() {

    private val _month = MutableStateFlow(LocalDate.now().withDayOfMonth(1))

    @OptIn(ExperimentalCoroutinesApi::class)
    val rows = _month.flatMapLatest { month ->
        val (from, to) = MoneyFormat.monthBounds(month)
        repo.categories.observeFinance().flatMapLatest { categories ->
            val budgeted = categories.filter { it.budgetMonthlyMinor != null }
            if (budgeted.isEmpty()) {
                flowOf(emptyList<BudgetRow>())
            } else {
                combine(
                    budgeted.map { category ->
                        repo.txns.observeSpentInCategory(category.id, from, to)
                            .map { spent -> BudgetRow(category, spent) }
                    },
                ) { it.toList() }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setMonth(month: LocalDate) {
        _month.value = month.withDayOfMonth(1)
    }

    /** Сохранить месячный бюджет категории (в копейках) */
    fun saveBudget(categoryId: Long, minor: Long) {
        viewModelScope.launch {
            repo.categories.setBudget(categoryId, minor)
        }
    }
}

/**
 * Бюджеты: FINANCE-категории с заданным месячным лимитом,
 * прогрессбар spent/budget c цветом статуса из ProgressionEngine.
 */
@Composable
fun BudgetsSection(month: LocalDate, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val vm: BudgetsViewModel = viewModel { BudgetsViewModel(AppRepo.get(context)) }

    LaunchedEffect(month) { vm.setMonth(month) }

    val rows by vm.rows.collectAsState()

    var editing by remember { mutableStateOf<Category?>(null) }

    SectionColumn(modifier = modifier) {
        if (rows.isEmpty()) {
            Text(
                text = "Бюджеты не заданы — задай месячный лимит,\nи категория появится здесь.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        rows.forEach { row ->
            BudgetRowCard(row = row, onEdit = { editing = row.category })
            Spacer(Modifier.height(8.dp))
        }
    }

    editing?.let { category ->
        EditBudgetDialog(
            category = category,
            onDismiss = { editing = null },
            onSave = { minor ->
                vm.saveBudget(category.id, minor)
                editing = null
            },
        )
    }
}

@Composable
private fun BudgetRowCard(row: BudgetRow, onEdit: () -> Unit) {
    // Цвет по доле бюджета: OK → success, WARN → warn, OVER → danger
    val statusColor = when (ProgressionEngine.budgetColor(row.fraction)) {
        ProgressionEngine.BudgetStatus.OK -> Q.success
        ProgressionEngine.BudgetStatus.WARN -> Q.warn
        ProgressionEngine.BudgetStatus.OVER -> Q.danger
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${row.category.emoji} ${row.category.name}", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Text(
                text = "${MoneyFormat.text(row.spentMinor)} / ${MoneyFormat.text(row.budgetMinor)}",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Изменить бюджет", tint = Q.inkMuted)
            }
        }

        val animatedFraction by animateFloatAsState(
            targetValue = row.fraction.coerceIn(0f, 1f),
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
            label = "budgetProgress",
        )
        LinearProgressIndicator(
            progress = { animatedFraction },
            color = statusColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
        )

        if (row.fraction >= 0.8f) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (row.fraction >= 1f) "Перерасход на ${MoneyFormat.text(row.spentMinor - row.budgetMinor)}"
                else "Осталось ${MoneyFormat.text(row.budgetMinor - row.spentMinor)}",
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
            )
        }
    }
}

@Composable
private fun EditBudgetDialog(
    category: Category,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit,
) {
    var text by remember {
        mutableStateOf(
            category.budgetMonthlyMinor?.let { (it / 100).toString() } ?: "",
        )
    }
    val minor = MoneyFormat.parseRubles(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Бюджет: ${category.emoji} ${category.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { input ->
                        text = input.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }
                    },
                    label = { Text("Лимит в месяц") },
                    suffix = { Text("\u20BD") },
                    singleLine = true,
                    isError = text.isNotBlank() && minor == null,
                )
                Text(
                    text = "Дробные суммы вводи через запятую",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = minor != null && minor > 0L,
                onClick = { onSave(minor ?: return@TextButton) },
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
