package com.questline.app.domain.habits

import com.questline.app.data.habits.Habit
import com.questline.app.data.habits.HabitCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * T-13: формула характеристик v3 — 4-недельные веса, стрик-бонус,
 * слияние 0.65 привычки + 0.35 квесты, fallback'и пустых источников.
 *
 * Ориентир дней: today = 200 (понедельник); недели назад от него:
 * неделя 0 = 194..200, неделя 1 = 187..193, неделя 2 = 180..186, неделя 3 = 173..179.
 */
class CharacteristicEngineTest {

    private val today = 200L

    private fun habit(
        id: Long = 1,
        characteristic: String = "PHYSICS",
        scheduleType: String = "DAILY",
        timesPerWeek: Int = 3,
        createdAt: Long = 172L,
    ) = Habit(
        id = id,
        title = "Тест",
        characteristic = characteristic,
        scheduleType = scheduleType,
        timesPerWeek = timesPerWeek,
        createdAt = createdAt,
    )

    private fun checks(days: LongRange, habitId: Long = 1): List<HabitCheck> =
        days.map { HabitCheck(habitId = habitId, epochDay = it, createdAtMillis = it) }

    // ---------------- Чистые привычки ----------------

    @Test
    fun pureHabits_fourWeeksOfChecks_habitScoreNearHundred() {
        val habit = habit(createdAt = 172L) // создана до старта недели 3
        val all = checks(172L..200L)
        assertEquals(100.0, CharacteristicEngine.habitScores(listOf(habit), all, today)["PHYSICS"]!!, 0.01)
        // Квестовой активности нет — привычки не размываются, сфера максимум
        assertEquals(100, CharacteristicEngine.characteristics(listOf(habit), all, emptyMap(), today)["PHYSICS"])
    }

    @Test
    fun noHabits_fallbackToQuestScore() {
        // Пустые привычки: радар v2.8 не обнуляется, живёт на квестах
        val result = CharacteristicEngine.characteristics(emptyList(), emptyList(), mapOf("PHYSICS" to 250), today)
        assertEquals(50, result["PHYSICS"]) // 250 из капа 500 → 50
        assertEquals(0, result["MIND"])
        assertEquals(0, result["MONEY"])
        assertEquals(0, result["SOCIAL"])
        assertEquals(0, result["DISCIPLINE"])
    }

    // ---------------- Слияние источников ----------------

    @Test
    fun bothSources_blendIs65Habits35Quests() {
        // Привычка с одной отметкой: неделя 0 = 1.0 (единственный запланированный
        // день 197 = ПТ закрыт), недель 1–3 ещё не существует → вклад 100 без бонуса
        val habit = habit(scheduleType = "WEEKDAYS", createdAt = 194L)
        val mask = 1 shl (((197L + 3) % 7).toInt()) // маска: запланирован только день 197
        val marked = habit.copy(weekdaysMask = mask)
        val checks = checks(197L..197L)
        // habitScore = 100 (без стрик-бонуса: серия 1), квесты 250/500 → 50
        val expected = (0.65 * 100.0 + 0.35 * 50.0).roundToInt() // = 83
        assertEquals(
            expected,
            CharacteristicEngine.characteristics(listOf(marked), checks, mapOf("PHYSICS" to 250), today)["PHYSICS"],
        )
    }

    // ---------------- Недельные веса ----------------

    @Test
    fun weeklyWeights_lastWeekCountsFourTimesFirst() {
        val habit = habit(createdAt = 172L)
        // Отметки только в неделе 0 (194..200): вес 4 из 10 → 0.4
        val lastWeekOnly = CharacteristicEngine.weeklyWeightedConsistency(habit, checks(194L..200L), today)!!
        assertEquals(0.4, lastWeekOnly, 1e-9)
        // Отметки только в неделе 3 (173..179): вес 1 из 10 → 0.1
        val oldestWeekOnly = CharacteristicEngine.weeklyWeightedConsistency(habit, checks(173L..179L), today)!!
        assertEquals(0.1, oldestWeekOnly, 1e-9)
        assertTrue(lastWeekOnly > oldestWeekOnly)
    }

    @Test
    fun streakBonus_addedToHabitScore() {
        val habit = habit(createdAt = 172L)
        // Консистентность 0.4 (отметки недели 0), серия 7 живая → 40 + 5 = 45
        val score = CharacteristicEngine.habitScores(listOf(habit), checks(194L..200L), today)["PHYSICS"]!!
        assertEquals(45.0, score, 0.01)
    }

    @Test
    fun streakBonus_neverPushesOverHundred() {
        val habit = habit(createdAt = 172L)
        // Идеальные 4 недели: консистентность 100 + бонус за серию 29 → кап 100
        val score = CharacteristicEngine.habitScores(listOf(habit), checks(172L..200L), today)["PHYSICS"]!!
        assertEquals(100.0, score, 0.01)
        assertTrue(score <= 100.0)
    }

    // ---------------- Пять характеристик ----------------

    @Test
    fun fiveCharacteristics_countedIndependently() {
        val physics = habit(id = 1, characteristic = "PHYSICS", createdAt = 172L)
        val mind = habit(id = 2, characteristic = "MIND", scheduleType = "TIMES_PER_WEEK", timesPerWeek = 2, createdAt = 194L)
        val all = checks(172L..200L, habitId = 1) + checks(195L..195L, habitId = 2)
        val questXp = mapOf("MONEY" to 250, "SOCIAL" to 1000)
        val result = CharacteristicEngine.characteristics(listOf(physics, mind), all, questXp, today)
        assertEquals(100, result["PHYSICS"]) // идеальная привычка, квестов нет
        assertEquals(50, result["MIND"])     // 1 из 2 в неделю → 50, квестов нет
        assertEquals(50, result["MONEY"])    // только квесты 250/500
        assertEquals(100, result["SOCIAL"])  // квесты выше капа
        assertEquals(0, result["DISCIPLINE"])// источников нет
    }

    // ---------------- Квесты ----------------

    @Test
    fun questScore_cappedAtHundred() {
        assertEquals(100.0, CharacteristicEngine.questScores(mapOf("SOCIAL" to 99_999))["SOCIAL"]!!, 0.01)
        val result = CharacteristicEngine.characteristics(emptyList(), emptyList(), mapOf("SOCIAL" to 99_999), today)
        assertEquals(100, result["SOCIAL"])
    }

    // ---------------- Гибкое расписание ----------------

    @Test
    fun timesPerWeek_targetIsDenominatorOfConsistency() {
        // Цель 3 в неделю, 3 отметки в неделе 0 → 1.0; неделя 1 пустая → 0.0
        val habit = habit(scheduleType = "TIMES_PER_WEEK", timesPerWeek = 3, createdAt = 187L)
        val weighted = CharacteristicEngine.weeklyWeightedConsistency(habit, checks(195L..197L), today)!!
        assertEquals(4.0 / 7.0, weighted, 1e-9)
        assertFalse(weighted > 1.0)
    }
}
