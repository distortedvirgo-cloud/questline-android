package com.questline.app.data

import android.content.Context
import com.questline.app.domain.ProgressionEngine
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Единый фасад над DAO. Категории сидируются при первом доступе.
 * Все деньги — minor units (копейки).
 */
class AppRepo private constructor(context: Context) {
    private val db = QuestlineDatabase.get(context)
    val categories = db.categoryDao()
    val tasks = db.taskDao()
    val quests = db.questDao()
    val txns = db.txnDao()
    val pending = db.pendingTxnDao()
    val goals = db.goalDao()
    val coins = db.coinsLedgerDao()

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

    /** Закрыть квест с наградой XP+монеты (xp применяет вызывающий VM к своего progress'у) */
    suspend fun completeQuest(quest: Quest) {
        quests.update(quest.copy(status = "DONE", closedAtMillis = System.currentTimeMillis()))
        addCoins(quest.coinReward, "QUEST_DONE", quest.id)
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
    }

    companion object {
        @Volatile private var instance: AppRepo? = null

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
