package com.questline.app.ui.today

/* VM экрана «Сегодня» (T-06/T-07): квесты дня, задачи, привычки, прогресс.
 * Отметка привычки возвращает HabitCheckResult: XP с дневным капом и веха
 * стрика 7/30/100 — веха поднимает событие milestone (полноэкранное
 * празднование QuestEffects.MilestoneCelebration). Заморозка — платная,
 * AppRepo.freezeHabitDay; чипу нужен день-кандидат (freezeOfferDays).
 */

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.questline.app.data.AppRepo
import com.questline.app.data.Category
import com.questline.app.data.Quest
import com.questline.app.data.Task
import com.questline.app.data.habits.Habit
import com.questline.app.data.habits.HabitCheck
import com.questline.app.domain.ProgressionEngine
import com.questline.app.domain.habits.HabitEngine
import com.questline.app.ui.habits.HabitMilestonePulse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

class TodayViewModel(private val repo: AppRepo) : ViewModel() {

    private val todayEpochDay: Long = AppRepo.todayEpochDay

    /** Открытые авто-квесты сегодняшнего дня. */
    val questsOfDay: StateFlow<List<Quest>> = repo.quests.observeOpen()
        .map { list ->
            list.filter { (it.source == "AUTO" && it.dateCreatedEpochDay == todayEpochDay) || it.source == "BUDGET" }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Задачи на сегодня; повторяющиеся в «отдыхе» скрыты, лимит отображения — на экране. */
    val tasksToday: StateFlow<List<Task>> = repo.tasks.observeForToday(todayEpochDay)
        .map { list ->
            list.filterNot { task ->
                val last = task.lastDoneEpochDay
                task.repeatIntervalDays > 0 && last != null &&
                    todayEpochDay < last + task.repeatIntervalDays
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Общий баланс монет. */
    val coins: StateFlow<Int> = repo.coins.observeTotalCoins()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** questKey -> эмодзи характеристики (сидируемые категории + запасные значения). */
    val keyEmoji: StateFlow<Map<String, String>> = repo.categories.observeQuest()
        .map { list ->
            val fallback = mapOf("PHYSICS" to "💪", "MIND" to "🧠", "MONEY" to "💰", "SOCIAL" to "💬", "DISCIPLINE" to "🎯")
            fallback + list.mapNotNull { c -> c.questKey?.let { it to c.emoji } }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** QUEST-категории для быстрого добавления задачи. */
    val questCategories: StateFlow<List<Category>> = repo.categories.observeQuest()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Активные привычки. */
    val habits: StateFlow<List<Habit>> = repo.habits.observeActive()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Отметки привычек за сегодня. */
    val habitChecksToday: StateFlow<List<HabitCheck>> = repo.habitChecks.observeForDay(todayEpochDay)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Стрик и день-кандидат на заморозку по каждой привычке (T-07). */
    private data class HabitProgress(val streak: Int, val freezeDay: Long?)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val habitProgress: StateFlow<Map<Long, HabitProgress>> = habits.flatMapLatest { list ->
        if (list.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(list.map { habit ->
                repo.habitChecks.observeRangeForHabit(habit.id, habit.createdAt, todayEpochDay)
            }) { checksList ->
                list.mapIndexed { index, habit ->
                    val checks = checksList[index]
                    val (current, _) = HabitEngine.streak(habit, checks, todayEpochDay)
                    habit.id to HabitProgress(
                        streak = current,
                        freezeDay = HabitEngine.lastMissedDueDay(habit, checks, todayEpochDay),
                    )
                }.toMap()
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Текущий стрик каждой привычки (HabitEngine.streak по всей истории с создания). */
    val habitStreaks: StateFlow<Map<Long, Int>> = habitProgress
        .map { progress -> progress.mapValues { (_, p) -> p.streak } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Привычка → последний пропущенный запланированный день (чип заморозки). */
    val freezeOfferDays: StateFlow<Map<Long, Long>> = habitProgress
        .map { progress -> progress.mapNotNull { (id, p) -> p.freezeDay?.let { id to it } }.toMap() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Событие вехи стрика (T-07) — полноэкранное празднование с конфетти. */
    private val _milestone = MutableStateFlow<HabitMilestonePulse?>(null)
    val milestone: StateFlow<HabitMilestonePulse?> = _milestone.asStateFlow()
    private var milestoneSeq = 0L

    fun clearMilestone() {
        _milestone.value = null
    }

    /** Квесты, по которым сейчас идёт пульс/закрытие (защита от двойного тапа). */
    private val _busyQuestIds = MutableStateFlow<Set<Long>>(emptySet())
    val busyQuestIds: StateFlow<Set<Long>> = _busyQuestIds.asStateFlow()

    private val _progress = MutableStateFlow<ProgressSnapshot?>(null)
    val progress: StateFlow<ProgressSnapshot?> = _progress.asStateFlow()

    /** Один раз собрать все DONE-квесты: уровень, XP, стрик. */
    fun loadProgressOnce() {
        viewModelScope.launch { _progress.value = buildProgressSnapshot() }
    }

    fun markQuestBusy(id: Long) = _busyQuestIds.update { it + id }
    fun releaseQuestBusy(id: Long) = _busyQuestIds.update { it - id }

    /** Закрыть квест и пересчитать прогресс (XP и стрик растут из новой записи DONE). */
    fun completeQuest(quest: Quest) {
        viewModelScope.launch {
            repo.completeQuest(quest)
            _progress.value = buildProgressSnapshot()
        }
    }

    /**
     * Отметить привычку за сегодня. XP (с дневным капом) начисляет repo.checkHabit;
     * веха стрика из результата — событие milestone (T-07). value != null —
     * фактическое значение количественной цели за тап.
     */
    fun checkHabit(habit: Habit, value: Double? = null) {
        viewModelScope.launch {
            val result = repo.checkHabit(habit, todayEpochDay, value)
            result.milestone?.let { milestone ->
                _milestone.value = HabitMilestonePulse(
                    habitId = habit.id,
                    streak = milestone,
                    coins = HabitEngine.milestoneCoinBonus(milestone),
                    seq = ++milestoneSeq,
                )
            }
            _progress.value = buildProgressSnapshot()
        }
    }

    /** Снятие отметки привычки с откатом XP (внутри repo.uncheckHabit). */
    fun uncheckHabit(habitId: Long) {
        viewModelScope.launch {
            repo.uncheckHabit(habitId, todayEpochDay)
            _progress.value = buildProgressSnapshot()
        }
    }

    /** Платная заморозка пропущенного дня (confirm-диалог уже пройден в чипе). */
    fun freezeHabit(habit: Habit, epochDay: Long) {
        viewModelScope.launch { repo.freezeHabitDay(habit, epochDay) }
    }

    /** Чекбокс задачи: закрытие создаёт USER-квест с XP (кор-луп); снятие — просто откат статуса. */
    fun toggleTask(task: Task, checked: Boolean) {
        viewModelScope.launch {
            if (checked) {
                repo.completeTaskAsQuest(task)
                _progress.value = buildProgressSnapshot()
            } else if (task.repeatIntervalDays > 0) {
                repo.tasks.update(task.copy(lastDoneEpochDay = null))
            } else {
                repo.tasks.update(task.copy(done = false, doneAtMillis = null))
            }
        }
    }

    /** Быстрое добавление задачи с «Сегодня» (AddTaskSheet). */
    fun addTask(
        title: String,
        complexity: String,
        categoryId: Long?,
        dueEpochDay: Long?,
        repeatIntervalDays: Int,
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repo.tasks.insert(
                Task(
                    title = title.trim(),
                    categoryId = categoryId,
                    repeatDaily = repeatIntervalDays > 0,
                    repeatIntervalDays = repeatIntervalDays,
                    dueEpochDay = dueEpochDay,
                    complexity = complexity,
                    createdAtMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

    private suspend fun buildProgressSnapshot(): ProgressSnapshot {
        val done = repo.quests.allDone()
        val closedDays = done.mapNotNull { q ->
            q.closedAtMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay() }
        }.toSet()
        // Уровень — единый источник XpLedger (SPEC v3): сумма всех начислений
        val state = ProgressionEngine.levelFromTotal(repo.xpLedger.observeSumTotal().first())
        return ProgressSnapshot(
            level = state.level,
            xpIntoLevel = state.xpIntoLevel,
            xpNeeded = state.xpNeeded,
            totalXp = state.totalXp,
            streakDays = ProgressionEngine.currentStreak(closedDays, todayEpochDay),
        )
    }
}
