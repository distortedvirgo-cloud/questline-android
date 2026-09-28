package com.questline.app.ui.money

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
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
 *
 * Дополнительно хранит: маппинг «last4 счёта → last4 карты» (пуши о переводе
 * себе пишут last4 счёта) и последние необъяснённые дельты карт для поиска
 * зеркальной пары (см. TransferHeuristics).
 */
object AccountsPrefs {

    private const val PREFS = "accounts_prefs"
    private const val KEY_ACCOUNTS = "accounts_json"
    private const val KEY_ACCOUNT_MAP = "account_map_json"
    private const val KEY_UNEXPLAINED_DELTAS = "unexplained_deltas_json"

    private val json = Json { ignoreUnknownKeys = true }
    private val accountMapFormat = MapSerializer(String.serializer(), String.serializer())
    private val deltaMapFormat = MapSerializer(String.serializer(), DeltaEntry.serializer())

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
     * и ищет заведённую карту с такими последними цифрами. Если прямой матч
     * не удался, а в тексте есть «счёт ••XXXX» — пробует маппинг
     * «last4 счёта → last4 карты» (см. rememberAccountMap).
     */
    fun findByLast4(context: Context, text: String): Account? {
        val last4 = extractLast4(text) ?: return null
        list(context).firstOrNull { it.last4 == last4 }?.let { return it }
        val cardLast4 = mapAccountToCard(context, last4) ?: return null
        return list(context).firstOrNull { it.last4 == cardLast4 }
    }

    /** Последние 4 цифры карты, закреплённой за счётом с last4 [accountLast4], или null. */
    fun mapAccountToCard(context: Context, accountLast4: String): String? =
        readStringMap(context, KEY_ACCOUNT_MAP)[accountLast4]

    /** Запомнить, что пушам со «счёт ••accountLast4» соответствует карта ••cardLast4. */
    fun rememberAccountMap(context: Context, accountLast4: String, cardLast4: String) {
        writeStringMap(context, KEY_ACCOUNT_MAP, readStringMap(context, KEY_ACCOUNT_MAP) + (accountLast4 to cardLast4))
    }

    /** Сохранить последнюю необъяснённую дельту карты (перезаписывает прежнюю). */
    fun putUnexplainedDelta(context: Context, cardLast4: String, deltaMinor: Long, millis: Long) {
        val map = readDeltaMap(context) + (cardLast4 to DeltaEntry(deltaMinor, millis))
        prefs(context).edit()
            .putString(KEY_UNEXPLAINED_DELTAS, json.encodeToString(deltaMapFormat, map))
            .apply()
    }

    /** Последние необъяснённые дельты: (cardLast4, deltaMinor, millis). */
    fun unexplainedDeltas(context: Context): List<Triple<String, Long, Long>> =
        readDeltaMap(context).map { (last4, entry) -> Triple(last4, entry.deltaMinor, entry.millis) }

    /** Убрать дельту карты — после того как она объяснена (зеркальная пара найдена). */
    fun clearUnexplainedDelta(context: Context, cardLast4: String) {
        prefs(context).edit()
            .putString(KEY_UNEXPLAINED_DELTAS, json.encodeToString(deltaMapFormat, readDeltaMap(context) - cardLast4))
            .apply()
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

    private fun readStringMap(context: Context, key: String): Map<String, String> {
        val raw = prefs(context).getString(key, null) ?: return emptyMap()
        return runCatching { json.decodeFromString(accountMapFormat, raw) }.getOrDefault(emptyMap())
    }

    private fun writeStringMap(context: Context, key: String, map: Map<String, String>) {
        prefs(context).edit().putString(key, json.encodeToString(accountMapFormat, map)).apply()
    }

    private fun readDeltaMap(context: Context): Map<String, DeltaEntry> {
        val raw = prefs(context).getString(KEY_UNEXPLAINED_DELTAS, null) ?: return emptyMap()
        return runCatching { json.decodeFromString(deltaMapFormat, raw) }.getOrDefault(emptyMap())
    }
}

/** Необъяснённая дельта карты: сколько копеек не покрыли операции и когда замечено. */
@Serializable
private data class DeltaEntry(val deltaMinor: Long, val millis: Long)
