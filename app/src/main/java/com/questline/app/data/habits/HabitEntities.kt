package com.questline.app.data.habits

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/* ============================================================
 * Questline v3 — привычки. Схема v4, аддитивно к Entities.kt.
 * Даты: Long epochDay, время: Long epochMillis.
 * Конвертеры не используются — все поля примитивы/String,
 * характеристики/расписания — строковые коды как в Entities.kt.
 * ============================================================ */

// ---------------- Привычки ----------------

@Serializable
@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val emoji: String = "",
    val colorIndex: Int = 0,
    /** PHYSICS | MIND | MONEY | SOCIAL | DISCIPLINE (как questKey в categories) */
    val characteristic: String,
    /** DAILY | WEEKDAYS | TIMES_PER_WEEK | INTERVAL */
    val scheduleType: String,
    /** Битовая маска дней недели: бит 0 = ПН ... бит 6 = ВС (при WEEKDAYS) */
    val weekdaysMask: Int = 0,
    /** Целевое число отметок в неделю (при TIMES_PER_WEEK) */
    val timesPerWeek: Int = 0,
    /** Повтор каждые N дней (при INTERVAL) */
    val intervalDays: Int = 0,
    /** Количественная цель (например 30 мин, 2000 шагов); null = обычная чек-привычка */
    val targetValue: Double? = null,
    /** Единица количественной цели: «мин», «стр», «стаканов» */
    val unit: String? = null,
    val complexity: String = "M",       // S | M | L (влияет на XP: 5/8/10)
    /** День создания (epochDay) */
    val createdAt: Long,
    /** День архивации; null = жива */
    val archivedAt: Long? = null,
    /** v3.1 (N-01): минута дня напоминания (0..1439); null = напоминания нет */
    val reminderMinOfDay: Int? = null,
)

// ---------------- Отметки привычек ----------------

/** Отметка за день: уникальна на пару habitId+день — двойная отметка невозможна на уровне БД. */
@Serializable
@Entity(
    tableName = "habit_checks",
    indices = [Index(value = ["habitId", "epochDay"], unique = true)]
)
data class HabitCheck(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val epochDay: Long,
    /** Фактическое значение для количественных привычек; null = обычная отметка */
    val value: Double? = null,
    val createdAtMillis: Long,
    /** День перекрыт заморозкой: стрик не рвёт и не удлиняет, в консистентности = «удержано» */
    val frozen: Boolean = false,
)
