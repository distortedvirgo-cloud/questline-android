package com.questline.app.ui.money

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Карта/счёт, заведённая пользователем во вкладке «Деньги».
 *
 * @param last4        последние 4 цифры номера — по ним карта матчится с банковскими пушами
 * @param balanceMinor актуальный остаток в копейках (абсолютное значение: пуш с остатком
 *                     или ручной ввод перезаписывают его целиком)
 * @param anchorMillis момент последнего подтверждения остатка
 */
@Serializable
data class Account(
    val id: Long,
    val name: String,
    val last4: String,
    val balanceMinor: Long,
    val anchorMillis: Long,
)

/**
 * Хранение карт/счетов: SharedPreferences «accounts_prefs», JSON-массив через
 * kotlinx.serialization. Балансы карт — абсолютные: остаток из пуша просто
 * перезаписывает прежнее значение (см. upsertBalance), поток операций не учитывается.
 */
object AccountsPrefs {

    private const val PREFS = "accounts_prefs"
    private const val KEY_ACCOUNTS = "accounts_json"

    private val json = Json { ignoreUnknownKeys = true }

    fun list(context: Context): List<Account> {
        val raw = prefs(context).getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(Account.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, accounts: List<Account>) {
        val raw = json.encodeToString(ListSerializer(Account.serializer()), accounts)
        prefs(context).edit().putString(KEY_ACCOUNTS, raw).apply()
    }

    /**
     * Найти карту по тексту банковского пуша: берёт 4 цифры после маркеров
     * «••»/«*»/«счёт»/«карта» («Плат. счёт •• 5129», «Карта *5129», «счёт 5129»)
     * и ищет заведённую карту с такими последними цифрами.
     */
    fun findByLast4(context: Context, text: String): Account? {
        val last4 = extractLast4(text) ?: return null
        return list(context).firstOrNull { it.last4 == last4 }
    }

    fun upsertBalance(context: Context, id: Long, balanceMinor: Long, anchorMillis: Long) {
        val accounts = list(context)
        val index = accounts.indexOfFirst { it.id == id }
        if (index < 0) return
        val updated = accounts.toMutableList()
        updated[index] = updated[index].copy(balanceMinor = balanceMinor, anchorMillis = anchorMillis)
        save(context, updated)
    }

    /** 4 цифры после маркеров «••»/«*»/«счёт»/«карта» в тексте пуша. */
    private fun extractLast4(text: String): String? =
        LAST4_PATTERNS.firstNotNullOfOrNull { regex -> regex.find(text)?.groupValues?.get(1) }

    private val LAST4_PATTERNS = listOf(
        // «•• 5129», «•••• 5129», «*5129»
        Regex("[•*·]\\s*(\\d{4})(?!\\d)"),
        // «счёт 5129», «Карта *5129», «по карте 5129»
        Regex("(?:счёт|счет|карт[ауые])\\s*\\**\\s*(\\d{4})(?!\\d)", RegexOption.IGNORE_CASE),
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
