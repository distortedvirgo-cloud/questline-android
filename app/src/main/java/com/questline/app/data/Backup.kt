package com.questline.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * T-15: экспорт/импорт всех данных приложения в один JSON-файл.
 * Id сохраняются как есть, поэтому восстановление делает
 * clearAllTables + вставка всех списков с REPLACE.
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
)

object Backup {
    const val CURRENT_VERSION = 1

    private val json = Json { prettyPrint = true }

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
     * Прочитать JSON из потока, очистить БД и вставить все списки заново.
     * Внимание: clearAllTables нельзя вызывать с main-потока —
     * вызывающий обязан обернуть в IO-диспетчер.
     *
     * @return суммарное число вставленных записей.
     */
    suspend fun restore(repo: AppRepo, context: Context, input: InputStream): Int {
        val data = input.use { stream ->
            json.decodeFromString<BackupData>(stream.readBytes().decodeToString())
        }
        require(data.version != 0) { "Неверная версия бэкапа: ${data.version}" }

        QuestlineDatabase.get(context).clearAllTables()

        // Порядок важен: категории раньше всего, на что ссылается.
        repo.categories.insertAllReplace(data.categories)
        repo.tasks.insertAll(data.tasks)
        repo.quests.insertAll(data.quests)
        repo.txns.insertAll(data.txns)
        repo.pending.insertAll(data.pending)
        repo.goals.insertAll(data.goals)
        repo.coins.insertAll(data.coins)

        return data.categories.size + data.tasks.size + data.quests.size +
            data.txns.size + data.pending.size + data.goals.size + data.coins.size
    }
}
