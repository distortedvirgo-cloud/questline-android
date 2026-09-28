package com.questline.app.domain.finance

import kotlin.math.abs

/** Окно поиска зеркальной пары: дельта на карте-приёмнике считается парой, если зафиксирована за последние 24 часа. */
const val MIRROR_WINDOW_MILLIS = 86_400_000L

/**
 * Эвристики автопоправки баланса после перевода себе: пуш перевода парсится
 * как расход, баланс уезжает на сумму перевода. Суммы — minor units
 * (копейки), время — epoch millis. Чистые функции, без android.
 */

/**
 * Сколько изменения баланса НЕ объяснено только что подтверждёнными операциями:
 * пуш сообщил [pushedBalanceMinor], в приложении [displayedBalanceMinor],
 * а подтверждённые операции должны были сдвинуть баланс на [confirmedNetMinor].
 */
fun unexplainedDelta(
    pushedBalanceMinor: Long,
    displayedBalanceMinor: Long,
    confirmedNetMinor: Long,
): Long = pushedBalanceMinor - displayedBalanceMinor - confirmedNetMinor

/**
 * Зеркальная пара: [a] и [b] почти противоположны (сумма в пределах ±100 копеек
 * от нуля) и хотя бы одна не нулевая.
 */
fun isMirror(a: Long, b: Long): Boolean = a != 0L && abs(a + b) <= 100L

/**
 * Найти среди [others] карту с зеркальной дельтой к [delta]: (cardLast4, deltaMinor, millis).
 * Дельта должна быть зафиксирована не раньше, чем [windowMillis] назад от [nowMillis].
 */
fun findMirror(
    delta: Long,
    others: List<Triple<String, Long, Long>>,
    windowMillis: Long,
    nowMillis: Long,
): String? = others.firstOrNull { (_, otherDelta, millis) ->
    nowMillis - millis in 0L..windowMillis && isMirror(delta, otherDelta)
}?.first
