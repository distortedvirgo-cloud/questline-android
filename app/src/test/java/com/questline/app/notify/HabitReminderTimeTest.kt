package com.questline.app.notify

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * N-01: расчёт следующего триггера напоминания (чистая логика,
 * без Android-зависимостей). Зона фиксирована — UTC, минуты дня отсчитываются
 * от полуночи зоны устройства.
 */
class HabitReminderTimeTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    private fun utc(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `утро до времени напоминания — сегодня`() {
        val now = utc(2026, 6, 15, 7, 0)
        assertEquals(utc(2026, 6, 15, 20, 0), HabitReminderScheduler.nextTriggerMillis(20 * 60, now, zone))
    }

    @Test
    fun `вечер после времени напоминания — завтра`() {
        val now = utc(2026, 6, 15, 21, 30)
        assertEquals(utc(2026, 6, 16, 9, 0), HabitReminderScheduler.nextTriggerMillis(9 * 60, now, zone))
    }

    @Test
    fun `ровно в минуту триггера — завтра`() {
        val now = utc(2026, 6, 15, 9, 0)
        assertEquals(utc(2026, 6, 16, 9, 0), HabitReminderScheduler.nextTriggerMillis(9 * 60, now, zone))
    }

    @Test
    fun `полночь с напоминанием в ноль часов — завтра`() {
        val now = utc(2026, 6, 15, 0, 0)
        assertEquals(utc(2026, 6, 16, 0, 0), HabitReminderScheduler.nextTriggerMillis(0, now, zone))
    }

    @Test
    fun `конец месяца — переход в следующий месяц`() {
        val now = utc(2026, 6, 30, 12, 0)
        assertEquals(utc(2026, 7, 1, 8, 0), HabitReminderScheduler.nextTriggerMillis(8 * 60, now, zone))
    }
}
