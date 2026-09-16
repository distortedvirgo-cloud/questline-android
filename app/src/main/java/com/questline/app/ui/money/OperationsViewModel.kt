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

/** Фильтр списка операций по типу */
enum class OperationTypeFilter(val label: String) {
    ALL("Все"),
    EXPENSE("Расходы"),
    INCOME("Доходы"),
}

/** Строка списка: транзакция и её финансовая категория */
data class OperationRow(val txn: Txn, val category: Category?) {
    val title: String get() = category?.name ?: "Без категории"
}

/** Группа операций одного дня (свежие сверху) */
data class OperationDayGroup(val epochDay: Long, val rows: List<OperationRow>)

/**
 * Список операций (T-09): месяц стрелками, поиск по заметке и названию
 * категории, фильтр типа. Группировка по дням — свежие сверху.
 */
class OperationsViewModel(private val repo: AppRepo) : ViewModel() {

    private val _month = MutableStateFlow(LocalDate.now().withDayOfMonth(1))
    val month: StateFlow<LocalDate> = _month.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _typeFilter = MutableStateFlow(OperationTypeFilter.ALL)
    val typeFilter: StateFlow<OperationTypeFilter> = _typeFilter.asStateFlow()

    private val categories = repo.categories.observeFinance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val groups: StateFlow<List<OperationDayGroup>> = _month.flatMapLatest { month ->
        val (from, to) = MoneyFormat.monthBounds(month)
        combine(
            repo.txns.observeRange(from, to),
            _query,
            _typeFilter,
            categories,
        ) { txns, query, type, cats ->
            val lowered = query.trim().lowercase()
            txns.asSequence()
                .filter { matchesType(it, type) }
                .filter { txn ->
                    if (lowered.isEmpty()) return@filter true
                    val category = cats.firstOrNull { it.id == txn.categoryId }
                    "${txn.note} ${category?.name ?: ""}".lowercase().contains(lowered)
                }
                .map { txn -> OperationRow(txn, cats.firstOrNull { it.id == txn.categoryId }) }
                .groupBy { it.txn.epochDay }
                .map { (day, rows) -> OperationDayGroup(day, rows) }
                .toList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun matchesType(txn: Txn, type: OperationTypeFilter): Boolean = when (type) {
        OperationTypeFilter.ALL -> true
        OperationTypeFilter.EXPENSE -> txn.type == "EXPENSE"
        OperationTypeFilter.INCOME -> txn.type == "INCOME"
    }

    fun setMonth(month: LocalDate) {
        _month.value = month.withDayOfMonth(1)
    }

    fun shiftMonth(deltaMonths: Long) {
        _month.value = _month.value.plusMonths(deltaMonths).withDayOfMonth(1)
    }

    fun setQuery(query: String) {
        _query.value = query
    }

    fun setTypeFilter(filter: OperationTypeFilter) {
        _typeFilter.value = filter
    }
}
