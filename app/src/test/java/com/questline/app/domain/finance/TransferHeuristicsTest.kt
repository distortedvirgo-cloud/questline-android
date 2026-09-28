package com.questline.app.domain.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Эвристики перевода себе: необъяснённая дельта баланса, зеркальная пара
 * дельт на картах. Все суммы — minor units, окно поиска пары — 24 ч.
 */
class TransferHeuristicsTest {

    private val windowMillis = 86_400_000L
    private val now = 1_700_000_000_000L

    @Test
    fun unexplainedDelta_allExplained_isZero() {
        val delta = unexplainedDelta(
            pushedBalanceMinor = 200_000,
            displayedBalanceMinor = 185_000,
            confirmedNetMinor = 15_000,
        )
        assertEquals(0, delta)
    }

    @Test
    fun unexplainedDelta_pushRicher_isPositive() {
        // Пуш показал больше, чем дают операции: 15 000 объяснено, 15 000 ₽ — нет.
        val delta = unexplainedDelta(
            pushedBalanceMinor = 200_000,
            displayedBalanceMinor = 170_000,
            confirmedNetMinor = 15_000,
        )
        assertEquals(15_000, delta)
    }

    @Test
    fun unexplainedDelta_pushPoorer_isNegative() {
        val delta = unexplainedDelta(
            pushedBalanceMinor = 200_000,
            displayedBalanceMinor = 230_000,
            confirmedNetMinor = 15_000,
        )
        assertEquals(-45_000, delta)
    }

    @Test
    fun isMirror_exactPair_isTrue() {
        assertTrue(isMirror(-100_000, 100_000))
        assertTrue(isMirror(100_000, -100_000))
    }

    @Test
    fun isMirror_withinHundredKopecks_isTrue() {
        // Сумма −100 копеек — на границе допуска, ещё пара.
        assertTrue(isMirror(-100_000, 99_900))
    }

    @Test
    fun isMirror_beyondHundredKopecks_isFalse() {
        assertFalse(isMirror(-100_000, 99_800))
    }

    @Test
    fun isMirror_notAPair_orZero_isFalse() {
        assertFalse(isMirror(50_000, 70_000))
        assertFalse(isMirror(0, 0))
        assertFalse(isMirror(0, -500))
    }

    @Test
    fun findMirror_freshOppositeDelta_findsCard() {
        val others = listOf(Triple("7612", 100_000L, now - 3_600_000L))
        assertEquals("7612", findMirror(-100_000, others, windowMillis, now))
    }

    @Test
    fun findMirror_staleDelta_ignored() {
        // Пара есть, но зафиксирована на миллисекунду позже окна — не считается.
        val others = listOf(
            Triple("7612", 5_000L, now - 1_000L),
            Triple("8153", 100_000L, now - windowMillis - 1),
        )
        assertNull(findMirror(-100_000, others, windowMillis, now))
    }

    @Test
    fun findMirror_noOppositeDelta_returnsNull() {
        val others = listOf(Triple("7612", 5_000L, now - 1_000L))
        assertNull(findMirror(-100_000, others, windowMillis, now))
        assertNull(findMirror(-100_000, emptyList(), windowMillis, now))
    }
}
