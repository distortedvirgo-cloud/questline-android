package com.questline.app.ui.tasks

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.questline.app.ai.AiClient
import com.questline.app.ai.AiPrefs
import com.questline.app.data.Category
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private const val HINT_NOT_CONFIGURED = "Вставь API-ключ: Настройки → AI-коуч"
private const val HINT_THINKING = "Думаю…"
private const val HINT_FAILED = "Не получилось, поставил M"
private const val HINT_TIMEOUT_MS = 4000L

private val COMPLEXITIES = setOf("S", "M", "L")

private val aiSuggestJson = Json { ignoreUnknownKeys = true }

private val SYSTEM_PROMPT =
    "Ты помогаешь категоризировать бытовые задачи. Ответь ТОЛЬКО минифицированный JSON без markdown: " +
        "{\"complexity\":\"S\",\"category\":null} где complexity — \"S\", \"M\" или \"L\" " +
        "(S — минутное дело, M — час, L — большой кусок работы), а category — название категории из списка или null."

/**
 * Чип AI-подбора сложности и категории для формы задачи.
 * Отвечает только за запрос и разбор ответа — результат отдаётся через [onResult],
 * форму (сложность/категорию) обновляет вызывающий код.
 */
@Composable
fun TaskAiSuggestChip(
    title: String,
    categories: List<Category>,
    onResult: (complexity: String, categoryId: Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hint by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    // Временная подсказка сама исчезает; «Думаю…» живёт, пока идёт запрос
    LaunchedEffect(hint, busy) {
        if (hint != null && !busy) {
            delay(HINT_TIMEOUT_MS)
            hint = null
        }
    }

    Column(modifier = modifier) {
        AssistChip(
            onClick = {
                if (!AiPrefs.isConfigured(context)) {
                    hint = HINT_NOT_CONFIGURED
                } else if (!busy) {
                    busy = true
                    hint = HINT_THINKING
                    val prompt = "Задача: \"$title\". " +
                        "Доступные категории: ${categories.joinToString(", ") { it.name }}. " +
                        "Какая сложность и категория?"
                    scope.launch {
                        var complexity = "M"
                        var categoryId: Long? = null
                        try {
                            val answer = AiClient.chat(
                                AiPrefs.baseUrl(context),
                                AiPrefs.apiKey(context),
                                AiPrefs.model(context),
                                listOf("system" to SYSTEM_PROMPT, "user" to prompt),
                            )
                            val (c, catId) = parseAiSuggestion(answer, categories)
                            complexity = c
                            categoryId = catId
                            hint = null
                        } catch (e: Exception) {
                            hint = HINT_FAILED
                        }
                        busy = false
                        onResult(complexity, categoryId)
                    }
                }
            },
            label = {
                Text("✨ Подобрать", style = MaterialTheme.typography.labelMedium)
            },
            enabled = title.isNotBlank() && !busy,
        )
        hint?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Вырезает JSON из ответа модели (от первого '{' до последнего '}') и достаёт
 * сложность (S/M/L, иначе M) и id категории по имени без учёта регистра.
 * Любая ошибка разбора — исключение, вызывающий код ставит M/null.
 */
private fun parseAiSuggestion(raw: String, categories: List<Category>): Pair<String, Long?> {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    if (start < 0 || end <= start) error("JSON не найден в ответе модели")
    val parsed = aiSuggestJson.decodeFromString<Map<String, JsonElement>>(raw.substring(start, end + 1))

    val rawComplexity = (parsed["complexity"] as? JsonPrimitive)?.contentOrNull?.trim()?.uppercase()
    val complexity = if (rawComplexity in COMPLEXITIES) rawComplexity!! else "M"

    val rawCategory = (parsed["category"] as? JsonPrimitive)?.contentOrNull?.trim()
    val categoryId = categories
        .firstOrNull { rawCategory != null && it.name.equals(rawCategory, ignoreCase = true) }
        ?.id
    return complexity to categoryId
}
