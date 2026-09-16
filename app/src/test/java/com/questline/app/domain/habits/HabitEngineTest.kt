package com.questline.app.domain.habits

import com.questline.app.data.habits.Habit
import com.questline.app.data.habits.HabitCheck
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T-03: чистая математика привычек — расписания, стрики, консистентность,
 * диагностика, XP. Без Robolectric: домен не зависит от Android.
 *
 * Ориентиры дней: epochDay 95 = понедельник, 100 = суббота, 101 = воскресенье
 * ((epochDay + 3) % 7 → 0 = ПН ... 6 = ВС).
 */
class HabitEngineTest {

    private fun habit(
        title: String = "Тест",
        characteristic: String = "PHYSICS",
        scheduleType: String = "DAILY",
        weekdaysMask: Int = 0b1111111,
        timesPerWeek: Int = 3,
        intervalDays: Int = 3,
        targetValue: Double? = null,
        createdAt: Long = 95L,
    ) = Habit(
        title = title,
        characteristic = characteristic,
        scheduleType = scheduleType,
        weekdaysMask = weekdaysMask,
        timesPerWeek = timesPerWeek,
        intervalDays = intervalDays,
        targetValue = targetValue,
        createdAt = createdAt,
    )

    private fun check(day: Long, value: Double? = null, frozen: Boolean = false) =
        HabitCheck(habitId = 1, epochDay = day, value = value, createdAtMillis = day, frozen = frozen)

    // ---------------- isDue: 4 типа расписаний ----------------

    @Test
    fun isDue_daily_trueEveryDay() {
        val habit = habit(scheduleType = "DAILY")
        assertEquals(true, HabitEngine.isDue(habit, 95L))
        assertEquals(true, HabitEngine.isDue(habit, 101L))
        assertEquals(true, HabitEngine.isDue(habit, 100_500L))
    }

    @Test
    fun isDue_weekdays_byMaskBits() {
        // Бит 0 = ПН ... бит 6 = ВС; маска 0b0111110 = ВТ..СБ
        val habit = habit(scheduleType = "WEEKDAYS", weekdaysMask = 0b0111110)
        assertEquals(false, HabitEngine.isDue(habit, 95L))  // ПН
        assertEquals(true, HabitEngine.isDue(habit, 96L))   // ВТ
        assertEquals(true, HabitEngine.isDue(habit, 99L))   // ПТ
        assertEquals(true, HabitEngine.isDue(habit, 100L))  // СБ
        assertEquals(false, HabitEngine.isDue(habit, 101L)) // ВС
    }

    @Test
    fun isDue_timesPerWeek_anyDayAndWeekTarget() {
        // Гибкая цель: конкретный день заранее не назначен — отмечать можно в любой
        val habit = habit(scheduleType = "TIMES_PER_WEEK", timesPerWeek = 3)
        assertEquals(true, HabitEngine.isDue(habit, 95L))
        assertEquals(true, HabitEngine.isDue(habit, 101L))
        assertEquals(3, HabitEngine.weekTarget(habit))
    }

    @Test
    fun isDue_interval_anchorIsCreationDay() {
        // Каждые 3 дня от якоря = день создания: 100, 103, 106 ... (95 — вне якоря)
        val habit = habit(scheduleType = "INTERVAL", intervalDays = 3, createdAt = 100L)
        assertEquals(true, HabitEngine.isDue(habit, 100L))
        assertEquals(false, HabitEngine.isDue(habit, 101L))
        assertEquals(false, HabitEngine.isDue(habit, 102L))
        assertEquals(true, HabitEngine.isDue(habit, 103L))
        assertEquals(true, HabitEngine.isDue(habit, 106L))
    }

    // ---------------- streak ----------------

    @Test
    fun streak_growsOnSuccessfulChecks() {
        val habit = habit()
        val checks = listOf(check(98L), check(99L))
        assertEquals(2 to 2, HabitEngine.streak(habit, checks, today = 100L))
    }

    @Test
    fun streak_todayCheckedExtendsTodayUncheckedDoesNotBreak() {
        val habit = habit()
        // Сегодня уже отмечен — серия растёт
        assertEquals(3 to 3, HabitEngine.streak(habit, listOf(check(98L), check(99L), check(100L)), today = 100L))
        // Сегодня ещё не отмечен — день не кончился, стрик не рвётся
        assertEquals(2 to 2, HabitEngine.streak(habit, listOf(check(98L), check(99L)), today = 100L))
    }

    @Test
    fun streak_freeGapBridgesOncePerWindow() {
        // Серия 95..99, один пропуск 100 — бесплатно мостится, стрик живёт
        val habit = habit()
        val checks = (95L..99L).map { check(it) }
        assertEquals(5 to 5, HabitEngine.streak(habit, checks, today = 101L))
    }

    @Test
    fun streak_secondMissWithinWindowBreaks() {
        // Два пропуска в окне 7 запланированных дней: второй рвёт серию
        val habit = habit()
        val checks = listOf(check(95L), check(96L), check(97L), check(98L), check(99L))
        assertEquals(0 to 5, HabitEngine.streak(habit, checks, today = 102L))
    }

    @Test
    fun streak_frozenBridgesButDoesNotExtend() {
        // Заморозка 100 мостит серию (стрикт не рвётся), но не удлиняет её
        val habit = habit()
        val checks = (95L..99L).map { check(it) } + check(100L, frozen = true)
        assertEquals(5 to 5, HabitEngine.streak(habit, checks, today = 101L))
    }

    @Test
    fun streak_frozenDayIsNotAMiss() {
        // Заморозка не считается пропуском: пропуск 99 после замороженного 98
        // мостится бесплатно, и серия не рвётся; чек 100 продолжает до 4
        val habit = habit()
        val checks = listOf(check(95L), check(96L), check(97L), check(98L, frozen = true), check(100L))
        assertEquals(4 to 4, HabitEngine.streak(habit, checks, today = 100L))
    }

    @Test
    fun streak_quantitative_requiresTargetValue() {
        val habit = habit(targetValue = 30.0)
        // 95 ✓ (30.0), 96 ниже цели (пропуск, бесплатный мост), 97 ниже цели — второй пропуск рвёт
        val checks = listOf(check(95L, 30.0), check(96L, 29.0), check(97L, 29.0))
        assertEquals(0 to 1, HabitEngine.streak(habit, checks, today = 98L))
    }

    @Test
    fun streak_interval_walksPlannedDaysFromAnchor() {
        val habit = habit(scheduleType = "INTERVAL", intervalDays = 3, createdAt = 100L)
        val checks = listOf(check(100L), check(103L), check(106L))
        assertEquals(3 to 3, HabitEngine.streak(habit, checks, today = 106L))
    }

    @Test
    fun streak_timesPerWeek_seriesOfChecks() {
        val habit = habit(scheduleType = "TIMES_PER_WEEK", timesPerWeek = 3)
        val checks = listOf(check(96L), check(98L), check(99L, frozen = true), check(100L))
        assertEquals(3 to 3, HabitEngine.streak(habit, checks, today = 100L))
    }

    // ---------------- weeklyConsistency ----------------

    @Test
    fun weeklyConsistency_daily_frozenCountsAsDone() {
        val habit = habit(createdAt = 90L)
        val checks = listOf(check(100L), check(101L, frozen = true), check(102L))
        assertEquals(3.0 / 7.0, HabitEngine.weeklyConsistency(habit, checks, weekStartEpochDay = 100L), 1e-9)
    }

    @Test
    fun weeklyConsistency_timesPerWeek_denominatorIsTargetCappedByWeek() {
        val habit = habit(scheduleType = "TIMES_PER_WEEK", timesPerWeek = 3, createdAt = 90L)
        // 4 отметки при цели 3 — доля капается единицей
        val checks = listOf(check(100L), check(101L), check(102L), check(103L))
        assertEquals(1.0, HabitEngine.weeklyConsistency(habit, checks, weekStartEpochDay = 100L), 1e-9)
        // 1 отметка при цели 3
        assertEquals(1.0 / 3.0, HabitEngine.weeklyConsistency(habit, listOf(check(100L)), 100L), 1e-9)
    }

    @Test
    fun weeklyConsistency_skipsDaysBeforeCreation() {
        // Привычка создана в середине недели: обязательны только дни с createdAt
        val habit = habit(createdAt = 103L)
        val checks = listOf(check(103L), check(104L))
        assertEquals(2.0 / 4.0, HabitEngine.weeklyConsistency(habit, checks, weekStartEpochDay = 100L), 1e-9)
    }

    @Test
    fun consistencyByCharacteristic_averagesPerKey() {
        val physics = habit(characteristic = "PHYSICS", createdAt = 90L).copy(id = 1L)
        val mind = habit(title = "Чтение", characteristic = "MIND", createdAt = 90L).copy(id = 2L)
        val checks = listOf(check(100L), check(101L))
        val map = HabitEngine.consistencyByCharacteristic(listOf(physics, mind), checks, 100L)
        assertEquals(2.0 / 7.0 * 100, map["PHYSICS"]!!, 1e-9)
        assertEquals(0.0, map["MIND"]!!, 1e-9)
        assertEquals(0.0, map["MONEY"]!!, 1e-9)
        assertEquals(5, map.size)
    }

    // ---------------- diagnose ----------------

    @Test
    fun diagnose_shrink_below40() {
        val habit = habit(createdAt = 0L)
        // 4 успешных дня из 14 запланированных (0..13) ≈ 29%
        val checks = (10L..13L).map { check(it) }
        assertEquals(HabitEngine.Diagnosis.SHRINK, HabitEngine.diagnose(habit, checks, today = 13L))
    }

    @Test
    fun diagnose_anchor_40to70() {
        val habit = habit(createdAt = 0L)
        // 8 из 14 ≈ 57%
        val checks = (6L..13L).map { check(it) }
        assertEquals(HabitEngine.Diagnosis.ANCHOR, HabitEngine.diagnose(habit, checks, today = 13L))
    }

    @Test
    fun diagnose_grow_90AndThreeWeeks() {
        val habit = habit(createdAt = 0L)
        // Все 21 день подряд: 14 дней ≥90% и 3 недели ≥90%
        val checks = (0L..20L).map { check(it) }
        assertEquals(HabitEngine.Diagnosis.GROW, HabitEngine.diagnose(habit, checks, today = 20L))
    }

    @Test
    fun diagnose_none_between70And90_orGrowthTooYoung() {
        val habit = habit(createdAt = 0L)
        // 11 из 14 ≈ 79% — в мёртвой зоне порогов
        val checks11 = (3L..13L).map { check(it) }
        assertEquals(HabitEngine.Diagnosis.NONE, HabitEngine.diagnose(habit, checks11, today = 13L))
        // 14 из 14 за 14 дней, но 15 из 21 за три недели — рано для GROW
        val checks15 = (0L..20L).filter { it >= 7L || it == 3L }.map { check(it) }
        assertEquals(HabitEngine.Diagnosis.NONE, HabitEngine.diagnose(habit, checks15, today = 20L))
    }

    // ---------------- XP ----------------

    @Test
    fun xpForHabit_byComplexity() {
        assertEquals(5, HabitEngine.xpForHabit("S"))
        assertEquals(8, HabitEngine.xpForHabit("M"))
        assertEquals(10, HabitEngine.xpForHabit("L"))
    }

    @Test
    fun habitXpTodayCapped_18Plus5FillsUpTo20() {
        // Уже 18 XP за день, planned 5 → засчитывается только 2 (суммарно 20)
        assertEquals(2, HabitEngine.habitXpTodayCapped(alreadyToday = 18, planned = 5))
        assertEquals(5, HabitEngine.habitXpTodayCapped(alreadyToday = 0, planned = 5))
        assertEquals(0, HabitEngine.habitXpTodayCapped(alreadyToday = 20, planned = 5))
    }

    // ---------------- Вехи стрика (T-07) ----------------

    @Test
    fun milestoneBonuses_byTier() {
        assertEquals(10, HabitEngine.milestoneCoinBonus(7))
        assertEquals(50, HabitEngine.milestoneCoinBonus(30))
        assertEquals(150, HabitEngine.milestoneCoinBonus(100))
        assertEquals(5, HabitEngine.milestoneXpBonus(7))
        assertEquals(10, HabitEngine.milestoneXpBonus(30))
        assertEquals(20, HabitEngine.milestoneXpBonus(100))
    }

    // ---------------- Заморозка: день-кандидат (T-07) ----------------

    @Test
    fun lastMissedDueDay_daily_returnsYesterdayWhenUnchecked() {
        val habit = habit(scheduleType = "DAILY", createdAt = 90L)
        assertEquals(100L, HabitEngine.lastMissedDueDay(habit, emptyList(), today = 101L))
    }

    @Test
    fun lastMissedDueDay_checkedOrFrozenYesterday_returnsNull() {
        val habit = habit(scheduleType = "DAILY", createdAt = 90L)
        // Вчерашний чек — кандидата нет
        assertEquals(null, HabitEngine.lastMissedDueDay(habit, listOf(check(100L)), today = 101L))
        // Вчерашняя заморозка — тоже нет
        assertEquals(null, HabitEngine.lastMissedDueDay(habit, listOf(check(100L, frozen = true)), today = 101L))
    }

    @Test
    fun lastMissedDueDay_weekdays_skipsUnscheduledDays() {
        // Маска 0b0101100 = СР, ЧТ, СБ; today 100 = СБ, вчера ПТ не запланирован
        val habit = habit(scheduleType = "WEEKDAYS", weekdaysMask = 0b0101100, createdAt = 90L)
        assertEquals(98L, HabitEngine.lastMissedDueDay(habit, emptyList(), today = 100L))
    }

    @Test
    fun lastMissedDueDay_stopsAtCreationAndTimesPerWeek() {
        // Привычка создана сегодня — пропущенных запланированных дней нет
        val fresh = habit(scheduleType = "DAILY", createdAt = 101L)
        assertEquals(null, HabitEngine.lastMissedDueDay(fresh, emptyList(), today = 101L))
        // TIMES_PER_WEEK запланированных дней не имеет — заморозка не применима
        val flex = habit(scheduleType = "TIMES_PER_WEEK", timesPerWeek = 3, createdAt = 90L)
        assertEquals(null, HabitEngine.lastMissedDueDay(flex, emptyList(), today = 101L))
    }
}
