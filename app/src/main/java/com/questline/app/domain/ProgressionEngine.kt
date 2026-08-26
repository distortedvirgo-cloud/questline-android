package com.questline.app.domain

import com.questline.app.data.Quest

/**
 * Чистая математика прогрессии. Ничего не хранит:
 * уровень/характеристики ВСЕГДА вычисляются из истории закрытых квестов
 * (решение зафиксировано в NOTES.md — нет рассинхронизации, нет воркеров).
 */
object ProgressionEngine {

    // Сложность → награды
    const val XP_S = 20
    const val XP_M = 50
    const val XP_L = 100
    const val COINS_S = 5
    const val COINS_M = 12
    const val COINS_L = 25

    fun xpFor(complexity: String): Int = when (complexity) {
        "S" -> XP_S
        "L" -> XP_L
        else -> XP_M
    }

    fun coinsFor(complexity: String): Int = when (complexity) {
        "S" -> COINS_S
        "L" -> COINS_L
        else -> COINS_M
    }

    /** Кривая уровней: N → N+1 стоит 120 + 30*N XP */
    fun xpToNext(level: Int): Int = 120 + 30 * level

    data class LevelState(
        val level: Int,
        val xpIntoLevel: Int,
        val xpNeeded: Int,
        val totalXp: Int,
    ) {
        val fraction: Float get() = if (xpNeeded <= 0) 0f else xpIntoLevel.toFloat() / xpNeeded
    }

    /** Уровень из суммарного XP всех закрытых квестов */
    fun levelFromTotal(totalXp: Int): LevelState {
        var remaining = totalXp
        var level = 1
        while (remaining >= xpToNext(level)) {
            remaining -= xpToNext(level)
            level++
            if (level > 999) break
        }
        return LevelState(level, remaining, xpToNext(level), totalXp)
    }

    fun totalXp(doneQuests: List<Quest>): Int =
        doneQuests.sumOf { it.xpReward }

    /** XP по характеристикам (ключам PHYSICS/MIND/MONEY/SOCIAL/DISCIPLINE) */
    fun keyXp(doneQuests: List<Quest>): Map<String, Int> =
        doneQuests.groupBy { it.questKey }.mapValues { (_, list) -> list.sumOf { it.xpReward } }

    /** Текущий стрик: сколько последних дней подряд (вплоть до сегодня или вчера)
     *  был закрыт хотя бы один квест. Сегодня без квестов ещё не рвёт стрик. */
    fun currentStreak(closedEpochDays: Set<Long>, todayEpochDay: Long): Int {
        if (closedEpochDays.isEmpty()) return 0
        var day = todayEpochDay
        if (!closedEpochDays.contains(day)) {
            day -= 1
            if (!closedEpochDays.contains(day)) return 0
        }
        var streak = 0
        while (closedEpochDays.contains(day)) {
            streak++
            day--
        }
        return streak
    }

    /** Цвет загрузки бюджета по STYLE.md: успех → предупреждение → перерасход */
    fun budgetColor(fractionSpent: Float): BudgetStatus = when {
        fractionSpent >= 1f -> BudgetStatus.OVER
        fractionSpent >= 0.8f -> BudgetStatus.WARN
        else -> BudgetStatus.OK
    }

    enum class BudgetStatus { OK, WARN, OVER }
}
