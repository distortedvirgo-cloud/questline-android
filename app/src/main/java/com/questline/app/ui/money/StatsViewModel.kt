package com.questline.app.ui.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.questline.app.data.AppRepo
import com.questline.app.data.Category
import com.questline.app.data.Txn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/**
 * Дельта топ-категории к прошлому месяцу (T-10): рост расходов —
 * статус warn, падение — success (статус данных, STYLE.md).
 */
data class CategoryDelta(
    val category: Category?,
    val currentMinor: Long,
    val prevMinor: Long,
) {
    /** Изменение в процентах к прошлому месяцу; null — базы сравнения нет */
    val deltaPercent: Int?
        get() = if (prevMinor <= 0L) null else ((currentMinor - prevMinor) * 100 / prevMinor).toInt()

    /** «+18%», «−7%» или «новая» */
    val label: String
        get() = deltaPercent?.let { "%+d%%".format(it) } ?: "новая"
}

/** Столбик тренда: месяц и суммарные расходы */
data class TrendBar(val month: LocalDate, val expenseMinor: Long)

/** Всё для экрана «Статистика» за выбранный месяц */
data class StatsUiState(
    val slices: List<ExpenseSlice> = emptyList(),
    val totalExpenseMinor: Long = 0L,
    val top: List<CategoryDelta> = emptyList(),
    val trend: List<TrendBar> = emptyList(),
    val avgPerDayMinor: Long = 0L,
)

/**
 * Статистика месяца (T-10): агрегаты считаются в ViewModel из потоков
 * TxnDao.observeRange — окно месяца, прошлого месяца и 12-месячный тренд.
 */
class StatsViewModel(private val repo: AppRepo) : ViewModel() {

    private val _month = MutableStateFlow(LocalDate.now().withDayOfMonth(1))
    val month: StateFlow<LocalDate> = _month.asStateFlow()

    private val categories = repo.categories.observeFinance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val stats: StateFlow<StatsUiState> = _month.flatMapLatest { month ->
        val (from, to) = MoneyFormat.monthBounds(month)
        val (prevFrom, prevTo) = MoneyFormat.monthBounds(month.minusMonths(1))
        val trendFrom = month.minusMonths(11).withDayOfMonth(1).toEpochDay()
        combine(
            repo.txns.observeRange(from, to),
            repo.txns.observeRange(prevFrom, prevTo),
            repo.txns.observeRange(trendFrom, to),
            categories,
        ) { current, previous, trendTxns, cats ->
            buildState(month, from, to, current, previous, trendTxns, cats)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    private fun buildState(
        month: LocalDate,
        fromDay: Long,
        toDay: Long,
        current: List<Txn>,
        previous: List<Txn>,
        trendTxns: List<Txn>,
        categories: List<Category>,
    ): StatsUiState {
        val expenses = current.filter { it.type == "EXPENSE" }
        val total = expenses.sumOf { it.amountMinor }

        val slices = expenses.groupBy { it.categoryId }
            .map { (categoryId, list) ->
                ExpenseSlice(categories.firstOrNull { it.id == categoryId }, list.sumOf { it.amountMinor })
            }
            .sortedByDescending { it.amountMinor }

        val prevByCategory = previous.filter { it.type == "EXPENSE" }
            .groupBy { it.categoryId }
            .mapValues { (_, list) -> list.sumOf { it.amountMinor } }

        val top = slices.take(3).map { slice ->
            val categoryId = slice.category?.id
            CategoryDelta(
                category = slice.category,
                currentMinor = slice.amountMinor,
                prevMinor = if (categoryId != null) prevByCategory[categoryId] ?: 0L else 0L,
            )
        }

        val byMonthKey = trendTxns.filter { it.type == "EXPENSE" }
            .groupBy { AppRepo.monthPeriodKey(it.epochDay) }
            .mapValues { (_, list) -> list.sumOf { it.amountMinor } }
        val trend = (11 downTo 0).map { back ->
            val m = month.minusMonths(back.toLong()).withDayOfMonth(1)
            TrendBar(m, byMonthKey[AppRepo.monthPeriodKey(m.toEpochDay())] ?: 0L)
        }

        // Среднее в день: за прошедшие дни текущего месяца, за полный — прошлых
        val today = AppRepo.todayEpochDay
        val elapsedDays = (minOf(toDay, today) - fromDay + 1).coerceAtLeast(0)
        val avg = if (elapsedDays > 0) total / elapsedDays else 0L

        return StatsUiState(
            slices = slices,
            totalExpenseMinor = total,
            top = top,
            trend = trend,
            avgPerDayMinor = avg,
        )
    }

    fun setMonth(month: LocalDate) {
        _month.value = month.withDayOfMonth(1)
    }

    fun shiftMonth(deltaMonths: Long) {
        _month.value = _month.value.plusMonths(deltaMonths).withDayOfMonth(1)
    }
}
