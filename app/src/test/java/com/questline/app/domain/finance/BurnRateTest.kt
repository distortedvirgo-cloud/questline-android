package com.questline.app.domain.finance

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T-08: чистая математика burn rate. FAST — быстрее плана более чем на
 * 10 п.п., OVER — месячный план потрачен до конца месяца.
 */
class BurnRateTest {

    @Test
    fun burnRate_onPace_isCalm() {
        val rate = burnRate(spentMinor = 50_000, planMinor = 100_000, dayOfMonth = 15, daysInMonth = 30)
        assertEquals(BurnRate.Status.CALM, rate.status)
        assertEquals(0, rate.overspendPercent)
    }

    @Test
    fun burnRate_tenPercentAhead_isStillCalm() {
        val rate = burnRate(spentMinor = 43_000, planMinor = 100_000, dayOfMonth = 10, daysInMonth = 30)
        assertEquals(BurnRate.Status.CALM, rate.status)
    }

    @Test
    fun burnRate_moreThanTenPercentAhead_isFast() {
        val rate = burnRate(spentMinor = 62_000, planMinor = 100_000, dayOfMonth = 15, daysInMonth = 30)
        assertEquals(BurnRate.Status.FAST, rate.status)
        assertEquals(12, rate.overspendPercent)
    }

    @Test
    fun burnRate_wholePlanSpentBeforeMonthEnds_isOver() {
        val rate = burnRate(spentMinor = 100_000, planMinor = 100_000, dayOfMonth = 15, daysInMonth = 30)
        assertEquals(BurnRate.Status.OVER, rate.status)
    }

    @Test
    fun burnRate_planExceeded_isOverWithDelta() {
        val rate = burnRate(spentMinor = 120_000, planMinor = 100_000, dayOfMonth = 20, daysInMonth = 30)
        assertEquals(BurnRate.Status.OVER, rate.status)
        assertEquals(54, rate.overspendPercent)
    }

    @Test
    fun burnRate_exactPlanAtMonthEnd_isNotOver() {
        val rate = burnRate(spentMinor = 100_000, planMinor = 100_000, dayOfMonth = 31, daysInMonth = 31)
        assertEquals(BurnRate.Status.CALM, rate.status)
    }

    @Test
    fun burnRate_noPlan_isCalm() {
        val rate = burnRate(spentMinor = 5_000, planMinor = 0, dayOfMonth = 10, daysInMonth = 30)
        assertEquals(BurnRate.Status.CALM, rate.status)
        assertEquals(0, rate.overspendPercent)
    }

    @Test
    fun burnRate_nothingSpent_isCalmWithZeroDelta() {
        val rate = burnRate(spentMinor = 0, planMinor = 100_000, dayOfMonth = 25, daysInMonth = 30)
        assertEquals(BurnRate.Status.CALM, rate.status)
        assertEquals(0, rate.overspendPercent)
    }
}
