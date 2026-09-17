package com.questline.app.domain.habits

import com.questline.app.data.habits.Habit
import com.questline.app.data.habits.HabitCheck
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * N-04 «Never miss twice»: подсказка показывается, когда привычка запланирована
 * и вчера, и сегодня — и оба дня остались без отметки. Заморозка и количественный
 * чек ниже цели трактуются по правилам стрика (HabitEngine.isSuccess).
 */
class MissedTwiceTest {

    private fun habit(
        scheduleType: String = "DAILY",
        targetValue: Double? = null,
        createdAt: Long = 95L,
    ) = Habit(
        title = "Тест",
        characteristic = "PHYSICS",
        scheduleType = scheduleType,
        weekdaysMask = 0b1111111,
        timesPerWeek = 3,
        intervalDays = 3,
        targetValue = targetValue,
        createdAt = createdAt,
    )

    private fun check(day: Long, value: Double? = null, frozen: Boolean = false) =
        HabitCheck(habitId = 1, epochDay = day, value = value, createdAtMillis = day, frozen = frozen)

    private val today = 110L

    @Test
    fun dueBothDays_noChecks_true() {
        assertEquals(true, HabitEngine.missedTwice(habit(), emptyList(), today))
    }

    @Test
    fun yesterdayChecked_false() {
        val checks = listOf(check(today - 1))
        assertEquals(false, HabitEngine.missedTwice(habit(), checks, today))
    }

    @Test
    fun todayChecked_false() {
        val checks = listOf(check(today))
        assertEquals(false, HabitEngine.missedTwice(habit(), checks, today))
    }

    @Test
    fun yesterdayFrozen_isKeptDay_false() {
        val checks = listOf(check(today - 1, frozen = true))
        assertEquals(false, HabitEngine.missedTwice(habit(), checks, today))
    }

    @Test
    fun quantitative_belowTargetYesterday_true() {
        // Вчерашний чек есть, но ниже цели — это пропуск
        val habit = habit(targetValue = 8.0)
        val checks = listOf(check(today - 1, value = 3.0))
        assertEquals(true, HabitEngine.missedTwice(habit, checks, today))
    }

    @Test
    fun quantitative_reachedTargetYesterday_false() {
        val habit = habit(targetValue = 8.0)
        val checks = listOf(check(today - 1, value = 8.0))
        assertEquals(false, HabitEngine.missedTwice(habit, checks, today))
    }

    @Test
    fun timesPerWeek_false() {
        // У гибкой цели нет запланированных дней — подсказка не показывается
        assertEquals(false, HabitEngine.missedTwice(habit(scheduleType = "TIMES_PER_WEEK"), emptyList(), today))
    }

    @Test
    fun yesterdayNotDue_false() {
        // INTERVAL 3 дня от createdAt=95: due 95, 98, 101, 104, 107, 110… → вчера (109) не запланирован
        val habit = habit(scheduleType = "INTERVAL", createdAt = 95L)
        assertEquals(false, HabitEngine.missedTwice(habit, emptyList(), today))
    }

    @Test
    fun createdYesterday_missedBoth_true() {
        // Привычка заведена вчера, не отмечена ни вчера, ни сегодня — подсказка уместна
        val habit = habit(createdAt = today - 1)
        assertEquals(true, HabitEngine.missedTwice(habit, emptyList(), today))
    }

    @Test
    fun createdToday_yesterdayWasNotCommitment_false() {
        val habit = habit(createdAt = today)
        assertEquals(false, HabitEngine.missedTwice(habit, emptyList(), today))
    }
}
