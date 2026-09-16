package com.questline.app.domain.habits

import com.questline.app.data.habits.Habit
import com.questline.app.data.habits.HabitCheck

/**
 * Чистая математика привычек (T-03): расписания, стрики, консистентность,
 * диагностика, XP. Без Android-зависимостей: всё состояние приходит параметрами.
 *
 * Даты — epochDay (LocalDate.toEpochDay()). День недели из epochDay:
 * (epochDay + 3) % 7 → 0 = ПН ... 6 = ВС (1970-01-01 был четвергом),
 * биты маски: бит 0 = ПН ... бит 6 = ВС.
 */
object HabitEngine {

    val CHARACTERISTICS = listOf("PHYSICS", "MIND", "MONEY", "SOCIAL", "DISCIPLINE")

    const val SCHEDULE_DAILY = "DAILY"
    const val SCHEDULE_WEEKDAYS = "WEEKDAYS"
    const val SCHEDULE_TIMES_PER_WEEK = "TIMES_PER_WEEK"
    const val SCHEDULE_INTERVAL = "INTERVAL"

    /** XP за отметку привычки (SPEC v3): S → 5, M → 8, L → 10 */
    const val XP_S = 5
    const val XP_M = 8
    const val XP_L = 10

    /** Дневной кап XP со всех привычек суммарно — уровни не инфлируются */
    const val DAILY_HABIT_XP_CAP = 20

    /** Милость стрика: 1 бесплатный пропуск за окно из 7 запланированных дней */
    const val FREE_GAP_WINDOW = 7

    /** Вехи стрика (T-07): разовый бонус при пересечении ровно этой отметки */
    val MILESTONE_DAYS = listOf(7, 30, 100)

    /** Бонус монет за веху стрика: 7 → +10, 30 → +50, 100 → +150 */
    fun milestoneCoinBonus(milestone: Int): Int = when (milestone) {
        100 -> 150
        30 -> 50
        else -> 10
    }

    /** Бонус XP за веху стрика: 7 → +5, 30 → +10, 100 → +20 */
    fun milestoneXpBonus(milestone: Int): Int = when (milestone) {
        100 -> 20
        30 -> 10
        else -> 5
    }

    /**
     * Последний запланированный прошедший день без чека/заморозки — кандидат
     * на платную заморозку (T-07). Обход от yesterday вниз до createdAt по
     * запланированным дням: день с любым чеком (в т.ч. заморозкой) — стоп,
     * пустой запланированный день — кандидат. TIMES_PER_WEEK запланированных
     * дней не имеет → null (заморозка там не влияет на стрик).
     */
    fun lastMissedDueDay(habit: Habit, checks: List<HabitCheck>, today: Long): Long? {
        if (habit.scheduleType == SCHEDULE_TIMES_PER_WEEK) return null
        val checked = checks.associateBy { it.epochDay }
        var day = today - 1
        while (day >= habit.createdAt) {
            if (isDue(habit, day)) return if (checked.containsKey(day)) null else day
            day--
        }
        return null
    }

    /** Бит дня недели (0 = ПН ... 6 = ВС) для дня epochDay */
    fun weekdayBit(epochDay: Long): Int = 1 shl (((epochDay + 3) % 7 + 7) % 7).toInt()

    /**
     * Положена ли привычка в конкретный день.
     * DAILY — всегда; WEEKDAYS — по битам маски; INTERVAL — каждые N дней от
     * якоря = createdAt (день создания сам является первым запланированным).
     * TIMES_PER_WEEK — гибкая недельная цель: «должен ли» в конкретный день
     * заранее не определяется, отмечать можно в любой день; недельное
     * обязательство выражает [weekTarget] (знаменатель консистентности).
     */
    fun isDue(habit: Habit, epochDay: Long): Boolean = when (habit.scheduleType) {
        SCHEDULE_WEEKDAYS -> (weekdayBit(epochDay) and habit.weekdaysMask) != 0
        SCHEDULE_INTERVAL -> habit.intervalDays > 0 && (epochDay - habit.createdAt) % habit.intervalDays == 0L
        else -> true
    }

    /** Недельная цель гибкой привычки: сколько отметок должно набраться за 7 дней */
    fun weekTarget(habit: Habit): Int = habit.timesPerWeek

    /** Отметка успешна: не заморозка и (для количественных) значение достигло цели */
    private fun isSuccess(habit: Habit, check: HabitCheck): Boolean =
        !check.frozen && (habit.targetValue == null || (check.value ?: 0.0) >= habit.targetValue)

    /** «Выполнено» для консистентности/диагностики: успех или заморозка (день удержан) */
    private fun isKept(habit: Habit, check: HabitCheck): Boolean =
        check.frozen || isSuccess(habit, check)

    /**
     * Стрик (текущий, лучший) по запланированным дням от createdAt до today.
     *
     * Обход запланированных дней: успешный чек удлиняет серию; frozen=1 мостит
     * (не рвёт и не удлиняет); день без чека (или количественный чек ниже цели)
     * бесплатно мостится только если среди предыдущих [FREE_GAP_WINDOW]
     * запланированных дней не было другого пропуска — иначе серия рвётся.
     * Неотмеченный сегодня не рвёт стрик (день ещё не кончился).
     *
     * Для TIMES_PER_WEEK запланированных дней нет — серию образуют сами отметки:
     * день с успешным чеком удлиняет, остальные дни мостят без штрафа.
     */
    fun streak(habit: Habit, checks: List<HabitCheck>, today: Long): Pair<Int, Int> {
        val checksByDay = HashMap<Long, HabitCheck>()
        for (check in checks) checksByDay[check.epochDay] = check // пара habitId+день уникальна

        var run = 0
        var best = 0
        var day = habit.createdAt
        val last = maxOf(today, habit.createdAt)
        while (day <= last) {
            val planned = isDue(habit, day)
            val dayCheck = checksByDay[day]
            run = when {
                // Гибкая цель: серию образуют сами отметки, остальные дни — нейтральный мост
                habit.scheduleType == SCHEDULE_TIMES_PER_WEEK ->
                    if (dayCheck != null && isSuccess(habit, dayCheck)) run + 1 else run

                !planned -> run                                  // незапланированный день — мимо счёта
                dayCheck != null && dayCheck.frozen -> run       // заморозка мостит, но не удлиняет
                dayCheck != null && isSuccess(habit, dayCheck) -> run + 1
                dayCheck == null && day == today -> run          // сегодня не отмечен — день не кончился
                recentMissWithin(day, habit, checksByDay) -> 0   // второй пропуск в окне — рвёт
                else -> run                                      // первый пропуск в окне — бесплатный мост
            }
            if (best < run) best = run
            day++
        }
        return run to best
    }

    /** Был ли пропуск среди предыдущих [FREE_GAP_WINDOW] запланированных дней перед днём */
    private fun recentMissWithin(
        day: Long,
        habit: Habit,
        checksByDay: Map<Long, HabitCheck>,
    ): Boolean {
        var scanned = 0
        var cursor = day - 1
        while (cursor >= habit.createdAt && scanned < FREE_GAP_WINDOW) {
            if (isDue(habit, cursor)) {
                scanned++
                val check = checksByDay[cursor]
                // Пропуск: нет чека или количественный чек ниже цели (заморозка — не пропуск)
                if (check == null || (!check.frozen && !isSuccess(habit, check))) return true
            }
            cursor--
        }
        return false
    }

    /**
     * Недельная консистентность: доля выполненных от запланированных за 7 дней
     * от weekStartEpochDay (0..1, сверху капается 1.0). Заморозка считается
     * выполненной. TIMES_PER_WEEK: знаменатель = weekTarget, капнутый числом
     * дней недели. Привычка не обязывается до дня создания (якорь createdAt).
     * Нет запланированных дней → 0.0 (вызывающий код решает, учитывать ли).
     */
    fun weeklyConsistency(habit: Habit, checks: List<HabitCheck>, weekStartEpochDay: Long): Double {
        if (habit.scheduleType == SCHEDULE_TIMES_PER_WEEK) {
            val denominator = weekTarget(habit).coerceAtMost(7)
            if (denominator <= 0) return 0.0
            val kept = checks.count { it.epochDay in weekStartEpochDay..weekStartEpochDay + 6 && isKept(habit, it) }
            return (kept.toDouble() / denominator).coerceAtMost(1.0)
        }
        return plannedFraction(habit, checks, weekStartEpochDay) ?: 0.0
    }

    /**
     * Консистентность 0..100 по всем 5 характеристикам: средняя недельная доля
     * привычек характеристики; привычки без запланированных дней в окне не
     * учитываются. Характеристика без вкладов → 0.0.
     */
    fun consistencyByCharacteristic(
        habits: List<Habit>,
        checks: List<HabitCheck>,
        weekStartEpochDay: Long,
    ): Map<String, Double> {
        val checksByHabit = checks.groupBy { it.habitId }
        val result = LinkedHashMap<String, Double>()
        for (key in CHARACTERISTICS) {
            val fractions = habits.asSequence()
                .filter { it.characteristic == key }
                .mapNotNull { habit -> plannedFraction(habit, checksByHabit[habit.id].orEmpty(), weekStartEpochDay) }
                .toList()
            result[key] = if (fractions.isEmpty()) 0.0 else fractions.average().coerceIn(0.0, 1.0) * 100
        }
        return result
    }

    /** Доля успеха по запланированным дням недели; null = в окне нечего планировать */
    private fun plannedFraction(habit: Habit, checks: List<HabitCheck>, weekStartEpochDay: Long): Double? {
        val weekEnd = weekStartEpochDay + 6
        val byDay = checks.associateBy { it.epochDay }
        var planned = 0
        var kept = 0
        var day = maxOf(weekStartEpochDay, habit.createdAt)
        while (day <= weekEnd) {
            if (isDue(habit, day)) {
                planned++
                if (byDay[day]?.let { isKept(habit, it) } == true) kept++
            }
            day++
        }
        return if (planned == 0) null else (kept.toDouble() / planned).coerceAtMost(1.0)
    }

    /** Диагностика адаптивной сложности за 14 запланированных дней (SPEC v3) */
    enum class Diagnosis { SHRINK, ANCHOR, GROW, NONE }

    /**
     * Доля успеха за последние 14 запланированных дней: <40% → SHRINK («упрости»),
     * 40–70% → ANCHOR («закрепи»); GROW — ≥90% за 14 дней и ≥90% за 21 день
     * (три недели подряд); иначе NONE. Заморозка считается успехом.
     */
    fun diagnose(habit: Habit, checks: List<HabitCheck>, today: Long): Diagnosis {
        val f14 = plannedFractionOver(habit, checks, today, 14)
        val f21 = plannedFractionOver(habit, checks, today, 21)
        return when {
            f14 < 0.40 -> Diagnosis.SHRINK
            f14 <= 0.70 -> Diagnosis.ANCHOR
            f14 >= 0.90 && f21 >= 0.90 -> Diagnosis.GROW
            else -> Diagnosis.NONE
        }
    }

    /** Доля успеха за последние [days] запланированных дней (TIMES_PER_WEEK: цель × недели) */
    private fun plannedFractionOver(habit: Habit, checks: List<HabitCheck>, today: Long, days: Int): Double {
        val byDay = checks.associateBy { it.epochDay }
        if (habit.scheduleType == SCHEDULE_TIMES_PER_WEEK) {
            val target = weekTarget(habit) * (days / 7)
            if (target <= 0) return 0.0
            var kept = 0
            var day = today - days + 1
            while (day <= today) {
                if (byDay[day]?.let { isKept(habit, it) } == true) kept++
                day++
            }
            return (kept.toDouble() / target).coerceAtMost(1.0)
        }
        var planned = 0
        var kept = 0
        var day = today
        while (day >= habit.createdAt && planned < days) {
            if (isDue(habit, day)) {
                planned++
                if (byDay[day]?.let { isKept(habit, it) } == true) kept++
            }
            day--
        }
        if (planned == 0) return 0.0
        return kept.toDouble() / planned
    }

    /** XP за отметку по сложности привычки */
    fun xpForHabit(complexity: String): Int = when (complexity) {
        "S" -> XP_S
        "L" -> XP_L
        else -> XP_M
    }

    /**
     * Сколько XP начислить сейчас: дневной кап [DAILY_HABIT_XP_CAP] на все
     * привычки суммарно. alreadyToday — уже начислено за сегодня по всем
     * привычкам; возвращается остаток до капа (не больше planned).
     */
    fun habitXpTodayCapped(alreadyToday: Int, planned: Int): Int =
        planned.coerceAtMost((DAILY_HABIT_XP_CAP - alreadyToday).coerceAtLeast(0))
}
