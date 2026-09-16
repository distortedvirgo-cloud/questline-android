package com.questline.app.data

import androidx.room.Room
import com.questline.app.data.habits.Habit
import com.questline.app.domain.habits.HabitEngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * T-07: вехи стрика и платная заморозка на уровне AppRepo — in-memory Room
 * как в HabitsXpDbTest; синглтоны базы и репозитория подменяются швом,
 * чтобы не зависеть от дисковой questline.db.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HabitMilestoneFreezeDbTest {

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
        // Репозиторий строится прямо над in-memory базой (вторичный конструктор)
        repo = AppRepo(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val today: Long get() = AppRepo.todayEpochDay

    private suspend fun habitWithId(createdAt: Long): Habit = repo.habits.byId(
        repo.habits.insert(
            Habit(
                title = "Зарядка",
                emoji = "💪",
                characteristic = "DISCIPLINE",
                scheduleType = "DAILY",
                createdAt = createdAt,
            ),
        ),
    )!!

    // ---------------- Вехи стрика ----------------

    @Test
    fun milestone7_firesExactlyOnSeventhCheck_notEarlier_notLater() = runBlocking {
        val habit = habitWithId(createdAt = 20_000L)
        // Дни 20_000..20_005: стрик растёт 1..6 — вех нет
        for (i in 0..5) {
            assertNull("веха на дне ${20_000L + i} стрика", repo.checkHabit(habit, 20_000L + i).milestone)
        }
        // Седьмой день: ровно 7 — +10 монет и +5 XP в журнал
        val seventh = repo.checkHabit(habit, 20_006L)
        assertEquals(7, seventh.milestone)
        assertEquals(10, repo.coins.totalCoins())
        assertEquals(5, repo.xpLedger.sumBySource(20_006L, "MILESTONE"))
        // Восьмой день — не веха, монеты не растут
        assertNull(repo.checkHabit(habit, 20_007L).milestone)
        assertEquals(10, repo.coins.totalCoins())
    }

    @Test
    fun milestoneGuard_noRepeatAfterUncheckRecheck() = runBlocking {
        val habit = habitWithId(createdAt = 20_000L)
        repeat(7) { repo.checkHabit(habit, 20_000L + it) }
        assertEquals(10, repo.coins.totalCoins())
        // Снять седьмой день и отметить заново: стрик снова 7, но гвард не даёт повтор
        repo.uncheckHabit(habit.id, 20_006L)
        val again = repo.checkHabit(habit, 20_006L)
        assertNull(again.milestone)
        assertEquals(10, repo.coins.totalCoins())
    }

    // ---------------- Платная заморозка ----------------

    @Test
    fun freeze_alreadyCheckedDay_returnsFalse() = runBlocking {
        val habit = habitWithId(createdAt = today - 5)
        repo.checkHabit(habit, today - 1)
        assertFalse(repo.freezeHabitDay(habit, today - 1))
        assertEquals(0, repo.coins.totalCoins())
    }

    @Test
    fun freeze_insufficientCoins_returnsFalse() = runBlocking {
        val habit = habitWithId(createdAt = today - 5)
        assertFalse(repo.freezeHabitDay(habit, today - 1))
        assertNull(repo.habitChecks.byDay(habit.id, today - 1))
    }

    @Test
    fun freeze_futureDay_returnsFalse() = runBlocking {
        val habit = habitWithId(createdAt = today - 5)
        assertFalse(repo.freezeHabitDay(habit, today + 1))
        assertNull(repo.habitChecks.byDay(habit.id, today + 1))
    }

    @Test
    fun freeze_success_costsCoins_writesFrozenCheck() = runBlocking {
        val habit = habitWithId(createdAt = today - 5)
        repo.addCoins(AppRepo.FREEZE_COST, "TEST_SEED")
        assertTrue(repo.freezeHabitDay(habit, today - 1))
        val frozen = repo.habitChecks.byDay(habit.id, today - 1)
        assertNotNull(frozen)
        assertTrue(frozen!!.frozen)
        assertEquals(0, repo.coins.totalCoins())
        val entry = repo.coins.all().first { it.reason == AppRepo.REASON_FREEZE }
        assertEquals(-AppRepo.FREEZE_COST, entry.delta)
        assertEquals(habit.id, entry.refId)
    }

    @Test
    fun freeze_doubleFreeze_singleCharge() = runBlocking {
        val habit = habitWithId(createdAt = today - 5)
        repo.addCoins(100, "TEST_SEED")
        assertTrue(repo.freezeHabitDay(habit, today - 1))
        assertFalse(repo.freezeHabitDay(habit, today - 1))
        assertEquals(80, repo.coins.totalCoins())
    }

    @Test
    fun freeze_bridgesStreak_keepsCurrentStreak() = runBlocking {
        val habit = habitWithId(createdAt = today - 3)
        repo.checkHabit(habit, today - 3) // стрик 1, день today-2 пропущен
        repo.addCoins(AppRepo.FREEZE_COST, "TEST_SEED")
        assertTrue(repo.freezeHabitDay(habit, today - 2))
        // Без заморозки пропуск today-2 рвал бы серию (второй пропуск в окне);
        // с заморозкой день мостится и стрик сохраняется
        val checks = repo.habitChecks.rangeForHabit(habit.id, habit.createdAt, today)
        val (current, _) = HabitEngine.streak(habit, checks, today)
        assertEquals(1, current)
    }
}
