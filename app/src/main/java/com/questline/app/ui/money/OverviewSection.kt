package com.questline.app.ui.money

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.questline.app.data.AppRepo
import com.questline.app.data.Category
import com.questline.app.data.Txn
import com.questline.app.ui.theme.QColors
import com.questline.app.ui.theme.questlineQ
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/**
 * Нейтральная палитра срезов диаграммы: осветлённые роли STYLE.md.
 * colorIndex категории указывает на индекс в этом наборе.
 */
private fun slicePalette(q: QColors) = listOf(
    q.accent,          // 0
    q.success,         // 1
    q.warn,            // 2
    q.coin,            // 3
    q.danger,          // 4
    Color(0xFF7B86E8), // 5 — светло-акцентный
    Color(0xFF6699A8), // 6 — приглушённый морской
    Color(0xFF8F8F8F), // 7 — нейтральный серый
)

@Composable
fun colorForIndex(idx: Int): Color {
    val palette = slicePalette(questlineQ())
    return palette[idx % palette.size]
}

class OverviewViewModel(repo: AppRepo) : ViewModel() {

    val financeCategories = repo.categories.observeFinance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val txnsRepo = repo.txns

    @OptIn(ExperimentalCoroutinesApi::class)
    fun rangeFlow(from: Long, to: Long) = txnsRepo.observeRange(from, to)
}

/** Срез расходов по категории для диаграммы и топа */
data class ExpenseSlice(val category: Category?, val amountMinor: Long) {
    val label: String get() = category?.let { "${it.emoji} ${it.name}" } ?: "Прочее"
}

/**
 * Обзор: пончиковая диаграмма расходов месяца, топ-3 категории,
 * последние 10 транзакций.
 */
@Composable
fun OverviewSection(month: LocalDate, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val vm: OverviewViewModel = viewModel { OverviewViewModel(AppRepo.get(context)) }

    val categories by vm.financeCategories.collectAsState()

    val (fromDay, toDay) = MoneyFormat.monthBounds(month)
    val txns by remember(fromDay, toDay) { vm.rangeFlow(fromDay, toDay) }
        .collectAsState(initial = emptyList())

    val expenses = txns.filter { it.type == "EXPENSE" }
    val totalExpense = expenses.sumOf { it.amountMinor }

    // Срезы по категориям, большие сверху
    val slices = remember(expenses, categories) {
        expenses.groupBy { it.categoryId }
            .map { (categoryId, list) ->
                ExpenseSlice(categories.firstOrNull { it.id == categoryId }, list.sumOf { it.amountMinor })
            }
            .sortedByDescending { it.amountMinor }
    }

    SectionColumn(modifier = modifier) {
        if (totalExpense <= 0L) {
            Text(
                text = "В этом месяце расходов пока нет",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionColumn
        }

        DonutChart(
            slices = slices,
            totalMinor = totalExpense,
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp),
        )

        Spacer(Modifier.height(16.dp))

        Text("Топ категорий", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        slices.take(3).forEach { slice ->
            TopCategoryRow(slice = slice, totalMinor = totalExpense)
            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.height(16.dp))

        Text("Последние операции", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        txns.take(10).forEach { txn ->
            TxnRow(txn = txn, category = categories.firstOrNull { it.id == txn.categoryId })
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun DonutChart(slices: List<ExpenseSlice>, totalMinor: Long, modifier: Modifier = Modifier) {
    val palette = slicePalette(questlineQ())
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 34.dp.toPx()
            val diameter = minOf(size.width, size.height) - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)

            var startAngle = -90f
            slices.forEachIndexed { index, slice ->
                val sweep = slice.amountMinor.toFloat() / totalMinor.toFloat() * 360f
                drawArc(
                    color = palette[(slice.category?.colorIndex ?: 7) % palette.size],
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
                startAngle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = MoneyFormat.text(totalMinor),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "расходы",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TopCategoryRow(slice: ExpenseSlice, totalMinor: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .background(colorForIndex(slice.category?.colorIndex ?: 7), CircleShape),
        )
        Spacer(Modifier.padding(4.dp))
        Text(slice.label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(
            text = "${MoneyFormat.text(slice.amountMinor)} · ${(slice.amountMinor * 100 / totalMinor)}%",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TxnRow(txn: Txn, category: Category?) {
    val isExpense = txn.type == "EXPENSE"
    val amountText = (if (isExpense) "\u2212" else "+") + MoneyFormat.text(txn.amountMinor)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(category?.emoji ?: "\uD83D\uDCE6", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.padding(4.dp))
        Column {
            Text(category?.name ?: "Без категории", style = MaterialTheme.typography.bodyLarge)
            if (txn.note.isNotBlank()) {
                Text(
                    text = txn.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = amountText,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = FontFamily.Monospace,
                color = if (isExpense) MaterialTheme.colorScheme.onSurface else questlineQ().success,
            )
            Text(
                text = MoneyFormat.dayWords(txn.epochDay),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
