package com.questline.app.data

import com.questline.app.data.habits.Habit
import com.questline.app.data.habits.HabitCheck
import com.questline.app.data.xp.XpLedger
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * T-15/T-16: экспорт/импорт всех данных приложения в один JSON-файл.
 * Id сохраняются как есть, поэтому восстановление делает очистку всех
 * таблиц + вставку всех списков с REPLACE.
 *
 * Формат version=3 (Questline v3): к секциям v2.8 добавлены habits,
 * habitChecks и xpLedger. Новые секции имеют пустые значения по умолчанию,
 * поэтому файл старого формата v2.8 (version=1, без этих секций)
 * читается и восстанавливается без ошибок.
 */
@Serializable
data class BackupData(
    val version: Int,
    val exportedAtMillis: Long,
    val categories: List<Category>,
    val tasks: List<Task>,
    val quests: List<Quest>,
    val txns: List<Txn>,
    val pending: List<PendingTxn>,
    val goals: List<Goal>,
    val coins: List<CoinsLedger>,
    // --- v3 (T-16): в файлах формата v2.8 эти секции отсутствуют ---
    val habits: List<Habit> = emptyList(),
    val habitChecks: List<HabitCheck> = emptyList(),
    val xpLedger: List<XpLedger> = emptyList(),
)

object Backup {
    const val CURRENT_VERSION = 3

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    /** Полный снимок всех таблиц БД. */
    suspend fun snapshot(repo: AppRepo): BackupData = BackupData(
        version = CURRENT_VERSION,
        exportedAtMillis = System.currentTimeMillis(),
        categories = repo.categories.all(),
        tasks = repo.tasks.all(),
        quests = repo.quests.all(),
        txns = repo.txns.all(),
        pending = repo.pending.all(),
        goals = repo.goals.all(),
        coins = repo.coins.all(),
        habits = repo.habits.all(),
        habitChecks = repo.habitChecks.all(),
        xpLedger = repo.xpLedger.all(),
    )

    /** Сериализовать снапшот в pretty-printed JSON и записать в поток. */
    suspend fun exportTo(repo: AppRepo, output: OutputStream) {
        val text = json.encodeToString(snapshot(repo))
        output.use { stream ->
            stream.write(text.toByteArray(Charsets.UTF_8))
            stream.flush()
        }
    }

    /**
     * Прочитать JSON из потока, очистить БД репозитория и вставить все списки
     * заново (стратегия v2.8 — полная замена). Очистка идёт через DAO, поэтому
     * работает и над in-memory базой (тесты), не только над диск-синглтоном.
     *
     * @return суммарное число вставленных записей.
     */
    suspend fun restore(repo: AppRepo, input: InputStream): Int {
        val data = input.use { stream ->
            json.decodeFromString<BackupData>(stream.readBytes().decodeToString())
        }
        require(data.version in 1..CURRENT_VERSION) { "Неверная версия бэкапа: ${data.version}" }

        clearAll(repo)

        // Порядок важен: категории раньше всего, на что ссылается; отметки
        // после привычек — их habitId ссылается на habits.
        repo.categories.insertAllReplace(data.categories)
        repo.tasks.insertAll(data.tasks)
        repo.quests.insertAll(data.quests)
        repo.txns.insertAll(data.txns)
        repo.pending.insertAll(data.pending)
        repo.goals.insertAll(data.goals)
        repo.coins.insertAll(data.coins)
        repo.habits.insertAll(data.habits)

        // Целостность v3 (T-16): id сохраняются при REPLACE, поэтому чеки
        // ссылаются на те же id привычек; подстраховка от осиротевших ссылок
        // (битый/свернутый руками файл) — такие чеки пропускаем.
        val habitIds = data.habits.map { it.id }.toSet()
        val checks = data.habitChecks.filter { it.habitId in habitIds }
        repo.habitChecks.insertAll(checks)
        // Журнал XP вставляется целиком: уровень считается on-demand суммой,
        // refId записей HABIT/MILESTONE указывают на сохранённые id чеков.
        repo.xpLedger.insertAll(data.xpLedger)

        return data.categories.size + data.tasks.size + data.quests.size +
            data.txns.size + data.pending.size + data.goals.size + data.coins.size +
            data.habits.size + checks.size + data.xpLedger.size
    }

    /** Очистить все таблицы перед восстановлением (журналы — первыми). */
    private suspend fun clearAll(repo: AppRepo) {
        repo.xpLedger.clearAll()
        repo.habitChecks.clearAll()
        repo.habits.clearAll()
        repo.coins.clearAll()
        repo.pending.clearAll()
        repo.txns.clearAll()
        repo.goals.clearAll()
        repo.quests.clearAll()
        repo.tasks.clearAll()
        repo.categories.clearAll()
    }
}
