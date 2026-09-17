package com.questline.app.data

import androidx.room.Room
import com.questline.app.data.habits.Habit
import com.questline.app.data.habits.HabitCheck
import com.questline.app.data.xp.XpLedger
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * T-16: round-trip бэкапа v3 на in-memory Room — экспорт → импорт в чистую
 * базу → списки совпадают. Плюс совместимость: файл старого формата v2.8
 * (version=1, без секций v3) импортируется без ошибок.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRoundTripTest {

    private lateinit var db: QuestlineDatabase
    private lateinit var repo: AppRepo

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            QuestlineDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        repo = AppRepo(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** Наполнить базу образцами всех таблиц (id заданы явно для ссылок). */
    private suspend fun seed(repo: AppRepo) {
        repo.categories.insertAllReplace(
            listOf(
                Category(id = 1, name = "Физика", kind = "QUEST", questKey = "PHYSICS", emoji = "💪", colorIndex = 0),
                Category(id = 2, name = "Продукты", kind = "FINANCE", emoji = "🍎", colorIndex = 0),
            ),
        )
        repo.tasks.insertAll(
            listOf(
                Task(id = 1, title = "Задача", categoryId = 1, done = true, doneAtMillis = 5L, createdAtMillis = 1L),
            ),
        )
        repo.quests.insertAll(
            listOf(
                Quest(
                    id = 1, taskId = 1, source = "USER", title = "Квест", questKey = "PHYSICS",
                    complexity = "M", xpReward = 8, coinReward = 5, status = "DONE",
                    dateCreatedEpochDay = 20_000L, closedAtMillis = 6L,
                ),
            ),
        )
        repo.txns.insertAll(
            listOf(
                Txn(id = 1, amountMinor = 150_00, type = "EXPENSE", categoryId = 2, epochDay = 20_001L, createdAtMillis = 2L),
            ),
        )
        repo.pending.insertAll(
            listOf(
                PendingTxn(
                    id = 1, bankPackage = "ru.bank", title = "Покупка", text = "Карта ••1234",
                    amountMinor = 500, type = "EXPENSE", epochDay = 20_001L, receivedMillis = 3L,
                ),
            ),
        )
        repo.goals.insertAll(
            listOf(
                Goal(id = 1, name = "Копилка", targetMinor = 100_000, savedMinor = 25_000),
            ),
        )
        repo.coins.insertAll(
            listOf(
                CoinsLedger(id = 1, delta = 5, reason = "QUEST_DONE", refId = 1, createdAtMillis = 6L),
                CoinsLedger(id = 2, delta = -20, reason = "HABIT_FREEZE", refId = 1, createdAtMillis = 7L),
            ),
        )
        repo.habits.insertAll(
            listOf(
                Habit(
                    id = 1, title = "Зарядка", emoji = "💪", characteristic = "DISCIPLINE",
                    scheduleType = "DAILY", complexity = "S", createdAt = 19_900L,
                ),
            ),
        )
        repo.habitChecks.insertAll(
            listOf(
                HabitCheck(id = 1, habitId = 1, epochDay = 20_000L, createdAtMillis = 8L),
                HabitCheck(id = 2, habitId = 1, epochDay = 20_001L, value = 3.0, createdAtMillis = 9L),
                HabitCheck(id = 3, habitId = 1, epochDay = 20_002L, value = null, createdAtMillis = 10L, frozen = true),
            ),
        )
        repo.xpLedger.insertAll(
            listOf(
                XpLedger(id = 1, delta = 30, source = "QUEST", refId = 1, epochDay = 20_000L, createdAtMillis = 6L),
                XpLedger(id = 2, delta = 5, source = "HABIT", refId = 1, epochDay = 20_000L, createdAtMillis = 8L),
                XpLedger(id = 3, delta = 5, source = "MILESTONE", refId = 3, epochDay = 20_002L, createdAtMillis = 10L),
            ),
        )
    }

    @Test
    fun roundTrip_exportThenImport_allSectionsIdentical() = runBlocking {
        seed(repo)

        val out = ByteArrayOutputStream()
        Backup.exportTo(repo, out)

        val db2 = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            QuestlineDatabase::class.java,
        ).allowMainThreadQueries().build()
        val repo2 = AppRepo(db2)
        try {
            val restored = Backup.restore(repo2, ByteArrayInputStream(out.toByteArray()))
            assertEquals(2 + 1 + 1 + 1 + 1 + 1 + 2 + 1 + 3 + 3, restored)

            assertEquals(repo.categories.all(), repo2.categories.all())
            assertEquals(repo.tasks.all(), repo2.tasks.all())
            assertEquals(repo.quests.all(), repo2.quests.all())
            assertEquals(repo.txns.all(), repo2.txns.all())
            assertEquals(repo.pending.all(), repo2.pending.all())
            assertEquals(repo.goals.all(), repo2.goals.all())
            assertEquals(repo.coins.all(), repo2.coins.all())
            assertEquals(repo.habits.all(), repo2.habits.all())
            assertEquals(repo.habitChecks.all(), repo2.habitChecks.all())
            assertEquals(repo.xpLedger.all(), repo2.xpLedger.all())
            // Монеты и XP считаются из журналов — итог после восстановления тот же
            assertEquals(repo.coins.totalCoins(), repo2.coins.totalCoins())
            assertEquals(-15, repo2.coins.totalCoins())
            assertEquals(40, repo2.xpLedger.sumBetween(0, AppRepo.todayEpochDay))
        } finally {
            db2.close()
        }
    }

    @Test
    fun export_v3Json_containsNewSections() = runBlocking {
        seed(repo)
        val out = ByteArrayOutputStream()
        Backup.exportTo(repo, out)
        val obj = kotlinx.serialization.json.Json.parseToJsonElement(
            out.toString("UTF-8"),
        ).jsonObject
        assertEquals(3, obj["version"]!!.jsonPrimitive.content.toInt())
        // Новые секции v3 присутствуют в JSON всегда (encodeDefaults)
        assertEquals(1, obj["habits"]!!.jsonArray.size)
        assertEquals(3, obj["habitChecks"]!!.jsonArray.size)
        assertEquals(3, obj["xpLedger"]!!.jsonArray.size)
        assertEquals("frozen", obj["habitChecks"]!!.jsonArray[2].jsonObject.keys.first { it == "frozen" })
    }

    @Test
    fun import_legacyV28File_withoutNewSections_succeeds() = runBlocking {
        val legacy = """
            {
              "version": 1,
              "exportedAtMillis": 1000,
              "categories": [
                {"id": 1, "name": "Продукты", "kind": "FINANCE", "emoji": "🍎", "colorIndex": 0}
              ],
              "tasks": [{"id": 1, "title": "Старая задача", "createdAtMillis": 1}],
              "quests": [],
              "txns": [
                {"id": 1, "amountMinor": 9900, "type": "EXPENSE", "categoryId": 1,
                 "epochDay": 19900, "note": "", "source": "MANUAL", "isPlanned": false,
                 "createdAtMillis": 2}
              ],
              "pending": [],
              "goals": [],
              "coins": []
            }
        """.trimIndent()

        val restored = Backup.restore(repo, legacy.byteInputStream())
        assertEquals(3, restored) // 1 категория + 1 задача + 1 транзакция

        assertEquals(1, repo.categories.all().size)
        assertEquals("Старая задача", repo.tasks.all().single().title)
        assertEquals(1, repo.txns.all().size)
        // Секции v3 после импорта старого формата пусты, ошибок нет
        assertTrue(repo.habits.all().isEmpty())
        assertTrue(repo.habitChecks.all().isEmpty())
        assertTrue(repo.xpLedger.all().isEmpty())
    }

    @Test
    fun import_v3FileWithOrphanChecks_skipsBrokenRefs() = runBlocking {
        seed(repo)
        val out = ByteArrayOutputStream()
        Backup.exportTo(repo, out)
        val json = out.toString("UTF-8")
            .replace("\"habitId\": 1", "\"habitId\": 999") // ломаем все ссылки
        val db2 = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            QuestlineDatabase::class.java,
        ).allowMainThreadQueries().build()
        val repo2 = AppRepo(db2)
        try {
            Backup.restore(repo2, json.byteInputStream())
            assertTrue(repo2.habitChecks.all().isEmpty())
            assertEquals(1, repo2.habits.all().size) // привычка восстановлена
        } finally {
            db2.close()
        }
    }
}
