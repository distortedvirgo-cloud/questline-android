package com.questline.app.ui.money

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.questline.app.domain.finance.BurnRate
import com.questline.app.domain.finance.burnRate
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

/** Конверт месяца: категория и потраченное за месяц */
data class PlanRow(val category: Category, val spentMinor: Long) {
    val planMinor: Long get() = category.budgetMonthlyMinor ?: 0L

    /** Доля расходов 0..1+ для цвета статуса (не обрезаем: OVER виден цветом) */
    val fraction: Float get() = if (planMinor <= 0L) 0f else spentMinor.toFloat() / planMinor
}

class PlanMonthViewModel(private val repo: AppRepo) : ViewModel() {

    private val _month = MutableStateFlow(LocalDate.now().withDayOfMonth(1))

    @OptIn(ExperimentalCoroutinesApi::class)
    val rows = _month.flatMapLatest { month ->
        val (from, to) = MoneyFormat.monthBounds(month)
        repo.categories.observeFinance().flatMapLatest { categories ->
            // Конверты — только расходные категории
            val envelopes = categories.filter { !it.isIncome }
            if (envelopes.isEmpty()) {
                flowOf(emptyList<PlanRow>())
            } else {
                combine(
                    envelopes.map { category ->
                        repo.txns.observeSpentInCategory(category.id, from, to)
                            .map { spent -> PlanRow(category, spent) }
                    },
                ) { it.toList() }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setMonth(month: LocalDate) {
        _month.value = month.withDayOfMonth(1)
    }

    /** Сохранить месячный план категории (в копейках) */
    fun savePlan(categoryId: Long, minor: Long) {
        viewModelScope.launch {
            repo.categories.setBudget(categoryId, minor)
        }
    }
}

/**
 * «План» — дефолтная секция «Денег»: сводка месяца, burn rate, конверты
 * категорий (план/потрачено/осталось) и инбокс банковских пушей внизу —
 * он жил в «Обзоре» v2 и теперь скроллится вместе с «Планом».
 */
@Composable
fun PlanMonthSection(month: LocalDate, balance: MonthBalance, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val vm: PlanMonthViewModel = viewModel { PlanMonthViewModel(AppRepo.get(context)) }

    LaunchedEffect(month) { vm.setMonth(month) }

    val rows by vm.rows.collectAsState()

    var editing by remember { mutableStateOf<Category?>(null) }

    SectionColumn(modifier = modifier) {
        // Сводка месяца одной строкой
        Text(
            text = monthSummaryLine(balance),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(6.dp))
        BurnRateLine(spentMinor = balance.expenseMinor, rows = rows, month = month)
        Spacer(Modifier.height(12.dp))

        // Конверты с планом; у категорий без плана показываем «Задай план»
        // только если по ним уже есть траты, пустые не показываем вовсе.
        val budgeted = rows.filter { it.planMinor > 0L }
        val unplanned = rows.filter { it.planMinor <= 0L && it.spentMinor > 0L }
        if (budgeted.isEmpty() && unplanned.isEmpty()) {
            Text(
                text = "Задай месячный план — категория станет конвертом.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        budgeted.forEach { row ->
            EnvelopeCard(row = row, onEdit = { editing = row.category })
            Spacer(Modifier.height(8.dp))
        }
        unplanned.forEach { row ->
            UnplannedRow(row = row, onPick = { editing = row.category })
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.height(8.dp))
        // Инбокс пушей — как в «Обзоре» v2, листается вместе с секцией
        PendingInboxSection(AppRepo.get(context))
    }

    editing?.let { category ->
        EditPlanDialog(
            category = category,
            onDismiss = { editing = null },
            onSave = { minor ->
                vm.savePlan(category.id, minor)
                editing = null
            },
        )
    }
}

/**
 * Burn rate бейджем: нейтральный «спокоен», янтарный «быстрее плана»,
 * красный «план пробит». Цвет — только статус данных, не кнопка.
 */
@Composable
private fun BurnRateLine(spentMinor: Long, rows: List<PlanRow>, month: LocalDate) {
    val planMinor = rows.sumOf { it.planMinor }
    // Прошлый и будущий месяц считаем целиком прошедшим; текущий — по дню
    val today = LocalDate.now()
    val day = if (month.year == today.year && month.month == today.month) today.dayOfMonth
    else month.lengthOfMonth()

    val rate = burnRate(spentMinor, planMinor, day, month.lengthOfMonth())

    val (text, fg, bg) = when (rate.status) {
        BurnRate.Status.CALM -> Triple("Темп: спокоен", Q.inkMuted, Q.surfaceAlt)
        BurnRate.Status.FAST -> Triple("Тратишь на ${rate.overspendPercent}% быстрее плана", Q.warn, Q.warn.copy(alpha = 0.15f))
        BurnRate.Status.OVER -> Triple("План пробит", Q.danger, Q.danger.copy(alpha = 0.15f))
    }
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

/** Конверт: прогресс spent/plan с семафором success→warn→danger, как в v2. */
@Composable
private fun EnvelopeCard(row: PlanRow, onEdit: () -> Unit) {
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
                text = "${MoneyFormat.text(row.spentMinor)} / ${MoneyFormat.text(row.planMinor)}",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Изменить план", tint = Q.inkMuted)
            }
        }

        val animatedFraction by animateFloatAsState(row.fraction.coerceIn(0f, 1f), tween(200), label = "planProgress")
        LinearProgressIndicator(
            progress = { animatedFraction },
            color = statusColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
        )

        Spacer(Modifier.height(4.dp))
        Text(
            text = if (row.fraction >= 1f) "Перерасход на ${MoneyFormat.text(row.spentMinor - row.planMinor)}" else "Осталось ${MoneyFormat.text(row.planMinor - row.spentMinor)}",
            style = MaterialTheme.typography.bodySmall,
            color = statusColor,
        )
    }
}

/** Тихая строка категории без плана: траты есть, лимита нет — быстрый ввод плана. */
@Composable
private fun UnplannedRow(row: PlanRow, onPick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${row.category.emoji} ${row.category.name}", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        Text(
            MoneyFormat.text(row.spentMinor),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onPick) { Text("Задай план") }
    }
}

/** Диалог ввода месячного плана — механика диалога бюджета из v2. */
@Composable
private fun EditPlanDialog(category: Category, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    // Нулевой бюджет — пустое поле, чтобы ввод не дописывался после «0»
    var text by remember {
        mutableStateOf(
            category.budgetMonthlyMinor
                ?.takeIf { it > 0L }
                ?.let { (it / 100).toString() }
                ?: "",
        )
    }
    val minor = MoneyFormat.parseRubles(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("План: ${category.emoji} ${category.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { input -> text = input.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' } },
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** «Доходы 100 ₽ · Расходы 88 ₽ → +12 ₽» — итог месяца одной строкой. */
private fun monthSummaryLine(balance: MonthBalance): String {
    val net = balance.incomeMinor - balance.expenseMinor
    val netText = if (net > 0) "+" + MoneyFormat.text(net) else if (net < 0) "−" + MoneyFormat.text(-net) else "0 ₽"
    return "Доходы ${MoneyFormat.text(balance.incomeMinor)} · Расходы ${MoneyFormat.text(balance.expenseMinor)} → $netText"
}
