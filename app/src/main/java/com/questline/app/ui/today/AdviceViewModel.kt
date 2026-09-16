package com.questline.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.questline.app.data.AppRepo
import com.questline.app.data.Category
import com.questline.app.data.Task
import com.questline.app.data.Txn
import com.questline.app.data.habits.Habit
import com.questline.app.domain.advice.AdviceCatalog
import com.questline.app.domain.advice.BudgetPressure
import com.questline.app.domain.advice.LaggingGoal
import com.questline.app.domain.advice.NudgeEngine
import com.questline.app.domain.advice.NudgeInput
import com.questline.app.domain.advice.Tip
import com.questline.app.domain.advice.TipKind
import com.questline.app.domain.advice.StarterHabit
import com.questline.app.domain.finance.BurnRate
import com.questline.app.domain.finance.burnRate
import com.questline.app.domain.habits.HabitEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Состояние совета дня (T-12). Данные для NudgeEngine (привычки, отметки,
 * бюджеты, копилки) собираются напрямую через DAO-потоки AppRepo — TodayViewModel
 * не задействован. Совет детерминирован по дате: пересчитывается только при
 * смене данных, в рамках дня — стабилен.
 */
class AdviceViewModel(private val repo: AppRepo) : ViewModel() {

    /** «Сегодня» фиксируется на момент создания VM — как в остальных экранах */
    val todayEpochDay: Long = AppRepo.todayEpochDay

    private val monthDate = LocalDate.ofEpochDay(todayEpochDay)
    private val monthStart: Long = monthDate.withDayOfMonth(1).toEpochDay()

    data class AdviceUi(
        val tip: Tip? = null,
        /** id совета, по которому уже нажали «Взять» — карточка показывает подтверждение */
        val confirmedId: String? = null,
        val busy: Boolean = false,
        /** Разовый toast-текст после действия («Упростил „X“ — верни ритм») */
        val event: String? = null,
    )

    private val busy = MutableStateFlow(false)
    private val confirmedId = MutableStateFlow<String?>(null)
    private val event = MutableStateFlow<String?>(null)

    /** Совет на момент нажатия «Взять»: после действия карточка не «прыгает» на новый */
    private val frozenTip = MutableStateFlow<Tip?>(null)

    val state: StateFlow<AdviceUi> = combine(
        dailyTipFlow(),
        busy.asStateFlow(),
        confirmedId.asStateFlow(),
        frozenTip.asStateFlow(),
        event.asStateFlow(),
    ) { liveTip, busy, confirmed, frozen, event ->
        AdviceUi(tip = frozen ?: liveTip, confirmedId = confirmed, busy = busy, event = event)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AdviceUi())

    /** Снимок данных пользователя → NudgeEngine.dailyTip (детерминирован по дате). */
    private fun dailyTipFlow() = combine(
        repo.habits.observeActive(),
        repo.habitChecks.observeAll(),
        repo.categories.observeFinance(),
        repo.txns.observeRange(monthStart, todayEpochDay),
        repo.goals.observeActive(),
    ) { habits, checks, financeCategories, txns, goals ->
        NudgeInput(
            habits = habits,
            checks = checks,
            consistency = HabitEngine.consistencyByCharacteristic(habits, checks, weekStartEpochDay()),
            budgets = budgetPressures(financeCategories, txns),
            laggingGoals = goals
                .filter { it.status == "ACTIVE" && it.targetMinor > 0 && it.savedMinor <= 0L }
                .map { LaggingGoal(it.name) },
            today = todayEpochDay,
        )
    }.map { input -> NudgeEngine.dailyTip(input) }

    /** Категории с планом, по которым burn rate FAST или OVER (T-08 математика). */
    private fun budgetPressures(categories: List<Category>, txns: List<Txn>): List<BudgetPressure> {
        val spentByCategory = txns
            .filter { it.type == "EXPENSE" }
            .groupBy { it.categoryId }
            .mapValues { (_, list) -> list.sumOf { it.amountMinor } }
        return categories.filter { !it.isIncome && (it.budgetMonthlyMinor ?: 0L) > 0L }.mapNotNull { category ->
            val rate = burnRate(
                spentMinor = spentByCategory[category.id] ?: 0L,
                planMinor = category.budgetMonthlyMinor ?: 0L,
                dayOfMonth = monthDate.dayOfMonth,
                daysInMonth = monthDate.lengthOfMonth(),
            )
            when (rate.status) {
                BurnRate.Status.CALM -> null
                else -> BudgetPressure(category.name, rate.status, rate.overspendPercent)
            }
        }
    }

    private fun weekStartEpochDay(): Long {
        val date = LocalDate.ofEpochDay(todayEpochDay)
        return date.minusDays((date.dayOfWeek.value - 1).toLong()).toEpochDay()
    }

    /**
     * «Взять»: START_HABIT — привычка из шаблона каталога; SHRINK/GROW —
     * понижение/повышение сложности существующей; TASK_INFO — задача на сегодня
     * (закроется как квест, XP как в v2). FINANCE навигует карточка, ANCHOR — без действия.
     */
    fun take(tip: Tip) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            try {
                when (tip.kind) {
                    TipKind.START_HABIT -> {
                        val starter = AdviceCatalog.parseStarterHabit(tip.payload)
                        if (starter != null) {
                            repo.upsertHabit(starter.toHabit(todayEpochDay))
                            confirm(tip, "Взял! Привычка создана")
                        }
                    }
                    TipKind.SHRINK_HABIT -> {
                        val habit = repo.habits.byId(tip.payload?.toLongOrNull() ?: 0L)
                        if (habit != null) {
                            repo.upsertHabit(NudgeEngine.shrunk(habit))
                            confirm(tip, "Упростил «${habit.title}» — верни ритм")
                        }
                    }
                    TipKind.GROW_HABIT -> {
                        val habit = repo.habits.byId(tip.payload?.toLongOrNull() ?: 0L)
                        if (habit != null) {
                            repo.upsertHabit(NudgeEngine.grown(habit))
                            confirm(tip, "Поднял планку «${habit.title}»")
                        }
                    }
                    TipKind.TASK_INFO -> {
                        repo.tasks.insert(
                            Task(
                                title = tip.payload.orEmpty().ifEmpty { "Малый шаг" },
                                dueEpochDay = todayEpochDay,
                                complexity = "S",
                                createdAtMillis = System.currentTimeMillis(),
                            ),
                        )
                        confirm(tip, "Взял! Задача на сегодня")
                    }
                    TipKind.FINANCE, TipKind.ANCHOR_HABIT -> Unit
                }
            } finally {
                busy.value = false
            }
        }
    }

    private fun confirm(tip: Tip, message: String) {
        confirmedId.value = tip.id
        frozenTip.value = tip
        event.value = message
    }

    /** Карточка после показа toast сбрасывает событие */
    fun consumeEvent() {
        event.value = null
    }
}

/** Привычка из шаблона каталога: ежедневная, создание — сегодня. */
private fun StarterHabit.toHabit(today: Long): Habit = Habit(
    title = title,
    emoji = emoji,
    characteristic = characteristic,
    scheduleType = HabitEngine.SCHEDULE_DAILY,
    targetValue = targetValue,
    unit = unit?.takeIf { targetValue != null },
    complexity = complexity,
    createdAt = today,
)
