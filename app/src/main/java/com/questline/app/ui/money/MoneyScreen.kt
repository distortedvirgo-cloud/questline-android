package com.questline.app.ui.money

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.questline.app.data.AppRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.LocalDate

/** Итоги месяца: доходы и расходы minor units */
data class MonthBalance(val incomeMinor: Long, val expenseMinor: Long) {
    val balanceMinor: Long get() = incomeMinor - expenseMinor
}

/** Месяц и баланс месяца — общее состояние всех секций финансов */
class MoneyViewModel(private val repo: AppRepo) : ViewModel() {

    private val _month = MutableStateFlow(LocalDate.now().withDayOfMonth(1))
    val month: kotlinx.coroutines.flow.StateFlow<LocalDate> = _month.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val balance = _month.flatMapLatest { m ->
        val (from, to) = MoneyFormat.monthBounds(m)
        repo.txns.observeRange(from, to).map { txns ->
            MonthBalance(
                incomeMinor = txns.filter { it.type == "INCOME" }.sumOf { it.amountMinor },
                expenseMinor = txns.filter { it.type == "EXPENSE" }.sumOf { it.amountMinor },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthBalance(0, 0))

    fun shiftMonth(deltaMonths: Long) {
        _month.value = _month.value.plusMonths(deltaMonths).withDayOfMonth(1)
    }
}

private enum class MoneyTab(val label: String) {
    OVERVIEW("Обзор"),
    BUDGETS("Бюджеты"),
    GOALS("Копилки"),
    AI("✨ AI"),
}

/**
 * Финансы: месяц стрелками ‹ ›, баланс месяца и три секции табами-чипами.
 * FAB «+» — быстрый ввод транзакции.
 */
@Composable
fun MoneyScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val vm: MoneyViewModel = viewModel { MoneyViewModel(AppRepo.get(context)) }

    val month by vm.month.collectAsState()
    val balance by vm.balance.collectAsState()

    var currentTab by remember { mutableStateOf(MoneyTab.OVERVIEW) }
    var showQuickAdd by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showQuickAdd = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Быстрый ввод")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            // Компактная строка баланса + чипы карт/счетов
            MoneyAccountsHeader(AppRepo.get(context))
            Spacer(Modifier.height(8.dp))

            // Шапка месяца ‹ ›
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.shiftMonth(-1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Предыдущий месяц")
                }
                Text(
                    text = MoneyFormat.monthTitle(month),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.shiftMonth(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Следующий месяц")
                }
            }

            Spacer(Modifier.height(8.dp))

            // Итог месяца одной строкой — сводка не должна съедать контент
            Text(
                text = monthSummaryLine(balance),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )

            Spacer(Modifier.height(10.dp))

            // Инбокс банковских пушей — только на вкладке Обзор текущего месяца
            // Табы-чипы секций; скролл, чтобы на узких экранах не терялся «✨ AI»
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                MoneyTab.entries.forEach { tab ->
                    FilterChip(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        label = { Text(tab.label) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // weight, а не fillMaxSize: Box занимает остаток после инбокса,
            // иначе длинный инбокс выталкивается за экран без скролла
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (currentTab) {
                    MoneyTab.OVERVIEW -> OverviewSection(month, Modifier.fillMaxSize())
                    MoneyTab.AI -> AiMonthTabContent(month, Modifier.fillMaxSize())
                    MoneyTab.BUDGETS -> BudgetsSection(month, Modifier.fillMaxSize())
                    MoneyTab.GOALS -> GoalsSection(Modifier.fillMaxSize())
                }
            }
        }
    }

    if (showQuickAdd) {
        QuickAddSheet(onDismissRequest = { showQuickAdd = false })
    }
}

/** Скролл-обёртка для содержимого секций (используется внутри секций) */
@Composable
internal fun SectionColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        content()
        Spacer(Modifier.height(88.dp)) // воздух над FAB
    }
}

/** «Доходы 100 ₽ · Расходы 88 ₽ → +12 ₽» — итог месяца одной строкой. */
private fun monthSummaryLine(balance: MonthBalance): String {
    val net = balance.incomeMinor - balance.expenseMinor
    val netText = if (net > 0) "+" + MoneyFormat.text(net)
        else if (net < 0) "−" + MoneyFormat.text(-net)
        else "0 ₽"
    return "Доходы ${MoneyFormat.text(balance.incomeMinor)} · Расходы ${MoneyFormat.text(balance.expenseMinor)} → $netText"
}
