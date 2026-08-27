package com.questline.app.ai

import com.questline.app.data.AppRepo
import com.questline.app.domain.ProgressionEngine
import com.questline.app.ui.money.MoneyFormat
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Мозг глобального ассистента: собирает снимок состояния пользователя
 * для промпта и разбирает предложенные моделью действия.
 * Действия НИКОГДА не применяются молча — только кнопкой пользователя.
 */
object AiAssistant {

    data class SuggestedAction(
        val kind: String,           // add_task | add_expense
        val title: String,          // для add_task
        val questKey: String,       // для add_task
        val amountRub: Double?,     // для add_expense
        val categoryName: String,   // для add_expense
    )

    private val validKeys = setOf("PHYSICS", "MIND", "MONEY", "SOCIAL", "DISCIPLINE")

    /** Текстовый снимок состояния приложения для системного промпта. */
    suspend fun buildContext(repo: AppRepo): String {
        val today = AppRepo.todayEpochDay
        val done = repo.quests.allDone()
        val level = ProgressionEngine.levelFromTotal(ProgressionEngine.totalXp(done))
        val closedDays = done.mapNotNull { q ->
            q.closedAtMillis?.let {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
            }
        }.toSet()
        val streak = ProgressionEngine.currentStreak(closedDays, today)
        val coins = repo.coins.totalCoins()

        val openQuests = repo.quests.observeOpen().first().take(5)
            .joinToString("; ") { it.title }.ifEmpty { "нет" }
        val todayTasks = repo.tasks.observeForToday(today).first().take(7)
            .joinToString("; ") { "${it.title}${if (it.done) " (выполнена)" else ""}" }
            .ifEmpty { "нет" }

        val monthStart = LocalDate.ofEpochDay(today).withDayOfMonth(1).toEpochDay()
        val monthTxns = repo.txns.observeRange(monthStart, today).first()
        val income = monthTxns.filter { it.type == "INCOME" }.sumOf { it.amountMinor }
        val expense = monthTxns.filter { it.type == "EXPENSE" }.sumOf { it.amountMinor }

        val budgets = repo.categories.all()
            .filter { it.kind == "FINANCE" && it.budgetMonthlyMinor != null && it.budgetMonthlyMinor!! > 0 }
            .map { cat ->
                val spent = repo.txns.spentInCategory(cat.id, monthStart, today)
                val pct = (spent * 100 / cat.budgetMonthlyMinor!!).toInt()
                "${cat.name}: ${MoneyFormat.text(spent)} из ${MoneyFormat.text(cat.budgetMonthlyMinor)} ($pct%)"
            }
            .joinToString("; ")
            .ifEmpty { "бюджеты не заданы" }

        return """
            Сегодня ${LocalDate.ofEpochDay(today)}.
            Уровень ${level.level}, XP ${level.xpIntoLevel}/${level.xpNeeded}, стрик $streak дн., монет $coins.
            Открытые квесты: $openQuests.
            Задачи на сегодня: $todayTasks.
            В этом месяце: доходы ${MoneyFormat.text(income)}, расходы ${MoneyFormat.text(expense)}.
            Бюджеты: $budgets.
        """.trimIndent()
    }

    val SYSTEM_PROMPT = """
        Ты — встроенный AI-ассистент приложения Questline: ежедневника с квестами
        и учётом финансов. Твои принципы: добрый тон, краткость (до 120 слов),
        конкретика, никаких стыда и наказаний. Пиши по-русски, без markdown.
        Отвечай на вопросы по данным пользователя, помогай планировать день,
        советуй по деньгам, мотивируй.
        Если предлагаешь КОНКРЕТНОЕ действие, добавь В САМОМ КОНЦЕ ответа одну
        строку вида:
        ACTION:{"action":"add_task","title":"...","questKey":"PHYSICS|MIND|MONEY|SOCIAL|DISCIPLINE"}
        — предложить добавить задачу;
        ACTION:{"action":"add_expense","amountRub":350,"categoryName":"Продукты"}
        — предложить записать расход. Не более одного ACTION на ответ.
        Если действие не нужно — строку ACTION не добавляй.
    """.trimIndent()

    /** Отделить чистый текст от предложенного действия. */
    fun parseReply(reply: String): Pair<String, SuggestedAction?> {
        val marker = "ACTION:"
        val idx = reply.lastIndexOf(marker)
        if (idx < 0) return reply.trim() to null
        val text = reply.substring(0, idx).trim()
        return try {
            val json = org.json.JSONObject(reply.substring(idx + marker.length).trim())
            val action = when (json.optString("action")) {
                "add_task" -> SuggestedAction(
                    kind = "add_task",
                    title = json.optString("title").trim().take(80),
                    questKey = json.optString("questKey", "DISCIPLINE").uppercase()
                        .let { if (it in validKeys) it else "DISCIPLINE" },
                    amountRub = null,
                    categoryName = "",
                )
                "add_expense" -> SuggestedAction(
                    kind = "add_expense",
                    title = "",
                    questKey = "",
                    amountRub = json.optDouble("amountRub").takeIf { it > 0 && it.isFinite() },
                    categoryName = json.optString("categoryName").trim(),
                )
                else -> null
            }
            text to action?.takeIf { (it.kind == "add_task" && it.title.isNotEmpty()) || (it.kind == "add_expense" && it.amountRub != null) }
        } catch (_: Exception) {
            text to null
        }
    }
}
