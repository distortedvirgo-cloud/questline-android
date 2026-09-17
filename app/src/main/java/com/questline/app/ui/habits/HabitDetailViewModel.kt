package com.questline.app.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.questline.app.data.AppRepo
import com.questline.app.data.habits.Habit
import com.questline.app.data.habits.HabitCheck
import com.questline.app.domain.habits.HabitEngine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Экран привычки (N-02): живые данные по одной привычке. Агрегаты (стрики,
 * «% дней за 30 дней», всего отметок) считаются здесь из сырых списков DAO;
 * математика расписаний и стриков — в HabitEngine. observeAll (включая
 * архивные) — чтобы правки из редактора обновляли экран и удаление привычки
 * закрывало его.
 */
class HabitDetailViewModel(
    private val repo: AppRepo,
    private val habitId: Long,
) : ViewModel() {

    val todayEpochDay: Long = AppRepo.todayEpochDay

    /** Модель экрана: вся математика посчитана, composables только рисуют */
    data class HabitDetailUi(
        val habit: Habit,
        /** Все отметки привычки по возрастанию дня — источник heatmap */
        val checks: List<HabitCheck>,
        val streakCurrent: Int,
        val streakBest: Int,
        val totalChecks: Int,
        val month30Percent: Int,
        /** Последние 10 отметок по убыванию дня */
        val recent: List<HabitCheck>,
        val canFreezeToday: Boolean,
    )

    val ui: StateFlow<HabitDetailUi?> = combine(
        repo.habits.observeAll(),
        repo.habitChecks.observeAll(),
        repo.coins.observeTotalCoins(),
    ) { habits, checks, coins ->
        val habit = habits.firstOrNull { it.id == habitId } ?: return@combine null
        val own = checks.filter { it.habitId == habitId }
        val (current, best) = HabitEngine.streak(habit, own, todayEpochDay)
        val todayCheck = own.firstOrNull { it.epochDay == todayEpochDay }
        HabitDetailUi(
            habit = habit,
            checks = own,
            streakCurrent = current,
            streakBest = best,
            totalChecks = own.count { !it.frozen },
            month30Percent = month30Percent(habit, own),
            recent = own.sortedByDescending { it.epochDay }.take(10),
            canFreezeToday = todayCheck == null &&
                habit.scheduleType != HabitEngine.SCHEDULE_TIMES_PER_WEEK &&
                HabitEngine.isDue(habit, todayEpochDay) &&
                coins >= AppRepo.FREEZE_COST,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Платная заморозка сегодняшнего дня; гварды внутри AppRepo.freezeHabitDay */
    fun freezeToday() {
        val habit = ui.value?.habit ?: return
        viewModelScope.launch { repo.freezeHabitDay(habit, todayEpochDay) }
    }

    /** Сохранение правок из HabitEditorSheet */
    fun save(habit: Habit) {
        viewModelScope.launch { repo.upsertHabit(habit) }
    }

    fun archive(habit: Habit) {
        viewModelScope.launch { repo.archiveHabit(habit) }
    }

    fun delete() {
        viewModelScope.launch { repo.deleteHabit(habitId) }
    }

    /**
     * «% дней за 30 дней»: доля удержанных (успех или заморозка) от
     * запланированных за окно [today-29..today]; привычка обязывается только
     * от createdAt. Для гибкой цели знаменатель — недельная цель × 4 недели
     * (как plannedFractionOver движка).
     */
    private fun month30Percent(habit: Habit, own: List<HabitCheck>): Int {
        val from = maxOf(todayEpochDay - 29, habit.createdAt)
        if (from > todayEpochDay) return 0
        if (habit.scheduleType == HabitEngine.SCHEDULE_TIMES_PER_WEEK) {
            val target = HabitEngine.weekTarget(habit) * (30 / 7)
            if (target <= 0) return 0
            val kept = own.count { it.epochDay in from..todayEpochDay && isKept(habit, it) }
            return ((kept.toDouble() / target) * 100).roundToInt().coerceAtMost(100)
        }
        val byDay = own.associateBy { it.epochDay }
        var planned = 0
        var kept = 0
        var day = from
        while (day <= todayEpochDay) {
            if (HabitEngine.isDue(habit, day)) {
                planned++
                if (byDay[day]?.let { isKept(habit, it) } == true) kept++
            }
            day++
        }
        if (planned == 0) return 0
        return ((kept.toDouble() / planned) * 100).roundToInt().coerceAtMost(100)
    }

    /** День удержан: заморозка или количественная цель достигнута */
    private fun isKept(habit: Habit, check: HabitCheck): Boolean =
        check.frozen || (habit.targetValue == null || (check.value ?: 0.0) >= habit.targetValue)
}
