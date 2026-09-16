package com.questline.app.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.questline.app.data.AppRepo
import com.questline.app.data.HabitCheckResult
import com.questline.app.data.habits.Habit
import com.questline.app.data.habits.HabitCheck
import com.questline.app.domain.habits.HabitEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Экран «Привычки» (T-04): активные привычки, отметки, стрики, недельная
 * консистентность. Списки — hot-флоу над DAO с WhileSubscribed(5000).
 */
class HabitsViewModel(private val repo: AppRepo) : ViewModel() {

    /** «Сегодня» и неделя фиксируются на момент создания VM — как в задачах */
    val todayEpochDay: Long = AppRepo.todayEpochDay

    /** Понедельник текущей недели (epochDay) — окно консистентности */
    val weekStartEpochDay: Long = LocalDate.ofEpochDay(todayEpochDay)
        .minusDays(LocalDate.ofEpochDay(todayEpochDay).dayOfWeek.value - 1L)
        .toEpochDay()

    /** Пульс «+N XP» после отметки; seq перезапускает анимацию на карточке */
    data class HabitXpPulse(val habitId: Long, val xp: Int, val seq: Long)

    private val _pulse = MutableStateFlow<HabitXpPulse?>(null)
    val pulse: StateFlow<HabitXpPulse?> = _pulse.asStateFlow()
    private var pulseSeq = 0L

    /** Событие вехи стрика (T-07) — полноэкранное празднование с конфетти */
    private val _milestone = MutableStateFlow<HabitMilestonePulse?>(null)
    val milestone: StateFlow<HabitMilestonePulse?> = _milestone.asStateFlow()
    private var milestoneSeq = 0L

    fun clearMilestone() {
        _milestone.value = null
    }

    /** Баланс монет: чип заморозки активен только при достатке */
    val coins: StateFlow<Int> = repo.coins.observeTotalCoins()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val cards: StateFlow<List<HabitCardUi>> =
        combine(repo.habits.observeActive(), repo.habitChecks.observeAll()) { habits, checks ->
            val byHabit = checks.groupBy { it.habitId }
            habits.map { habit ->
                val own = byHabit[habit.id].orEmpty()
                val (current, _) = HabitEngine.streak(habit, own, todayEpochDay)
                HabitCardUi(
                    habit = habit,
                    todayCheck = own.firstOrNull { it.epochDay == todayEpochDay },
                    streakCurrent = current,
                    weekConsistency = HabitEngine.weeklyConsistency(habit, own, weekStartEpochDay),
                    freezeDay = HabitEngine.lastMissedDueDay(habit, own, todayEpochDay),
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Тап по чеку: без цели — тоггл одним тапом; с целью — +1 к значению дня
     * (кап = цель). Достигнутая цель тапом не снимается — есть долгий тап.
     * Веха стрика из результата поднимает событие milestone (T-07).
     */
    fun check(card: HabitCardUi) {
        viewModelScope.launch {
            val habit = card.habit
            val existing = card.todayCheck
            when {
                habit.targetValue == null ->
                    if (existing == null) {
                        emitResult(habit, repo.checkHabit(habit, todayEpochDay))
                    } else {
                        repo.uncheckHabit(habit.id, todayEpochDay)
                    }

                existing == null -> emitResult(habit, repo.checkHabit(habit, todayEpochDay, 1.0))

                (existing.value ?: 0.0) < habit.targetValue -> {
                    val next = (existing.value ?: 0.0) + 1.0
                    emitResult(habit, repo.checkHabit(habit, todayEpochDay, next))
                }
                // Цель уже достигнута: тап ничего не меняет
            }
        }
    }

    /** Долгий тап по чеку: снять отметку за сегодня (чек и его XP). */
    fun uncheck(card: HabitCardUi) {
        viewModelScope.launch { repo.uncheckHabit(card.habit.id, todayEpochDay) }
    }

    /** Платная заморозка последнего пропущенного запланированного дня */
    fun freeze(card: HabitCardUi) {
        val day = card.freezeDay ?: return
        viewModelScope.launch { repo.freezeHabitDay(card.habit, day) }
    }

    /** Создание/правка из редактора */
    fun save(habit: Habit) {
        viewModelScope.launch { repo.upsertHabit(habit) }
    }

    fun archive(habit: Habit) {
        viewModelScope.launch { repo.archiveHabit(habit) }
    }

    fun delete(habitId: Long) {
        viewModelScope.launch { repo.deleteHabit(habitId) }
    }

    private fun emitPulse(habitId: Long, xp: Int) {
        if (xp <= 0) return
        _pulse.value = HabitXpPulse(habitId, xp, ++pulseSeq)
    }

    /** XP-пульс + событие вехи стрика по итогу отметки (T-07) */
    private suspend fun emitResult(habit: Habit, result: HabitCheckResult) {
        emitPulse(habit.id, result.awardedXp)
        val milestone = result.milestone ?: return
        _milestone.value = HabitMilestonePulse(
            habitId = habit.id,
            streak = milestone,
            coins = HabitEngine.milestoneCoinBonus(milestone),
            seq = ++milestoneSeq,
        )
    }
}

/** Модель карточки: вся математика посчитана движком, экран только рисует. */
data class HabitCardUi(
    val habit: Habit,
    val todayCheck: HabitCheck?,
    val streakCurrent: Int,
    val weekConsistency: Double,
    /** Последний запланированный прошедший день без чека — кандидат на заморозку */
    val freezeDay: Long? = null,
) {
    /** Выполнено сегодня: обычная отмечена; количественная доросла до цели */
    val doneToday: Boolean
        get() = todayCheck != null &&
            (habit.targetValue == null || (todayCheck.value ?: 0.0) >= habit.targetValue)

    /** Сегодняшний день перекрыт заморозкой — чек-зона показывает снежинку */
    val frozenToday: Boolean
        get() = todayCheck?.frozen == true
}
