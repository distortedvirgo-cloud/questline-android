package com.questline.app.domain.finance

import kotlin.math.ceil

/**
 * Прогноз копилки (T-11): сколько дней осталось до цели при текущем темпе.
 * Чистая функция — тестируется отдельно, UI только форматирует результат.
 *
 * Темп = сумма пополнений за последние 30 дней (minor units), равномерно
 * распределённая на окно. Возвращает:
 * - null — прогноза нет (некорректные входы или пополнений не было);
 * - 0 — цель уже достигнута;
 * - >0 — дней до цели, с округлением вверх.
 */
fun goalPace(
    savedMinor: Long,
    targetMinor: Long,
    depositsLast30DaysMinor: Long,
): Long? {
    if (savedMinor < 0L || targetMinor <= 0L || depositsLast30DaysMinor < 0L) return null
    if (savedMinor >= targetMinor) return 0L
    if (depositsLast30DaysMinor == 0L) return null
    val perDay = depositsLast30DaysMinor.toDouble() / 30.0
    return ceil((targetMinor - savedMinor).toDouble() / perDay).toLong()
}
