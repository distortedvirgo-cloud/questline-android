package com.questline.app.domain.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Прогноз темпа копилки (T-11) */
class GoalPaceTest {

    @Test
    fun `темпа нет - прогноза нет`() {
        assertNull(goalPace(savedMinor = 1_000L, targetMinor = 10_000L, depositsLast30DaysMinor = 0L))
    }

    @Test
    fun `отрицательных значений не бывает - прогноза нет`() {
        assertNull(goalPace(1_000L, 10_000L, -1L))
        assertNull(goalPace(-1L, 10_000L, 5_000L))
        assertNull(goalPace(1_000L, 0L, 5_000L))
    }

    @Test
    fun `равномерный темп - точный прогноз в днях`() {
        // 3 000 000 копеек за 30 дней = 100 000/день; осталось столько же → 30 дней
        assertEquals(30L, goalPace(0L, 3_000_000L, 3_000_000L))
    }

    @Test
    fun `частичная цель - прогноз по остатку с округлением вверх`() {
        // темп 90 000/день, осталось 2 300 000 → 25,55 → 26 дней
        assertEquals(26L, goalPace(700_000L, 3_000_000L, 2_700_000L))
    }

    @Test
    fun `цель уже достигнута - ноль дней`() {
        assertEquals(0L, goalPace(10_000L, 10_000L, 5_000L))
        assertEquals(0L, goalPace(12_000L, 10_000L, 5_000L))
    }
}
