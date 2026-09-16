package com.questline.app.domain.habits

import com.questline.app.data.habits.Habit
import com.questline.app.data.habits.HabitCheck
import kotlin.math.roundToInt

/**
 * Характеристики v3 (T-13): сила каждой из 5 сфер 0..100.
 *
 * Первичный источник — привычки: средневзвешенная недельная консистентность
 * за последние 4 недели ([HabitEngine.weeklyConsistency], ближние недели
 * тяжелее) + бонус за живые стрики. Вторичный — закрытые квесты/задачи:
 * XP характеристики за последние 30 дней против капа (по духу v2 — keyXp).
 * Итог при обоих источниках — слияние 0.65 привычки + 0.35 квесты.
 *
 * Пустые источники не обнуляют сферу (радар не должен умирать у игрока v2.8):
 * нет данных привычек → чистый questScore; нет квестовой активности за 30 дней
 * → чистый habitScore; пусто и там и там → 0.
 *
 * Чистый Kotlin без Android-зависимостей: всё состояние приходит параметрами.
 */
object CharacteristicEngine {

    /** Вклад источников в итог: привычки первичны, квесты/задачи вторичны */
    const val WEIGHT_HABITS = 0.65
    const val WEIGHT_QUESTS = 0.35

    /** Сколько недель консистентности смотрим назад (включая текущую) */
    const val WEEKS = 4

    /** Кап XP за 30 дней на характеристику: выше — квесты дают максимум сферы */
    const val QUEST_XP_CAP = 500

    /** Бонус привычки за живой стрик и его потолок на характеристику */
    const val STREAK_BONUS_PER_HABIT = 5.0
    const val STREAK_BONUS_MAX = 15.0
    const val STREAK_MIN_FOR_BONUS = 7

    /**
     * Итоговая карта характеристик 0..100 (ключи PHYSICS/MIND/MONEY/SOCIAL/
     * DISCIPLINE). questKeyXp — XP по questKey за последние 30 дней
     * (в профиле: [ProgressionEngine.keyXp] по закрытым за окно квестам).
     */
    fun characteristics(
        habits: List<Habit>,
        checks: List<HabitCheck>,
        questKeyXp: Map<String, Int>,
        today: Long,
    ): Map<String, Int> {
        val habitScores = habitScores(habits, checks, today)
        val questScores = questScores(questKeyXp)
        val result = LinkedHashMap<String, Int>()
        for (key in HabitEngine.CHARACTERISTICS) {
            val habit = habitScores[key]
            val quest = questScores[key] ?: 0.0
            val hasQuestActivity = (questKeyXp[key] ?: 0) > 0
            val score = when {
                habit == null && !hasQuestActivity -> 0.0
                habit == null -> quest
                !hasQuestActivity -> habit
                else -> WEIGHT_HABITS * habit + WEIGHT_QUESTS * quest
            }
            result[key] = score.roundToInt().coerceIn(0, 100)
        }
        return result
    }

    /**
     * Вклад привычек 0..100 по характеристикам: среднее по привычкам сферы
     * взвешенной 4-недельной консистентности + бонус за стрики (кап 100).
     * Сфера без единой посчитанной привычки отсутствует в карте — вызывающий
     * код решает fallback.
     */
    fun habitScores(habits: List<Habit>, checks: List<HabitCheck>, today: Long): Map<String, Double> {
        val checksByHabit = checks.groupBy { it.habitId }
        val result = LinkedHashMap<String, Double>()
        for (key in HabitEngine.CHARACTERISTICS) {
            val habitsOfKey = habits.filter { it.characteristic == key }
            if (habitsOfKey.isEmpty()) continue
            val weighted = habitsOfKey.mapNotNull { habit ->
                weeklyWeightedConsistency(habit, checksByHabit[habit.id].orEmpty(), today)
            }
            if (weighted.isEmpty()) continue
            var bonus = 0.0
            for (habit in habitsOfKey) {
                val (current) = HabitEngine.streak(habit, checksByHabit[habit.id].orEmpty(), today)
                if (current >= STREAK_MIN_FOR_BONUS) bonus += STREAK_BONUS_PER_HABIT
            }
            val raw = weighted.average() * 100 + kotlin.math.min(bonus, STREAK_BONUS_MAX)
            result[key] = raw.coerceIn(0.0, 100.0)
        }
        return result
    }

    /**
     * Вклад квестов/задач 0..100: доля XP характеристики за окно от [QUEST_XP_CAP]
     * (по духу v2 — XP по ключам, как ProgressionEngine.keyXp, только нормирован).
     */
    fun questScores(questKeyXp: Map<String, Int>): Map<String, Double> {
        val result = LinkedHashMap<String, Double>()
        for (key in HabitEngine.CHARACTERISTICS) {
            val xp = questKeyXp[key] ?: 0
            result[key] = (xp.toDouble() / QUEST_XP_CAP * 100).coerceIn(0.0, 100.0)
        }
        return result
    }

    /**
     * Взвешенная 4-недельная консистентность привычки 0..1: недели считаются
     * назад от [today] окнами по 7 дней (неделя 0 — последние 7 дней включая
     * сегодня), веса 4/3/2/1 — ближняя неделя тяжелее. Неделя, начавшаяся
     * раньше создания привычки, в среднее не входит (привычки тогда не было).
     * Ни одной посчитанной недели → null (привычка не даёт вклада).
     */
    fun weeklyWeightedConsistency(habit: Habit, checks: List<HabitCheck>, today: Long): Double? {
        var weighted = 0.0
        var weights = 0
        for (k in 0 until WEEKS) {
            val weekStart = today - 6 - 7L * k
            if (habit.createdAt > weekStart + 6) break // дальше все недели до создания
            val weight = (WEEKS - k).toDouble()
            weighted += weight * HabitEngine.weeklyConsistency(habit, checks, weekStart)
            weights += WEEKS - k
        }
        return if (weights == 0) null else weighted / weights
    }
}
