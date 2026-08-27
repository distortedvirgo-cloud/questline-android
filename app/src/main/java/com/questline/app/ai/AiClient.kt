package com.questline.app.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Настройки AI-провайдера. По умолчанию — Z.ai GLM (OpenAI-совместимый API).
 * Ключ пользователь вводит в Настройках; без ключа AI-фичи скрыты.
 */
object AiPrefs {
    private const val PREFS = "ai_prefs"
    private const val KEY_BASE = "base_url"
    private const val KEY_API = "api_key"
    private const val KEY_MODEL = "model"
    private const val KEY_LAST_AI_QUEST_DAY = "last_ai_quest_day"

    const val DEFAULT_BASE_URL = "https://opencode.ai/zen/v1"
    const val DEFAULT_MODEL = "glm-5.2"

    fun baseUrl(ctx: Context) = prefs(ctx).getString(KEY_BASE, DEFAULT_BASE_URL).orEmpty().trim().ifEmpty { DEFAULT_BASE_URL }
    fun apiKey(ctx: Context) = prefs(ctx).getString(KEY_API, "").orEmpty()
    fun model(ctx: Context) = prefs(ctx).getString(KEY_MODEL, DEFAULT_MODEL).orEmpty().trim().ifEmpty { DEFAULT_MODEL }

    fun save(ctx: Context, baseUrl: String, apiKey: String, model: String) {
        prefs(ctx).edit()
            .putString(KEY_BASE, baseUrl.trim())
            .putString(KEY_API, apiKey.trim())
            .putString(KEY_MODEL, model.trim())
            .apply()
    }

    fun isConfigured(ctx: Context) = apiKey(ctx).isNotEmpty()

    /** Лимит: один AI-квест в день */
    fun lastAiQuestDay(ctx: Context) = prefs(ctx).getLong(KEY_LAST_AI_QUEST_DAY, -1L)
    fun markAiQuestToday(ctx: Context, todayEpochDay: Long) {
        prefs(ctx).edit().putLong(KEY_LAST_AI_QUEST_DAY, todayEpochDay).apply()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/**
 * Минимальный клиент OpenAI-совместимого chat/completions через OkHttp
 * (та же библиотека, что у апдейтера — новых зависимостей нет).
 */
object AiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    /** messages — пары (роль, текст). Возвращает текст первого ответа ассистента. */
    suspend fun chat(baseUrl: String, apiKey: String, model: String, messages: List<Pair<String, String>>): String =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("model", model)
                put("temperature", 0.8)
                put("max_tokens", 500)
                put("messages", JSONArray().apply {
                    messages.forEach { (role, text) ->
                        put(JSONObject().put("role", role).put("content", text))
                    }
                })
            }
            val url = baseUrl.trimEnd('/') + "/chat/completions"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody(jsonType))
                .build()
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw RuntimeException("HTTP ${response.code}: ${raw.take(200)}")
                }
                val json = JSONObject(raw)
                json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()
            }
        }
}
