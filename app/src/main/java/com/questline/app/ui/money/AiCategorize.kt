package com.questline.app.ui.money

import android.content.Context
import com.questline.app.ai.AiClient
import com.questline.app.ai.AiPrefs
import com.questline.app.data.Category
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private const val RAW_LIMIT = 300

private val aiCategorizeJson = Json { ignoreUnknownKeys = true }

private val SYSTEM_PROMPT =
    "Ты категоризируешь банковские операции по тексту пуша. Ответь ТОЛЬКО минифицированный JSON без markdown: " +
        "{\"category\":\"<название>\"} где название — строго одна из перечисленных категорий. " +
        "Если ни одна не подходит, ответь {\"category\":null}."

/**
 * AI-подбор финансовой категории для операции из пуша. null — если AI не
 * настроен, сеть недоступна или модель не дала валидный ответ.
 */
suspend fun aiSuggestCategoryId(
    context: Context,
    raw: String,
    categories: List<Category>,
): Long? {
    if (!AiPrefs.isConfigured(context)) return null
    if (categories.isEmpty()) return null
    return try {
        val userPrompt = "Операция: \"${raw.take(RAW_LIMIT)}\". " +
            "Категории: ${categories.joinToString(", ") { it.name }}."
        val answer = AiClient.chat(
            AiPrefs.baseUrl(context),
            AiPrefs.apiKey(context),
            AiPrefs.model(context),
            listOf("system" to SYSTEM_PROMPT, "user" to userPrompt),
        )
        parseCategoryResponse(answer, categories)
    } catch (e: Throwable) {
        null
    }
}

/**
 * Вырезает JSON из ответа модели (от первого '{' до последнего '}') и достаёт
 * id категории по имени без учёта регистра. Любая ошибка разбора — исключение,
 * вызывающий код ставит null.
 */
private fun parseCategoryResponse(raw: String, categories: List<Category>): Long? {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start < 0 || end <= start) error("JSON не найден в ответе модели")
    val parsed = aiCategorizeJson.decodeFromString<Map<String, JsonElement>>(raw.substring(start, end + 1))

    val name = (parsed["category"] as? JsonPrimitive)
        ?.takeIf { it.isString }
        ?.contentOrNull
        ?.trim()
    if (name.isNullOrEmpty() || name == "null") return null
    return categories.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
}
