package com.questline.app.ui.money

import android.content.Context
import com.questline.app.ai.AiClient
import com.questline.app.ai.AiPrefs
import com.questline.app.data.Category
import com.questline.app.data.PendingTxn
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Настройки пакетного AI-разбора очереди пушей. */
object AutoProcessPrefs {
    private const val PREFS = "auto_process_prefs"
    private const val KEY = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
    }
}

/** Решение по одной карточке инбокса: action = "confirm" | "discard" | "ask". */
data class AiAutoDecision(
    val pendingId: Long,
    val action: String,
    val categoryId: Long?,
    val note: String?,
)

@Serializable
private data class AutoResponse(val items: List<AutoItem> = emptyList())

@Serializable
private data class AutoItem(
    val i: Int = -1,
    val action: String = "",
    val category: String? = null,
    val note: String? = null,
)

private val autoProcessJson = Json { ignoreUnknownKeys = true }

private val autoProcessTimeFormat = DateTimeFormatter.ofPattern("dd.MM HH:mm")

private val SYSTEM_PROMPT =
    "Ты — финансовый ассистент приложения личного учёта. Перед тобой хронологический список " +
        "банковских уведомлений пользователя (время указано у каждого). Для каждого пункта прими решение: " +
        "\"confirm\" — обычная трата или поступление (обязательно укажи категорию строго из списка); " +
        "\"discard\" — мусор, служебное уведомление или точный дубль; " +
        "\"ask\" — неуверенность: переводы между своими счетами/картами, балансы без операции, непонятный текст. " +
        "Учитывай время и порядок операций (последовательные списание и зачисление могут быть переводом). " +
        "Ответь ТОЛЬКО минифицированный JSON без markdown: " +
        "{\"items\":[{\"i\":1,\"action\":\"confirm\",\"category\":\"Продукты\",\"note\":\"до 40 символов или null\"}]} " +
        "— items для КАЖДОГО пункта."

/**
 * Пакетный разбор очереди пушей: один запрос, операции в хронологическом
 * порядке (модель видит время каждой). Возвращает решения только по тем
 * карточкам, что удалось разобрать; сбой сети/парсинга -> пустой список.
 */
suspend fun aiAutoProcess(
    context: Context,
    cards: List<PendingTxn>,
    categories: List<Category>,
): List<AiAutoDecision> {
    if (!AiPrefs.isConfigured(context)) return emptyList()
    if (cards.isEmpty() || categories.isEmpty()) return emptyList()

    val sorted = cards.sortedBy { it.receivedMillis }
    val listText = sorted.mapIndexed { index, card -> buildCardLine(index + 1, card) }
        .joinToString("\n")
    val userPrompt = listText + "\nКатегории: ${categories.joinToString(", ") { it.name }}."

    val answer = try {
        AiClient.chat(
            AiPrefs.baseUrl(context),
            AiPrefs.apiKey(context),
            AiPrefs.model(context),
            listOf("system" to SYSTEM_PROMPT, "user" to userPrompt),
        )
    } catch (e: Throwable) {
        return emptyList()
    }

    return parseAutoResponse(answer, sorted, categories)
}

/** "[1] 28.08 14:07 · Расход 320,00 ₽ · Супермаркет Пятерочка оплата" */
private fun buildCardLine(index: Int, card: PendingTxn): String {
    val time = autoProcessTimeFormat.format(
        Instant.ofEpochMilli(card.receivedMillis).atZone(ZoneId.systemDefault())
    )
    val kind = if (card.type == "INCOME") "Доход" else "Расход"
    val body = (card.title + " " + card.text).trim().take(200)
    return "[$index] $time · $kind ${formatMinor(card.amountMinor)} · $body"
}

/** Копейки -> «320,00 ₽» (рубли, запятая, 2 знака). */
private fun formatMinor(minor: Long): String {
    val abs = if (minor < 0) -minor else minor
    val sign = if (minor < 0) "-" else ""
    return "$sign${abs / 100},${(abs % 100).toString().padStart(2, '0')} \u20BD"
}

/**
 * Вырезает JSON из ответа модели (от первого '{' до последнего '}') и маппит
 * пункты на карточки по номеру. Пункт без распознанной категории при
 * "confirm" понижается до "ask"; номер вне диапазона или неизвестное действие
 * решение отбрасывают/страхуют.
 */
private fun parseAutoResponse(
    raw: String,
    sorted: List<PendingTxn>,
    categories: List<Category>,
): List<AiAutoDecision> {
    return try {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) error("JSON не найден в ответе модели")
        val parsed = autoProcessJson.decodeFromString<AutoResponse>(raw.substring(start, end + 1))

        parsed.items.mapNotNull { item ->
            if (item.i < 1 || item.i > sorted.size) return@mapNotNull null
            val card = sorted[item.i - 1]
            var action = if (item.action in setOf("confirm", "discard", "ask")) item.action else "ask"
            val categoryId = item.category
                ?.trim()
                ?.takeIf { it.isNotEmpty() && it != "null" }
                ?.let { name -> categories.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id }
            if (action == "confirm" && categoryId == null) action = "ask"
            AiAutoDecision(card.id, action, categoryId, item.note?.trim()?.takeIf { it.isNotEmpty() })
        }
    } catch (e: Throwable) {
        emptyList()
    }
}
