package com.questline.app.data.habits

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Insert
    suspend fun insert(habit: Habit): Long

    @Update
    suspend fun update(habit: Habit)

    /** Архивация: пометить датой архивации (null внутри habit не трогаем — обновляем колонку) */
    @Query("UPDATE habits SET archivedAt = :epochDay WHERE id = :id")
    suspend fun archive(id: Long, epochDay: Long)

    @Query("SELECT * FROM habits ORDER BY id")
    fun observeAll(): Flow<List<Habit>>

    @Query("SELECT * FROM habits WHERE archivedAt IS NULL ORDER BY id")
    fun observeActive(): Flow<List<Habit>>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun byId(id: Long): Habit?

    /** Полное удаление вместе с историей отметок (журнал XP не трогаем) */
    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun delete(id: Long)

    /** Сколько отметок привычки в интервале дат (включительно) — консистентность/диагностика */
    @Query("SELECT COUNT(*) FROM habit_checks WHERE habitId = :habitId AND epochDay BETWEEN :fromDay AND :toDay")
    suspend fun countChecksBetween(habitId: Long, fromDay: Long, toDay: Long): Int

    // --- T-16: бэкап v3 ---
    /** Все привычки (включая архивные) для экспорта */
    @Query("SELECT * FROM habits ORDER BY id")
    suspend fun all(): List<Habit>

    /** Восстановление из бэкапа: REPLACE с сохранёнными id */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(habits: List<Habit>)

    @Query("DELETE FROM habits")
    suspend fun clearAll()
}

@Dao
interface HabitCheckDao {
    @Insert
    suspend fun insert(check: HabitCheck): Long

    /** Весь журнал отметок — для стриков/консистентности экрана привычек */
    @Query("SELECT * FROM habit_checks ORDER BY epochDay")
    fun observeAll(): Flow<List<HabitCheck>>

    /** Отметка конкретной привычки за конкретный день (уникальна) */
    @Query("SELECT * FROM habit_checks WHERE habitId = :habitId AND epochDay = :epochDay LIMIT 1")
    suspend fun byDay(habitId: Long, epochDay: Long): HabitCheck?

    /** Инкремент значения количественной цели за день */
    @Query("UPDATE habit_checks SET value = :value WHERE id = :id")
    suspend fun updateValue(id: Long, value: Double?)

    /** Дубликат (habitId, epochDay) невозможен — unique index */
    @Query("SELECT * FROM habit_checks WHERE epochDay = :epochDay ORDER BY id")
    fun observeForDay(epochDay: Long): Flow<List<HabitCheck>>

    @Query("SELECT * FROM habit_checks WHERE habitId = :habitId AND epochDay BETWEEN :fromDay AND :toDay ORDER BY epochDay")
    fun observeRangeForHabit(habitId: Long, fromDay: Long, toDay: Long): Flow<List<HabitCheck>>

    /** Разовое чтение истории привычки для расчёта стрика (вехи T-07) */
    @Query("SELECT * FROM habit_checks WHERE habitId = :habitId AND epochDay BETWEEN :fromDay AND :toDay ORDER BY epochDay")
    suspend fun rangeForHabit(habitId: Long, fromDay: Long, toDay: Long): List<HabitCheck>

    @Query("SELECT COUNT(*) FROM habit_checks WHERE habitId = :habitId AND epochDay = :epochDay")
    suspend fun existsForDay(habitId: Long, epochDay: Long): Int

    @Query("DELETE FROM habit_checks WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Удаление всех отметок привычки (при полном удалении привычки) */
    @Query("DELETE FROM habit_checks WHERE habitId = :habitId")
    suspend fun deleteForHabit(habitId: Long)

    // --- T-16: бэкап v3 ---
    /** Весь журнал отметок для экспорта (порядок по id сохраняет парность с записями XP) */
    @Query("SELECT * FROM habit_checks ORDER BY id")
    suspend fun all(): List<HabitCheck>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(checks: List<HabitCheck>)

    @Query("DELETE FROM habit_checks")
    suspend fun clearAll()
}
