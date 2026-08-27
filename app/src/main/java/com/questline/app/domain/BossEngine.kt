package com.questline.app.domain

import com.questline.app.data.Quest
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * «Босс месяца» — чистая математика геймификации, без android.*.
 * Каждый закрытый (DONE) квест календарного месяца наносит боссу урон
 * max(10, xpReward / 2). HP босса — 100; суммарный урон >= 100 — победа.
 */
object BossEngine {

    const val BOSS_MAX_HP = 100
    const val WIN_BONUS_COINS = 50

    /** reason записи о бонусе за победу в CoinsLedger. */
    const val REASON_BOSS_WIN = "BOSS_WIN"

    /** 12 боссов, индекс = номер месяца - 1; имя стабильно весь месяц. */
    private val BOSSES = listOf(
        "Ледяной Прокрастинатор" to "❄️",  // январь
        "Пожиратель времени" to "🕳️",      // февраль
        "Хаос в делах" to "🌪️",            // март
        "Тень дедлайна" to "🌧️",           // апрель
        "Диванный тролль" to "🛋️",         // май
        "Выжигатель мотивации" to "☀️",    // июнь
        "Импульс трат" to "🍹",            // июль
        "Лень-дракон" to "🐲",             // август
        "Клуб забытых задач" to "📚",      // сентябрь
        "Слизень откладывания" to "🍂",    // октябрь
        "Туман целей" to "🌫️",             // ноябрь
        "Гринч бюджета" to "🎄",           // декабрь
    )

    data class BossState(
        val name: String,
        val emoji: String,
        val damage: Int,
        val hpLeft: Int,
        val defeated: Boolean,
    )

    /** Урон одного закрытого квеста: не меньше 10, иначе половина XP. */
    fun damageFor(quest: Quest): Int = maxOf(10, quest.xpReward / 2)

    /** Имя и эмодзи босса месяца — детерминированы номером месяца. */
    fun forMonth(month: YearMonth): Pair<String, String> =
        BOSSES[(month.monthValue - 1) % BOSSES.size]

    /** Человекочитаемый ключ месяца «2026-08» — для подписей и логов. */
    fun monthKey(month: YearMonth): String = "%04d-%02d".format(month.year, month.monthValue)

    /**
     * refId для CoinsLedger: схема хранит Long?, поэтому ключ месяца
     * «2026-08» кодируется числом год*100 + месяц → 202608. Взаимно однозначно.
     */
    fun ledgerRefId(month: YearMonth): Long = month.year * 100L + month.monthValue

    /**
     * Состояние босса на месяц: суммарный урон DONE-квестов, закрытых
     * в этом календарном месяце (год+месяц берутся из closedAtMillis).
     */
    fun compute(
        closedQuests: List<Quest>,
        month: YearMonth,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): BossState {
        val (name, emoji) = forMonth(month)
        val damage = closedQuests
            .filter { it.status == "DONE" && it.closedAtMillis != null }
            .filter {
                YearMonth.from(Instant.ofEpochMilli(it.closedAtMillis!!).atZone(zoneId)) == month
            }
            .sumOf { damageFor(it) }
        return BossState(
            name = name,
            emoji = emoji,
            damage = damage,
            hpLeft = (BOSS_MAX_HP - damage).coerceAtLeast(0),
            defeated = damage >= BOSS_MAX_HP,
        )
    }
}
