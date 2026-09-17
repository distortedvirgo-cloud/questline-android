package com.questline.app.data

import android.content.Context
import com.questline.app.data.habits.Habit
import com.questline.app.data.habits.HabitCheck
import com.questline.app.data.xp.XpLedger
import com.questline.app.domain.ProgressionEngine
import com.questline.app.domain.habits.HabitEngine
import com.questline.app.notify.HabitReminderScheduler
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Итог отметки привычки: XP за чек и веха стрика, если она пересечена (T-07) */
data class HabitCheckResult(
    val awardedXp: Int,
    val milestone: Int? = null,
)

/**
 * Единый фасад над DAO. Категории сидируются при первом доступе.
 * Все деньги — minor units (копейки). Синглтон через [get]; для тестов
 * есть вторичный конструктор над готовой (in-memory) базой.
 */
class AppRepo internal constructor(private val db: QuestlineDatabase) {

    /** ApplicationContext для receiver-времени (N-01); в тестах (db-конструктор) — null */
    @Volatile private var appContext: Context? = null

    constructor(context: Context) : this(QuestlineDatabase.get(context)) {
        appContext = context.applicationContext
    }

    val categories = db.categoryDao()
    val tasks = db.taskDao()
    val quests = db.questDao()
    val txns = db.txnDao()
    val pending = db.pendingTxnDao()
    val goals = db.goalDao()
    val coins = db.coinsLedgerDao()
    val habits = db.habitDao()
    val habitChecks = db.habitCheckDao()
    val xpLedger = db.xpLedgerDao()

    // ---------------- Транзакции: правка и удаление (T-09) ----------------

    /** Правка существующей транзакции (сумма/тип/категория/дата/заметка) */
    suspend fun updateTxn(txn: Txn) = txns.update(txn)

    /** Удаление транзакции */
    suspend fun deleteTxn(txn: Txn) = txns.delete(txn.id)

    suspend fun seedIfEmpty() {
        val existing = categories.all()
        if (existing.isNotEmpty()) return
        // Характеристики (QUEST)
        categories.insertAll(questCategories())
        // Финансовые статьи (FINANCE), colorIndex 0..11 циклично
        categories.insertAll(financeCategories())
    }

    /** Добавить монеты одной записью в гроссбух */
    suspend fun addCoins(delta: Int, reason: String, refId: Long? = null) {
        coins.insert(
            CoinsEntry(delta = delta, reason = reason, refId = refId, createdAtMillis = System.currentTimeMillis()),
        )
    }

    /**
     * Единая точка начисления XP (SPEC v3 «XP-экономика»): любая награда —
     * запись в xp_ledger; уровень считается on-demand суммой журнала.
     */
    suspend fun awardXp(source: String, refId: Long?, delta: Int, epochDay: Long) {
        xpLedger.insert(
            XpLedger(
                delta = delta,
                source = source,
                refId = refId,
                epochDay = epochDay,
                createdAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    /** Закрыть квест с наградой XP+монеты (xp применяет вызывающий VM к своего progress'у) */
    suspend fun completeQuest(quest: Quest) {
        val closedAt = System.currentTimeMillis()
        quests.update(quest.copy(status = "DONE", closedAtMillis = closedAt))
        addCoins(quest.coinReward, "QUEST_DONE", quest.id)
        // Журнал XP: каждое НОВОЕ закрытие — запись QUEST (backfill миграции покрывает только старые)
        awardXp("QUEST", quest.id, quest.xpReward, todayEpochDay)
    }

    /**
     * Кор-луп «задача → квест»: закрытие задачи создаёт USER-квест,
     * который тут же закрывается и даёт XP/монеты.
     */
    suspend fun completeTaskAsQuest(task: Task) {
        val now = System.currentTimeMillis()
        val repeats = task.repeatIntervalDays > 0
        if (repeats && task.lastDoneEpochDay == todayEpochDay) return
        val updated = if (repeats) {
            task.copy(lastDoneEpochDay = todayEpochDay)
        } else {
            task.copy(done = true, doneAtMillis = now)
        }
        tasks.update(updated)
        val key = task.categoryId
            ?.let { categories.byId(it)?.questKey }
            ?: "DISCIPLINE"
        val questId = quests.insert(
            Quest(
                taskId = task.id,
                source = "USER",
                title = task.title,
                questKey = key,
                complexity = task.complexity,
                xpReward = ProgressionEngine.xpFor(task.complexity),
                coinReward = ProgressionEngine.coinsFor(task.complexity),
                status = "DONE",
                dateCreatedEpochDay = todayEpochDay,
                closedAtMillis = now,
            ),
        )
        addCoins(ProgressionEngine.coinsFor(task.complexity), "QUEST_DONE", questId)
        // Журнал XP: новое закрытие задачи = запись TASK со ссылкой на созданный квест
        awardXp("TASK", questId, ProgressionEngine.xpFor(task.complexity), todayEpochDay)
    }

    // ---------------- Привычки (T-04/T-05/T-07) ----------------

    /**
     * Отметка привычки за день (уникальная на пару habitId+день). Для
     * количественных value — фактическое значение дня; XP по сложности
     * начисляется один раз, когда отметка успешна (без цели — сразу,
     * с целью — при достижении targetValue), с дневным капом 20 XP на все
     * привычки суммарно. После успешного чека проверяется веха стрика
     * 7/30/100 — разовый бонус монет + XP (T-07). Итог — [HabitCheckResult]:
     * awardedXp — начисленный XP за отметку (0 — записана без награды),
     * milestone — пересечённая веха (null — вехи нет).
     */
    suspend fun checkHabit(habit: Habit, epochDay: Long, value: Double? = null): HabitCheckResult {
        val existing = habitChecks.byDay(habit.id, epochDay)
        val checkId = existing?.id ?: habitChecks.insert(
            HabitCheck(
                habitId = habit.id,
                epochDay = epochDay,
                value = value,
                createdAtMillis = System.currentTimeMillis(),
            ),
        )
        if (existing != null && value != null && value != existing.value) {
            habitChecks.updateValue(checkId, value)
        }
        val success = habit.targetValue == null || (value ?: existing?.value ?: 0.0) >= habit.targetValue
        val awarded = if (!success || xpLedger.countByRef("HABIT", checkId) > 0) {
            0
        } else {
            HabitEngine.habitXpTodayCapped(xpLedger.sumBySource(epochDay, "HABIT"), HabitEngine.xpForHabit(habit.complexity))
        }
        if (awarded > 0) awardXp("HABIT", checkId, awarded, epochDay)
        // Веха проверяется при каждом успешном чеке (в т.ч. когда количественная
        // привычка доросла до цели инкрементом): гвард по coins_ledger не даёт повтор.
        val milestone = if (success) awardMilestone(habit, checkId, epochDay) else null
        rescheduleReminder(habit)
        return HabitCheckResult(awardedXp = awarded, milestone = milestone)
    }

    /**
     * Вехи стрика (T-07): при пересечении ровно отметки 7/30/100 — разовый
     * бонус монет (+10/+50/+150, reason "MILESTONE", refId = habitId*1000+веха)
     * и XP (5/10/20, source "MILESTONE", refId = id чека). Защита от повтора —
     * CoinsLedgerDao.countByReasonRef: бонус только если записи ещё нет.
     */
    private suspend fun awardMilestone(habit: Habit, checkId: Long, epochDay: Long): Int? {
        val checks = habitChecks.rangeForHabit(habit.id, habit.createdAt, epochDay)
        val (current, _) = HabitEngine.streak(habit, checks, epochDay)
        if (current !in HabitEngine.MILESTONE_DAYS) return null
        val milestone = current
        val refId = habit.id * 1000 + milestone
        if (coins.countByReasonRef(REASON_MILESTONE, refId) > 0) return null
        addCoins(HabitEngine.milestoneCoinBonus(milestone), REASON_MILESTONE, refId)
        awardXp(REASON_MILESTONE, checkId, HabitEngine.milestoneXpBonus(milestone), epochDay)
        return milestone
    }

    /**
     * Платная заморозка пропущенного дня (T-07): цена [FREEZE_COST] монет,
     * списание одной записью CoinsLedger (reason "HABIT_FREEZE", refId = habit.id).
     * Условия: в этот день ещё нет чека/заморозки (иначе false) и день не в
     * будущем (вчера или сегодня). Не хватает монет или день невалиден — false.
     * Заморозка мостит день в стрике, не удлиняя его (HabitEngine).
     */
    suspend fun freezeHabitDay(habit: Habit, epochDay: Long): Boolean {
        if (epochDay > todayEpochDay) return false
        if (habitChecks.byDay(habit.id, epochDay) != null) return false
        val balance = coins.totalCoins()
        if (balance < FREEZE_COST) return false
        habitChecks.insert(
            HabitCheck(
                habitId = habit.id,
                epochDay = epochDay,
                value = null,
                createdAtMillis = System.currentTimeMillis(),
                frozen = true,
            ),
        )
        addCoins(-FREEZE_COST, REASON_FREEZE, habit.id)
        return true
    }

    /** Снять отметку за день: удаляет чек и связанную запись XP из журнала. */
    suspend fun uncheckHabit(habitId: Long, epochDay: Long) {
        val check = habitChecks.byDay(habitId, epochDay) ?: return
        habitChecks.deleteById(check.id)
        xpLedger.deleteByRef("HABIT", check.id)
    }

    /** Создание (id == 0) или правка привычки; напоминание перепланируется. */
    suspend fun upsertHabit(habit: Habit) {
        val id = if (habit.id == 0L) habits.insert(habit) else { habits.update(habit); habit.id }
        rescheduleReminder(habit.copy(id = id))
    }

    /** Включить/выключить (null) напоминание привычки — точечная правка колонки. */
    suspend fun setHabitReminder(habitId: Long, minOfDay: Int?) {
        val habit = habits.byId(habitId) ?: return
        val updated = habit.copy(reminderMinOfDay = minOfDay)
        habits.update(updated)
        rescheduleReminder(updated)
    }

    /** Архивация: уходит из активных, отметки и стрик сохраняются. */
    suspend fun archiveHabit(habit: Habit) {
        habits.archive(habit.id, AppRepo.todayEpochDay)
        rescheduleReminder(habit.copy(archivedAt = AppRepo.todayEpochDay))
    }

    /** Полное удаление вместе с отметками; журнал XP не трогаем — история уровня. */
    suspend fun deleteHabit(habitId: Long) {
        habitChecks.deleteForHabit(habitId)
        habits.delete(habitId)
        appContext?.let { HabitReminderScheduler.cancel(it, habitId) }
    }

    /** Перепланирование напоминания (N-01); в тестах без контекста — no-op. */
    private fun rescheduleReminder(habit: Habit) {
        val context = appContext ?: return
        if (habit.archivedAt == null && habit.reminderMinOfDay != null) HabitReminderScheduler.schedule(context, habit)
        else HabitReminderScheduler.cancel(context, habit.id)
    }

    companion object {
        @Volatile private var instance: AppRepo? = null

        const val REASON_MILESTONE = "MILESTONE"
        const val REASON_FREEZE = "HABIT_FREEZE"

        /** Цена платной заморозки дня, монет */
        const val FREEZE_COST = 20

        fun get(context: Context): AppRepo =
            instance ?: synchronized(this) {
                instance ?: AppRepo(context.applicationContext).also { instance = it }
            }

        val todayEpochDay: Long get() = LocalDate.now().toEpochDay()

        fun monthPeriodKey(epochDay: Long): String {
            val d = LocalDate.ofEpochDay(epochDay)
            return "%04d-%02d".format(d.year, d.monthValue)
        }
    }
}

private typealias CoinsEntry = CoinsLedger

/** Дефолтные 5 характеристик */
private fun questCategories() = listOf(
    Category(name = "Физика", kind = "QUEST", questKey = "PHYSICS", emoji = "💪", colorIndex = 0),
    Category(name = "Разум", kind = "QUEST", questKey = "MIND", emoji = "🧠", colorIndex = 1),
    Category(name = "Деньги", kind = "QUEST", questKey = "MONEY", emoji = "💰", colorIndex = 2),
    Category(name = "Харизма", kind = "QUEST", questKey = "SOCIAL", emoji = "💬", colorIndex = 3),
    Category(name = "Дисциплина", kind = "QUEST", questKey = "DISCIPLINE", emoji = "🎯", colorIndex = 4),
)

/** Дефолтные финансовые категории; индексы цветов повторяют STYLE.md статусы */
private fun financeCategories() = listOf(
    Category(name = "Продукты", kind = "FINANCE", emoji = "🍎", colorIndex = 0, budgetMonthlyMinor = 20_000_00),
    Category(name = "Кафе и рестораны", kind = "FINANCE", emoji = "☕️", colorIndex = 1, budgetMonthlyMinor = 5_000_00),
    Category(name = "Транспорт", kind = "FINANCE", emoji = "🚌", colorIndex = 2, budgetMonthlyMinor = 3_000_00),
    Category(name = "Жильё и ЖКХ", kind = "FINANCE", emoji = "🏠", colorIndex = 3, budgetMonthlyMinor = 25_000_00),
    Category(name = "Здоровье", kind = "FINANCE", emoji = "💊", colorIndex = 4, budgetMonthlyMinor = 5_000_00),
    Category(name = "Развлечения", kind = "FINANCE", emoji = "🎮", colorIndex = 5, budgetMonthlyMinor = 4_000_00),
    Category(name = "Одежда", kind = "FINANCE", emoji = "👕", colorIndex = 6),
    Category(name = "Подписки", kind = "FINANCE", emoji = "📱", colorIndex = 7),
    Category(name = "Подарки", kind = "FINANCE", emoji = "🎁", colorIndex = 8),
    Category(name = "Прочее", kind = "FINANCE", emoji = "📦", colorIndex = 9),
)
