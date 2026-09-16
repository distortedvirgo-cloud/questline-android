package com.questline.app.domain.advice

import com.questline.app.data.habits.Habit
import com.questline.app.data.habits.HabitCheck
import com.questline.app.domain.finance.BurnRate
import com.questline.app.domain.habits.HabitEngine
import java.time.LocalDate

/** Тип совета — как карточка реагирует на кнопку «Взять». */
enum class TipKind { START_HABIT, SHRINK_HABIT, ANCHOR_HABIT, GROW_HABIT, FINANCE, TASK_INFO }

/** Один совет: действие ≤10 минут или конкретная привычка. */
data class Tip(
    val id: String,
    val title: String,
    val body: String,
    val kind: TipKind,
    /**
     * Содержимое действия: START_HABIT — пакет [AdviceCatalog.serialize],
     * SHRINK/GROW — id привычки, FINANCE — имя категории/копилки,
     * TASK_INFO — текст задачи.
     */
    val payload: String? = null,
)

/** Категория с планом, по которой давление: бюджет FAST или OVER. */
data class BudgetPressure(
    val categoryName: String,
    val status: BurnRate.Status,
    /** На сколько п.п. темп опережает план (0 при OVER — план уже пробит). */
    val overspendPercent: Int = 0,
)

/** Копилка, отстающая от темпа (пока без пополнений при ненулевой цели). */
data class LaggingGoal(val name: String)

/** Вход движка: всё состояние приходит параметрами — чистый Kotlin, без Android. */
data class NudgeInput(
    val habits: List<Habit> = emptyList(),
    val checks: List<HabitCheck> = emptyList(),
    /** 0..100 по ключам HabitEngine.CHARACTERISTICS (consistencyByCharacteristic). */
    val consistency: Map<String, Double> = emptyMap(),
    val budgets: List<BudgetPressure> = emptyList(),
    val laggingGoals: List<LaggingGoal> = emptyList(),
    val today: Long = 0,
)

/**
 * T-12 NudgeEngine: оффлайн rule-движок советов, детерминированный по дате
 * (seed = epochDay). Один вызов [dailyTip] на день даёт один и тот же совет.
 *
 * Приоритет правил: (1) буксующая привычка SHRINK → «упрости»; (2) финансовое
 * давление FAST/OVER или копилка без пополнений → финансовый совет;
 * (3) слабейшая сфера (консистентность активных сфер < 40% или по ней 0 данных)
 * → стартовая привычка из каталога; (4) GROW ≥90% за 21 день → «усложни»;
 * (5) ANCHOR 40–70% → «закрепи»; (6) последние 2 дня месяца → план на
 * следующий; иначе — ротация нейтральных «малых шагов» по дате.
 */
object NudgeEngine {

    /** Слабая сфера — консистентность ниже порога диагностики SHRINK (40%). */
    private const val WEAK_PERCENT = 40.0

    /** Хвост месяца: последние 2 дня — время плана на следующий месяц. */
    private const val MONTH_END_DAYS = 2

    private val CHAR_TITLES = mapOf(
        "PHYSICS" to "физика",
        "MIND" to "разум",
        "MONEY" to "деньги",
        "SOCIAL" to "харизма",
        "DISCIPLINE" to "дисциплина",
    )

    /** Последние [MONTH_END_DAYS] дня месяца (28–31 число включительно). */
    fun isMonthEnd(today: Long): Boolean {
        val date = LocalDate.ofEpochDay(today)
        return date.dayOfMonth > date.lengthOfMonth() - MONTH_END_DAYS
    }

    /** Совет дня: ровно один, детерминированный по дате и данным. */
    fun dailyTip(input: NudgeInput): Tip = candidates(input).first()

    /** Советы недели для «Зеркала» (T-14): 2–3 совета, тоже детерминированные. */
    fun weeklyTips(input: NudgeInput): List<Tip> {
        val tips = candidates(input).toMutableList()
        var offset = 1L
        while (tips.size < 3) {
            tips += rotationTip(input, offset)
            offset++
        }
        return tips.take(3)
    }

    /** Упрощение привычки: ступень сложности вниз, количественную цель — вдвое меньше. */
    fun shrunk(habit: Habit): Habit = habit.copy(
        complexity = when (habit.complexity) { "L" -> "M"; else -> "S" },
        targetValue = habit.targetValue?.let { (it / 2).coerceAtLeast(1.0) },
    )

    /** Усложнение: ступень сложности вверх, количественную цель — в 1.5 раза больше. */
    fun grown(habit: Habit): Habit = habit.copy(
        complexity = when (habit.complexity) { "S" -> "M"; else -> "L" },
        targetValue = habit.targetValue?.let { it * 1.5 },
    )

    // ---------------- Правила, по приоритету ----------------

    private fun candidates(input: NudgeInput): List<Tip> {
        val window = input.checks.filter { it.epochDay >= input.today - 21 }
        val byHabit = window.groupBy { it.habitId }
        val diagnosed = input.habits.map { it to HabitEngine.diagnose(it, byHabit[it.id].orEmpty(), input.today) }
        val out = ArrayList<Tip>(3)

        // (1) Буксующая привычка → «упрости»
        diagnosed.filter { it.second == HabitEngine.Diagnosis.SHRINK }
            .map { it.first }
            .ifNotEmpty { out += shrinkTip(it.pick(input.today), input.today) }

        // (2) Финансовое давление: бюджет FAST/OVER, затем копилка без пополнений
        val flagged = input.budgets
        if (flagged.any { it.status == BurnRate.Status.OVER }) {
            out += financeOverTip(flagged.filter { it.status == BurnRate.Status.OVER }.pick(input.today), input.today)
        } else if (flagged.any { it.status == BurnRate.Status.FAST }) {
            out += financeFastTip(flagged.filter { it.status == BurnRate.Status.FAST }.pick(input.today), input.today)
        } else if (input.laggingGoals.isNotEmpty()) {
            out += goalTip(input.laggingGoals.pick(input.today), input.today)
        }

        // (3) Слабейшая сфера → стартовая привычка из каталога
        if (input.habits.isNotEmpty()) {
            val activeChars = input.habits.map { it.characteristic }.toSet()
            val weakest = activeChars.minOf { input.consistency[it] ?: 0.0 }
            if (weakest < WEAK_PERCENT) {
                out += startHabitTip(input, weakestSphere(input), input.today)
            }
        }

        // (4) GROW: привычка ≥90% за 14 и 21 день → «усложни»
        diagnosed.filter { it.second == HabitEngine.Diagnosis.GROW }
            .map { it.first }
            .ifNotEmpty { out += growTip(it.pick(input.today), input.today) }

        // (5) ANCHOR: 40–70% → «закрепи время/место»
        diagnosed.filter { it.second == HabitEngine.Diagnosis.ANCHOR }
            .map { it.first }
            .ifNotEmpty { out += anchorTip(it.pick(input.today), input.today) }

        // (6) Конец месяца → план на следующий
        if (isMonthEnd(input.today)) out += monthEndTip(input.today)

        // (7) Ничего не применимо → ротация нейтральных «малых шагов»
        if (out.isEmpty()) out += rotationTip(input, 0)
        return out
    }

    private fun shrinkTip(habit: Habit, today: Long): Tip {
        val body = if (habit.targetValue != null) {
            val full = fmt(habit.targetValue!!)
            "«${habit.title}» получается меньше чем в 40% дней. Сделай вдвое меньше — ${fmt(habit.targetValue!! / 2)} ${habit.unit.orEmpty()} вместо $full — и закрепи привычку: половина плана — уже успех."
        } else {
            "«${habit.title}» получается меньше чем в 40% дней. Сделай шаг меньше: половина нормы или лёгкий вариант. Сейчас важен не результат, а вернуть ритм."
        }
        return Tip("SHRINK_HABIT-${habit.id}-$today", "Упрости «${habit.title}»", body, TipKind.SHRINK_HABIT, habit.id.toString())
    }

    private fun financeOverTip(pressure: BudgetPressure, today: Long): Tip = Tip(
        "FINANCE-OVER-${pressure.categoryName}-$today",
        "План «${pressure.categoryName}» пробит",
        "Категория уже ушла за месячный лимит. Мера до конца месяца: без покупок из этой категории, а траты записывай сразу — видно, что разгружать.",
        TipKind.FINANCE,
        pressure.categoryName,
    )

    private fun financeFastTip(pressure: BudgetPressure, today: Long): Tip = Tip(
        "FINANCE-FAST-${pressure.categoryName}-$today",
        "Тратишь по «${pressure.categoryName}» быстрее плана",
        "Темп выше плана на ${pressure.overspendPercent} п.п. Мера на неделю: дневной лимит по этой категории — сверяйся с ним перед каждой тратой.",
        TipKind.FINANCE,
        pressure.categoryName,
    )

    private fun goalTip(goal: LaggingGoal, today: Long): Tip = Tip(
        "FINANCE-GOAL-${goal.name}-$today",
        "Копилка «${goal.name}» стоит",
        "Пополнений пока не было. Закинь любую сумму сегодня — важен сам старт, размер не важен.",
        TipKind.FINANCE,
        goal.name,
    )

    private fun startHabitTip(input: NudgeInput, char: String, today: Long): Tip {
        val starter = AdviceCatalog.forCharacteristic(char)
            .filter { it.title !in input.habits.map(Habit::title) }
            .ifEmpty { AdviceCatalog.forCharacteristic(char) }
            .pick(today)
        val sphere = CHAR_TITLES.getValue(char)
        val hasData = input.habits.any { it.characteristic == char }
        val stateText = if (hasData) "проседает" else "по ней пока нет привычек"
        val body = "Сфера «$sphere» $stateText. Начни с малого: ${starter.title}. Одна отметка в день — и сфера оживает."
        return Tip(
            id = "START_HABIT-$char-$today",
            title = "Попробуй: ${starter.emoji} ${starter.title}",
            body = body,
            kind = TipKind.START_HABIT,
            payload = AdviceCatalog.serialize(starter),
        )
    }

    private fun growTip(habit: Habit, today: Long): Tip = Tip(
        "GROW_HABIT-${habit.id}-$today",
        "Усложни «${habit.title}»",
        "Три недели подряд — 90% и выше: привычка крепкая. Подними планку на ступень, чтобы оставалось чуть-чуть интересного вызова.",
        TipKind.GROW_HABIT,
        habit.id.toString(),
    )

    private fun anchorTip(habit: Habit, today: Long): Tip = Tip(
        "ANCHOR_HABIT-${habit.id}-$today",
        "Закрепи «${habit.title}»",
        "Получается в 40–70% дней — уже не случайность, но ещё не ритм. Привяжи к якорю: «сразу после завтрака / прихода домой» и в одно и то же время.",
        TipKind.ANCHOR_HABIT,
        habit.id.toString(),
    )

    private fun monthEndTip(today: Long): Tip = Tip(
        "TASK_INFO-MONTH_END-$today",
        "План на новый месяц",
        "Осталось пара дней. Выдели 10 минут: глянь, куда ушли деньги в этом месяце, и задай планы категориям на следующий — месяц начнётся с готовым планом.",
        TipKind.TASK_INFO,
        "Составить план на следующий месяц",
    )

    /** Ротация «малых шагов»: сфера и привычка выбираются по дате (+offset для списка недели). */
    private fun rotationTip(input: NudgeInput, offset: Long): Tip {
        val seed = input.today + offset * 11
        val char = HabitEngine.CHARACTERISTICS[(((seed % 5) + 5) % 5).toInt()]
        val active = input.habits.map(Habit::title).toSet()
        val starters = AdviceCatalog.forCharacteristic(char).filter { it.title !in active }
            .ifEmpty { AdviceCatalog.forCharacteristic(char) }
        val starter = starters[(((seed * 3) % starters.size + starters.size) % starters.size).toInt()]
        return Tip(
            id = "TASK_INFO-ROT-$char-${starter.title.hashCode()}-${input.today}",
            title = "Малый шаг",
            body = "${starter.emoji} ${starter.title}. Одна маленькая победа сегодня — без пафоса.",
            kind = TipKind.TASK_INFO,
            payload = starter.title,
        )
    }

    /** Самая слабая сфера из всех пяти: пустые считаются слабыми, ничья — по дате. */
    private fun weakestSphere(input: NudgeInput): String {
        val scored = HabitEngine.CHARACTERISTICS.map { it to (input.consistency[it] ?: 0.0) }
        val min = scored.minOf { it.second }
        return scored.filter { it.second == min }.map { it.first }.pick(input.today)
    }

    private fun <T> List<T>.pick(seed: Long): T = this[(((seed % size) + size) % size).toInt()]

    private fun <T> List<T>.ifNotEmpty(action: (List<T>) -> Unit) {
        if (isNotEmpty()) action(this)
    }

    private fun fmt(x: Double): String = if (x % 1.0 == 0.0) x.toLong().toString() else "%.1f".format(x).trimEnd('0').trimEnd('.')
}
