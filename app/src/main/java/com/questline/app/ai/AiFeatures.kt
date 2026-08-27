package com.questline.app.ai

import android.content.Context
import com.questline.app.data.AppRepo
import com.questline.app.data.Quest
import com.questline.app.domain.ProgressionEngine
import org.json.JSONObject

/**
 * AI-фичи: персональный квест дня и совет недели.
 * Промпты требуют JSON-ответ и валидируют его с белыми списками —
 * ответ модели никогда не попадает в БД без санитизации.
 */
object AiFeatures {

    private val validKeys = setOf("PHYSICS", "MIND", "MONEY", "SOCIAL", "DISCIPLINE")
    private val validComplexity = setOf("S", "M", "L")

    data class AiQuest(val title: String, val questKey: String, val complexity: String)

    /** Сгенерировать и сохранить персональный квест дня. Максимум 1/день. */
    suspend fun generateDailyQuest(ctx: Context, repo: AppRepo, todayEpochDay: Long): AiQuest {
        check(AiPrefs.isConfigured(ctx)) { "AI не настроен" }
        check(AiPrefs.lastAiQuestDay(ctx) != todayEpochDay) { "AI-квест на сегодня уже создан" }

        val done = repo.quests.allDone()
        val keyXp = ProgressionEngine.keyXp(done)
        val recentTitles = done.sortedByDescending { it.closedAtMillis ?: 0 }
            .take(10)
            .joinToString("; ") { it.title }
            .ifEmpty { "пока ничего" }
        val keyStats = validKeys.joinToString(", ") { key ->
            val ru = keyRu(key)
            "$ru=${keyXp[key] ?: 0}"
        }

        val answer = AiClient.chat(
            baseUrl = AiPrefs.baseUrl(ctx),
            apiKey = AiPrefs.apiKey(ctx),
            model = AiPrefs.model(ctx),
            messages = listOf(
                "system" to """
                    Ты — добрый коуч в приложении-ежедневнике Questline. Придумай ОДНУ конкретную,
                    выполнимую за день задачу для пользователя. Не наказывай, вдохновляй.
                    XP по сферам: $keyStats (чем меньше — тем сильнее стоит подтянуть).
                    Недавние задачи пользователя: $recentTitles.
                    Ответ строго в JSON без markdown: {"title":"...","questKey":"PHYSICS|MIND|MONEY|SOCIAL|DISCIPLINE","complexity":"S|M|L"}
                    title — по-русски, до 60 символов, глагол в начале.
                """.trimIndent(),
                "user" to "Придумай персональный квест на сегодня.",
            ),
        )

        val quest = sanitizeQuest(answer)
        repo.quests.insert(
            Quest(
                source = "AUTO",
                templateId = "AI",
                title = quest.title,
                questKey = quest.questKey,
                complexity = quest.complexity,
                xpReward = ProgressionEngine.xpFor(quest.complexity),
                coinReward = ProgressionEngine.coinsFor(quest.complexity),
                status = "OPEN",
                dateCreatedEpochDay = todayEpochDay,
            ),
        )
        AiPrefs.markAiQuestToday(ctx, todayEpochDay)
        return quest
    }

    /** Совет недели по статистике. Возвращает готовый текст (2–3 совета). */
    suspend fun weekAdvice(ctx: Context, statsSummary: String): String =
        AiClient.chat(
            baseUrl = AiPrefs.baseUrl(ctx),
            apiKey = AiPrefs.apiKey(ctx),
            model = AiPrefs.model(ctx),
            messages = listOf(
                "system" to """
                    Ты — добрый коуч Questline. По статистике недели пользователя дай 2 коротких
                    совета (по 1–2 предложения, по-русски, без markdown и без нумерации).
                    Тон поддерживающий, без стыда и наказаний. Максимум 400 символов.
                """.trimIndent(),
                "user" to statsSummary,
            ),
        )

    private fun sanitizeQuest(raw: String): AiQuest {
        val withoutFence = raw.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val json = JSONObject(withoutFence)
        val title = json.optString("title").trim().take(60)
        require(title.isNotEmpty()) { "Пустой заголовок от модели" }
        val key = json.optString("questKey", "DISCIPLINE").uppercase()
        val complexity = json.optString("complexity", "M").uppercase()
        return AiQuest(
            title = title,
            questKey = if (key in validKeys) key else "DISCIPLINE",
            complexity = if (complexity in validComplexity) complexity else "M",
        )
    }

    fun keyRu(key: String): String = when (key) {
        "PHYSICS" -> "Физика"
        "MIND" -> "Разум"
        "MONEY" -> "Деньги"
        "SOCIAL" -> "Харизма"
        else -> "Дисциплина"
    }
}
