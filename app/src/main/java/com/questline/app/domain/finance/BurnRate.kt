package com.questline.app.domain.finance

/**
 * T-08 «План месяца»: темп трат — сравнение доли потраченного бюджета
 * с долей прошедшего месяца. Прошла половина месяца, а потрачено 62% плана —
 * «тратишь на 12% быстрее плана».
 */
data class BurnRate(
    /** На сколько процентных пунктов тратишь быстрее плана (0, если темп в норме). */
    val overspendPercent: Int,
    val status: Status,
) {
    enum class Status {
        /** Темп в норме или тратишь медленнее плана. */
        CALM,

        /** Быстрее плана более чем на 10 п.п. */
        FAST,

        /** План пробит: весь месячный лимит потрачен до конца месяца. */
        OVER,
    }
}

/**
 * Чистая математика темпа трат. Суммы — minor units.
 * [dayOfMonth] вне [1..daysInMonth] или нулевой план — спокойный темп.
 */
fun burnRate(
    spentMinor: Long,
    planMinor: Long,
    dayOfMonth: Int,
    daysInMonth: Int,
): BurnRate {
    if (planMinor <= 0L || daysInMonth <= 0 || dayOfMonth <= 0) {
        return BurnRate(0, BurnRate.Status.CALM)
    }

    val day = dayOfMonth.coerceAtMost(daysInMonth)
    val percentSpent = (spentMinor * 100 / planMinor).toInt()
    val percentElapsed = day * 100 / daysInMonth
    val delta = percentSpent - percentElapsed

    // OVER — план реально пробит: потрачено больше плана, либо весь план
    // уже потрачен, а месяц ещё идёт. Ровно весь план в последний день —
    // план выполнен, не пробит.
    val status = when {
        percentSpent >= 100 && delta > 0 -> BurnRate.Status.OVER
        delta > 10 -> BurnRate.Status.FAST
        else -> BurnRate.Status.CALM
    }
    return BurnRate(overspendPercent = delta.coerceAtLeast(0), status = status)
}
