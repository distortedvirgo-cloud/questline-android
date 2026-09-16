package com.questline.app.ui.profile

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.questline.app.data.AppRepo
import com.questline.app.data.CoinsLedger
import com.questline.app.domain.ProgressionEngine
import com.questline.app.domain.habits.CharacteristicEngine
import com.questline.app.domain.habits.HabitEngine
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ProfileViewModel(private val repo: AppRepo) : ViewModel() {

    data class State(
        val level: Int = 1,
        val xpIntoLevel: Int = 0,
        val xpNeeded: ProgressionEngine.LevelState? = null,
        val coins: Int = 0,
        /** Характеристики v3 (T-13): 0..100 по PHYSICS/MIND/MONEY/SOCIAL/DISCIPLINE */
        val characteristics: Map<String, Int> = emptyMap(),
        val recentCoins: List<CoinsLedger> = emptyList(),
        val streakMilestones: List<HabitStreakMilestones> = emptyList(),
    )

    val state = mutableStateOf(State())

    init {
        viewModelScope.launch {
            // Радар v3 (T-13): привычки (4 недели) первичны + квесты/задачи
            // (XP за 30 дней) вторичны; живой Flow — обновляется на каждую отметку
            combine(
                repo.habits.observeActive(),
                repo.habitChecks.observeAll(),
                repo.quests.observeDone(),
            ) { habits, checks, done ->
                val windowStart = System.currentTimeMillis() - QUEST_WINDOW_DAYS * MILLIS_PER_DAY
                val questKeyXp = ProgressionEngine.keyXp(done.filter { (it.closedAtMillis ?: 0L) >= windowStart })
                CharacteristicEngine.characteristics(habits, checks, questKeyXp, AppRepo.todayEpochDay)
            }.collect { chars ->
                state.value = state.value.copy(characteristics = chars)
            }
        }
        viewModelScope.launch {
            // Уровень — единый источник XpLedger (SPEC v3), обновляется на каждое начисление
            repo.xpLedger.observeSumTotal().collect { total ->
                val levelState = ProgressionEngine.levelFromTotal(total)
                state.value = state.value.copy(
                    level = levelState.level,
                    xpIntoLevel = levelState.xpIntoLevel,
                    xpNeeded = levelState,
                )
            }
        }
        viewModelScope.launch {
            repo.coins.observeTotalCoins().collect { total ->
                state.value = state.value.copy(coins = total)
            }
        }
        viewModelScope.launch {
            repo.coins.observeRecent(20).collect { list ->
                state.value = state.value.copy(recentCoins = list)
            }
        }
        viewModelScope.launch {
            // Вехи стрика (T-07): считаются на лету из HabitEngine, без новых таблиц
            combine(repo.habits.observeActive(), repo.habitChecks.observeAll()) { habits, checks ->
                val byHabit = checks.groupBy { it.habitId }
                habits.mapNotNull { habit ->
                    val best = HabitEngine.streak(habit, byHabit[habit.id].orEmpty(), AppRepo.todayEpochDay).second
                    if (best < HabitEngine.MILESTONE_DAYS.first()) return@mapNotNull null
                    HabitStreakMilestones(
                        emoji = habit.emoji,
                        title = habit.title,
                        best = best,
                    )
                }
            }.collect { rows ->
                state.value = state.value.copy(streakMilestones = rows)
            }
        }
    }
}

/** Строка «Вехи стрика» на «Я»: активная привычка с лучшей серией ≥ 7 */
data class HabitStreakMilestones(
    val emoji: String,
    val title: String,
    val best: Int,
) {
    /** Достигнутые вехи: 7/30/100 по лучшему стрику */
    val achieved: List<Int> get() = HabitEngine.MILESTONE_DAYS.filter { best >= it }
}

fun profileVmFactory(context: Context): ViewModelProvider.Factory = viewModelFactory {
    initializer { ProfileViewModel(AppRepo.get(context.applicationContext)) }
}

/** Окно квестовой активности для радара: 30 дней */
private const val QUEST_WINDOW_DAYS = 30L
private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
