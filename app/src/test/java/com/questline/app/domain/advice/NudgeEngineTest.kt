package com.questline.app.domain.advice

import com.questline.app.data.habits.Habit
import com.questline.app.data.habits.HabitCheck
import com.questline.app.domain.finance.BurnRate
import com.questline.app.domain.habits.HabitEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * T-12 NudgeEngine: детерминированность по дате и покрытие правил приоритета
 * (SHRINK → финансы → слабая сфера → GROW → ANCHOR → конец месяца → ротация).
 */
class NudgeEngineTest {

    private val today = LocalDate.of(2026, 9, 17).toEpochDay() // середина месяца
    private val weekStart = LocalDate.of(2026, 9, 14).toEpochDay() // понедельник

    private fun habit(id: Long, char: String, createdAt: Long = today - 30, target: Double? = null) = Habit(
        id = id, title = "Привычка $id", emoji = "💪", characteristic = char,
        scheduleType = HabitEngine.SCHEDULE_DAILY, createdAt = createdAt, targetValue = target,
    )

    private fun checks(vararg days: Long, habitId: Long = 1) =
        days.map { HabitCheck(habitId = habitId, epochDay = it, createdAtMillis = 0) }

    private fun input(
        habits: List<Habit> = emptyList(),
        checks: List<HabitCheck> = emptyList(),
        consistency: Map<String, Double> = emptyMap(),
        budgets: List<BudgetPressure> = emptyList(),
        goals: List<LaggingGoal> = emptyList(),
        today: Long = this.today,
    ) = NudgeInput(habits, checks, consistency, budgets, goals, today)

    // ---------------- Детерминированность ----------------

    @Test
    fun dailyTip_sameDay_isDeterministic() {
        val tip1 = NudgeEngine.dailyTip(input())
        val tip2 = NudgeEngine.dailyTip(input())
        assertEquals(tip1, tip2)
    }

    @Test
    fun dailyTip_withRuleInput_isDeterministic() {
        val h = habit(1, "PHYSICS")
        val tip1 = NudgeEngine.dailyTip(input(habits = listOf(h), checks = checks(today - 1, today - 3)))
        val tip2 = NudgeEngine.dailyTip(input(habits = listOf(h), checks = checks(today - 1, today - 3)))
        assertEquals(tip1, tip2)
    }

    @Test
    fun rotation_variesAcrossDays() {
        val ids = (0..5L).map { NudgeEngine.dailyTip(input(today = today + it)).id }
        assertTrue("разные дни должны давать разные советы", ids.distinct().size >= 2)
    }

    // ---------------- Правило 1: SHRINK ----------------

    @Test
    fun shrink_whenBelow40PercentOver14Days() {
        val h = habit(1, "PHYSICS")
        val tip = NudgeEngine.dailyTip(input(habits = listOf(h), checks = checks(today - 1, today - 3, today - 5, today - 7, today - 9)))
        assertEquals(TipKind.SHRINK_HABIT, tip.kind)
        assertEquals("1", tip.payload)
        assertTrue(tip.body.contains("Привычка 1"))
    }

    @Test
    fun shrink_beatsFinance_priorityOneOverTwo() {
        val h = habit(1, "PHYSICS")
        val tip = NudgeEngine.dailyTip(
            input(
                habits = listOf(h),
                checks = checks(today - 1, today - 3, today - 5, today - 7, today - 9),
                budgets = listOf(BudgetPressure("Продукты", BurnRate.Status.OVER)),
            ),
        )
        assertEquals(TipKind.SHRINK_HABIT, tip.kind)
    }

    // ---------------- Правило 2: финансы ----------------

    @Test
    fun financeTip_whenBudgetOver() {
        val tip = NudgeEngine.dailyTip(input(budgets = listOf(BudgetPressure("Продукты", BurnRate.Status.OVER))))
        assertEquals(TipKind.FINANCE, tip.kind)
        assertEquals("Продукты", tip.payload)
        assertTrue(tip.title.contains("Продукты"))
    }

    @Test
    fun financeTip_whenBudgetFast() {
        val tip = NudgeEngine.dailyTip(input(budgets = listOf(BudgetPressure("Кафе", BurnRate.Status.FAST, overspendPercent = 12))))
        assertEquals(TipKind.FINANCE, tip.kind)
        assertTrue(tip.body.contains("12"))
    }

    @Test
    fun financeTip_whenGoalHasNoDeposits() {
        val tip = NudgeEngine.dailyTip(input(goals = listOf(LaggingGoal("Ноутбук"))))
        assertEquals(TipKind.FINANCE, tip.kind)
        assertEquals("Ноутбук", tip.payload)
    }

    // ---------------- Правило 3: слабейшая сфера ----------------

    @Test
    fun startHabit_goesToWeakestCharacteristic() {
        // MIND: 7 из 14 дней (ANCHOR, не SHRINK), но в текущей неделе 1 отметка из 4 → слабая
        val mind = habit(1, "MIND")
        val mindChecks = checks(
            today - 2, today - 8, today - 9, today - 10, today - 11, today - 12, today - 13, habitId = 1,
        )
        // PHYSICS: полные 22 отметки → 100%
        val physics = habit(2, "PHYSICS", createdAt = today - 25)
        val physicsChecks = (today - 21..today).map { HabitCheck(habitId = 2, epochDay = it, createdAtMillis = 0) }
        val habits = listOf(mind, physics)
        val allChecks = mindChecks + physicsChecks
        val consistency = HabitEngine.consistencyByCharacteristic(habits, allChecks, weekStart)

        val tip = NudgeEngine.dailyTip(input(habits = habits, checks = allChecks, consistency = consistency))
        assertEquals(TipKind.START_HABIT, tip.kind)
        val starter = AdviceCatalog.parseStarterHabit(tip.payload)!!
        assertTrue("совет должен уйти в сферу без данных", starter.characteristic in setOf("MONEY", "SOCIAL", "DISCIPLINE"))
    }

    @Test
    fun ruleThree_skipped_whenNoActiveHabits() {
        // Без привычек диагностировать нечего: совет дня — ротация, не старт привычки
        val tip = NudgeEngine.dailyTip(input())
        assertEquals(TipKind.TASK_INFO, tip.kind)
        assertTrue(tip.id.contains("ROT"))
    }

    // ---------------- Правило 4: GROW ----------------

    @Test
    fun grow_whenNinetyPercentOver21Days() {
        // Обе активные сферы сильные (100%) → правило 3 пропущено, GROW срабатывает
        val physics = habit(1, "PHYSICS", createdAt = today - 25)
        val mind = habit(2, "MIND", createdAt = today - 25)
        val allChecks = (today - 21..today).flatMap { day ->
            listOf(
                HabitCheck(habitId = 1, epochDay = day, createdAtMillis = 0),
                HabitCheck(habitId = 2, epochDay = day, createdAtMillis = 0),
            )
        }
        val consistency = HabitEngine.consistencyByCharacteristic(listOf(physics, mind), allChecks, weekStart)
        val tip = NudgeEngine.dailyTip(input(habits = listOf(physics, mind), checks = allChecks, consistency = consistency))
        assertEquals(TipKind.GROW_HABIT, tip.kind)
        assertTrue(tip.payload in setOf("1", "2"))
    }

    // ---------------- Правило 5: ANCHOR ----------------

    @Test
    fun anchor_forModerateConsistency() {
        // 5 привычек, 7 из 14 дней каждая → ANCHOR; недельная консистентность 3/7 ≈ 43% ≥ 40%,
        // поэтому правило 3 (слабая сфера) пропущено и доходит до правила 5.
        val chars = HabitEngine.CHARACTERISTICS
        val habits = chars.mapIndexed { i, char -> habit((i + 1).toLong(), char) }
        val anchorDays = listOf(today - 13, today - 12, today - 11, today - 10, today - 3, today - 2, today - 1)
        val allChecks = habits.flatMapIndexed { i, h ->
            anchorDays.map { HabitCheck(habitId = h.id, epochDay = it, createdAtMillis = 0) }
        }
        val consistency = HabitEngine.consistencyByCharacteristic(habits, allChecks, weekStart)

        val tip = NudgeEngine.dailyTip(input(habits = habits, checks = allChecks, consistency = consistency))
        assertEquals(TipKind.ANCHOR_HABIT, tip.kind)
    }

    // ---------------- Правило 6: конец месяца ----------------

    @Test
    fun monthEnd_triggersNextMonthPlan() {
        val lastDay = LocalDate.of(2026, 9, 30).toEpochDay()
        val tip = NudgeEngine.dailyTip(input(today = lastDay))
        assertEquals(TipKind.TASK_INFO, tip.kind)
        assertTrue(tip.id.contains("MONTH_END"))
        assertEquals("Составить план на следующий месяц", tip.payload)
    }

    @Test
    fun monthEnd_notTriggeredMidMonth() {
        val tip = NudgeEngine.dailyTip(input())
        assertTrue(tip.id.contains("ROT"))
        assertNotEquals("Составить план на следующий месяц", tip.payload)
    }

    // ---------------- weeklyTips ----------------

    @Test
    fun weeklyTips_areTwoToThree_andDeterministic() {
        val tips = NudgeEngine.weeklyTips(input())
        assertTrue("2–3 совета, а не весь каталог", tips.size in 2..3)
        assertEquals("id уникальны", tips.size, tips.map { it.id }.distinct().size)
        assertEquals(tips, NudgeEngine.weeklyTips(input()))
    }

    // ---------------- Каталог ----------------

    @Test
    fun catalog_has30Habits_sixPerCharacteristic() {
        assertEquals(30, AdviceCatalog.ALL.size)
        assertEquals(30, AdviceCatalog.ALL.map { it.title }.distinct().size)
        for (char in HabitEngine.CHARACTERISTICS) {
            assertEquals("6 привычек на $char", 6, AdviceCatalog.forCharacteristic(char).size)
        }
        assertTrue(AdviceCatalog.ALL.all { it.characteristic in HabitEngine.CHARACTERISTICS })
    }

    @Test
    fun starterHabit_payloadRoundtrip() {
        val withTarget = StarterHabit("10 страниц книги", "📖", "MIND", "M", 10.5, "стр")
        assertEquals(withTarget, AdviceCatalog.parseStarterHabit(AdviceCatalog.serialize(withTarget)))
        val plain = StarterHabit("Записать траты сразу же", "💸", "MONEY", "S")
        val parsed = AdviceCatalog.parseStarterHabit(AdviceCatalog.serialize(plain))!!
        assertEquals(null, parsed.targetValue)
        assertEquals(null, parsed.unit)
    }

    // ---------------- Упрощение/усложнение ----------------

    @Test
    fun shrunk_stepsComplexityDown_andHalvesTarget() {
        val big = habit(1, "PHYSICS", target = 20.0).copy(complexity = "L")
        val shrunk = NudgeEngine.shrunk(big)
        assertEquals("M", shrunk.complexity)
        assertEquals(10.0, shrunk.targetValue!!, 0.001)

        val medium = habit(2, "MIND").copy(complexity = "M")
        assertEquals("S", NudgeEngine.shrunk(medium).complexity)
    }

    @Test
    fun grown_stepsComplexityUp_andScalesTargetByHalfAgain() {
        val small = habit(1, "MIND", target = 10.0).copy(complexity = "S")
        val grown = NudgeEngine.grown(small)
        assertEquals("M", grown.complexity)
        assertEquals(15.0, grown.targetValue!!, 0.001)

        assertEquals("L", NudgeEngine.grown(habit(2, "MONEY").copy(complexity = "M")).complexity)
    }
}
