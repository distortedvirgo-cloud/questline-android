package com.questline.app.domain

import com.questline.app.data.AppRepo
import com.questline.app.data.Quest
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Синергия «бюджет порождает квест»: когда за месяц потрачено 60% бюджета
 * категории, создаётся BUDGET-вызов «не тратить до конца недели». Квест
 * закрывается сам по данным транзакций: сорвал — тихо EXPIRED (без наказаний),
 * продержался 7 дней в нуле — DONE с монетами.
 */
object BudgetQuestEngine {

    private const val TEMPLATE_ID = "BUDGET_WEEK"

    /** Порог запуска вызова: доля месячного бюджета */
    private const val TRIGGER_PERCENT = 60L

    /** Сколько дней нужно продержаться без трат, чтобы получить награду */
    private const val QUEST_DURATION_DAYS = 7L

    private const val COIN_REWARD = 15

    /**
     * Создать бюджетные вызовы на текущий месяц. Идемпотентно:
     * не более одного квеста на пару (период, категория).
     */
    suspend fun ensureBudgetQuests(repo: AppRepo, todayEpochDay: Long) {
        val periodKey = AppRepo.monthPeriodKey(todayEpochDay)
        val monthStart = monthStartEpochDay(todayEpochDay)
        val budgeted = repo.categories.all().filter {
            it.kind == "FINANCE" && it.budgetMonthlyMinor != null
        }
        for (cat in budgeted) {
            if (repo.quests.findBudgetQuest(TEMPLATE_ID, periodKey, cat.id) != null) continue
            val budget = cat.budgetMonthlyMinor ?: continue
            val spent = repo.txns.spentInCategory(cat.id, monthStart, todayEpochDay)
            // Целочисленное сравнение вместо дробей: spent >= 60% от budget
            if (spent * 100 < budget * TRIGGER_PERCENT) continue
            repo.quests.insert(
                Quest(
                    source = "BUDGET",
                    templateId = TEMPLATE_ID,
                    title = "Не тратить на ${cat.emoji} ${cat.name} до конца недели",
                    questKey = "MONEY",
                    complexity = "M",
                    xpReward = ProgressionEngine.xpFor("M"),
                    coinReward = COIN_REWARD,
                    status = "OPEN",
                    dateCreatedEpochDay = todayEpochDay,
                    budgetPeriodKey = periodKey,
                    budgetCategoryId = cat.id,
                ),
            )
        }
    }

    /**
     * Разобрать открытые BUDGET-квесты по фактам трат с дня их создания:
     * появилась любая трата в категории — тихо снять с доски (EXPIRED);
     * ноль трат 7 дней подряд — закрыть как выполненный и начислить монеты.
     */
    suspend fun resolveBudgetQuests(repo: AppRepo, todayEpochDay: Long) {
        val openBudget = repo.quests.observeOpen().first()
            .filter { it.source == "BUDGET" }
        for (quest in openBudget) {
            val categoryId = quest.budgetCategoryId ?: continue
            val spent = repo.txns.spentInCategory(categoryId, quest.dateCreatedEpochDay, todayEpochDay)
            when {
                spent > 0 ->
                    // Сорвал вызов — без наказаний, просто снимаем с доски
                    repo.quests.update(quest.copy(status = "EXPIRED"))

                todayEpochDay - quest.dateCreatedEpochDay >= QUEST_DURATION_DAYS -> {
                    repo.quests.update(
                        quest.copy(status = "DONE", closedAtMillis = System.currentTimeMillis()),
                    )
                    repo.addCoins(quest.coinReward, "BUDGET_OK", quest.id)
                }
                // Иначе квест остаётся открытым до следующей проверки
            }
        }
    }

    /** EpochDay первого числа месяца, которому принадлежит день */
    private fun monthStartEpochDay(todayEpochDay: Long): Long {
        val d = LocalDate.ofEpochDay(todayEpochDay)
        return LocalDate.of(d.year, d.monthValue, 1).toEpochDay()
    }
}
