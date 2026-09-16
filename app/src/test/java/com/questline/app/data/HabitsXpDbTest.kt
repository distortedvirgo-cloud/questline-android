package com.questline.app.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import com.questline.app.data.habits.Habit
import com.questline.app.data.habits.HabitCheck
import com.questline.app.data.xp.XpLedger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * T-02: Room in-memory тесты схемы v4 — привычки, отметки, журнал XP.
 * Robolectric + Room in-memory, без эмулятора.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HabitsXpDbTest {

    private lateinit var db: QuestlineDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            QuestlineDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun newHabit(
        title: String = "Зарядка",
        scheduleType: String = "DAILY",
        archivedAt: Long? = null,
    ) = Habit(
        title = title,
        emoji = "💪",
        colorIndex = 3,
        characteristic = "PHYSICS",
        scheduleType = scheduleType,
        weekdaysMask = 0b0111110,
        timesPerWeek = 3,
        intervalDays = 0,
        targetValue = 30.0,
        unit = "мин",
        complexity = "L",
        createdAt = 20_000L,
        archivedAt = archivedAt,
    )

    @Test
    fun habitRoundtrip_allFields() = runBlocking {
        val habit = newHabit(archivedAt = 20_020L)
        val id = db.habitDao().insert(habit)
        val loaded = db.habitDao().byId(id)
        assertEquals(habit.copy(id = id), loaded)
    }

    @Test
    fun habitCheck_insertAndReadBack() = runBlocking {
        val habitId = db.habitDao().insert(newHabit())
        val check = HabitCheck(
            habitId = habitId,
            epochDay = 20_100L,
            value = 6.0,
            createdAtMillis = 1_234_567_890L,
        )
        val checkId = db.habitCheckDao().insert(check)
        val loaded = db.habitCheckDao().observeRangeForHabit(habitId, 20_100L, 20_100L).first().single()
        assertEquals(check.copy(id = checkId), loaded)
        assertEquals(1, db.habitCheckDao().existsForDay(habitId, 20_100L))
        assertEquals(0, db.habitCheckDao().existsForDay(habitId, 20_101L))
    }

    @Test
    fun habitCheck_duplicateSameDay_throws() = runBlocking {
        val habitId = db.habitDao().insert(newHabit())
        db.habitCheckDao().insert(HabitCheck(habitId = habitId, epochDay = 20_100L, createdAtMillis = 1L))
        val thrown = runCatching {
            db.habitCheckDao().insert(HabitCheck(habitId = habitId, epochDay = 20_100L, createdAtMillis = 2L))
        }.exceptionOrNull()
        assertTrue("ожидали SQLiteConstraintException, получили $thrown", thrown is SQLiteConstraintException)
        assertEquals(1, db.habitCheckDao().existsForDay(habitId, 20_100L))
    }

    @Test
    fun habitCheck_sameDayDifferentHabits_allowed() = runBlocking {
        val h1 = db.habitDao().insert(newHabit(title = "Вода"))
        val h2 = db.habitDao().insert(newHabit(title = "Шаги"))
        db.habitCheckDao().insert(HabitCheck(habitId = h1, epochDay = 20_100L, createdAtMillis = 1L))
        db.habitCheckDao().insert(HabitCheck(habitId = h2, epochDay = 20_100L, createdAtMillis = 2L))
        assertEquals(1, db.habitCheckDao().existsForDay(h1, 20_100L))
        assertEquals(1, db.habitCheckDao().existsForDay(h2, 20_100L))
    }

    @Test
    fun habitCheck_frozenRoundtrip() = runBlocking {
        val habitId = db.habitDao().insert(newHabit())
        db.habitCheckDao().insert(
            HabitCheck(habitId = habitId, epochDay = 20_100L, createdAtMillis = 1L, frozen = true),
        )
        val loaded = db.habitCheckDao().observeRangeForHabit(habitId, 20_100L, 20_100L).first().single()
        assertTrue(loaded.frozen)
        // Без явного флага заморозка по умолчанию выключена
        db.habitCheckDao().insert(HabitCheck(habitId = habitId, epochDay = 20_101L, createdAtMillis = 2L))
        val normal = db.habitCheckDao().observeRangeForHabit(habitId, 20_101L, 20_101L).first().single()
        assertTrue(!normal.frozen)
    }

    @Test
    fun xpLedger_insertAndSumTotal() = runBlocking {
        db.xpLedgerDao().insert(XpLedger(delta = 20, source = "TASK", refId = 11L, epochDay = 20_100L, createdAtMillis = 1L))
        db.xpLedgerDao().insert(XpLedger(delta = 8, source = "HABIT", refId = 22L, epochDay = 20_100L, createdAtMillis = 2L))
        db.xpLedgerDao().insert(XpLedger(delta = -3, source = "BUDGET", refId = null, epochDay = 20_101L, createdAtMillis = 3L))
        assertEquals(25, db.xpLedgerDao().observeSumTotal().first())
        assertEquals(25, db.xpLedgerDao().observeSumSince(20_100L).first())
        assertEquals(-3, db.xpLedgerDao().observeSumSince(20_101L).first())
        assertEquals(25, db.xpLedgerDao().sumBetween(20_100L, 20_101L))
        assertEquals(28, db.xpLedgerDao().sumBetween(20_100L, 20_100L))
    }

    @Test
    fun countChecksBetween_countsOnlyHabitInRange() = runBlocking {
        val h1 = db.habitDao().insert(newHabit(title = "Зарядка"))
        val h2 = db.habitDao().insert(newHabit(title = "Чтение"))
        db.habitCheckDao().insert(HabitCheck(habitId = h1, epochDay = 20_100L, createdAtMillis = 1L))
        db.habitCheckDao().insert(HabitCheck(habitId = h1, epochDay = 20_101L, createdAtMillis = 2L))
        db.habitCheckDao().insert(HabitCheck(habitId = h1, epochDay = 20_102L, createdAtMillis = 3L))
        db.habitCheckDao().insert(HabitCheck(habitId = h2, epochDay = 20_101L, createdAtMillis = 4L))
        assertEquals(3, db.habitDao().countChecksBetween(h1, 20_100L, 20_102L))
        assertEquals(2, db.habitDao().countChecksBetween(h1, 20_101L, 20_102L))
        assertEquals(1, db.habitDao().countChecksBetween(h2, 20_100L, 20_102L))
        assertEquals(0, db.habitDao().countChecksBetween(h1, 20_103L, 20_110L))
    }

    @Test
    fun observeRangeForHabit_filtersByHabitAndDayRange() = runBlocking {
        val h1 = db.habitDao().insert(newHabit(title = "Зарядка"))
        val h2 = db.habitDao().insert(newHabit(title = "Чтение"))
        db.habitCheckDao().insert(HabitCheck(habitId = h1, epochDay = 20_100L, createdAtMillis = 1L))
        db.habitCheckDao().insert(HabitCheck(habitId = h1, epochDay = 20_101L, createdAtMillis = 2L))
        db.habitCheckDao().insert(HabitCheck(habitId = h1, epochDay = 20_105L, createdAtMillis = 3L))
        db.habitCheckDao().insert(HabitCheck(habitId = h2, epochDay = 20_101L, createdAtMillis = 4L))

        val week = db.habitCheckDao().observeRangeForHabit(h1, 20_101L, 20_104L).first()
        assertEquals(listOf(20_101L), week.map { it.epochDay })

        val all = db.habitCheckDao().observeRangeForHabit(h1, 20_000L, 20_200L).first()
        assertEquals(listOf(20_100L, 20_101L, 20_105L), all.map { it.epochDay })
    }

    @Test
    fun archive_hidesFromActiveKeepsInAll() = runBlocking {
        val habitId = db.habitDao().insert(newHabit())
        db.habitDao().archive(habitId, 20_050L)
        assertEquals(0, db.habitDao().observeActive().first().size)
        assertEquals(1, db.habitDao().observeAll().first().size)
        val archived = db.habitDao().byId(habitId)!!
        assertEquals(20_050L, archived.archivedAt)
    }
}
