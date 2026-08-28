package com.questline.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/* ============================================================
 * Questline — схема данных v1.
 * Даты: Long epochDay. Время: Long epochMillis.
 * Конвертеры не используются — все поля примитивы/String.
 * Деньги — minor units (копейки), Long.
 * ============================================================ */

// ---------------- Категории ----------------

/** kind = "QUEST" (характеристика) | "FINANCE" (статья расходов/доходов) */
@Serializable
@Entity(
    tableName = "categories",
    indices = [Index(value = ["name", "kind"], unique = true)]
)
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String,               // QUEST | FINANCE
    val questKey: String? = null,   // для QUEST: PHYSICS/MIND/MONEY/SOCIAL/DISCIPLINE
    val budgetMonthlyMinor: Long? = null,
    val colorIndex: Int = 0,
    val emoji: String = "",
    val isIncome: Boolean = false,
)

// ---------------- Задачи ----------------

@Serializable
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    val categoryId: Long? = null,       // QUEST-категория
    /** Легаси-флаг: для новых записей инвариант repeatDaily == (repeatIntervalDays >= 1) */
    val repeatDaily: Boolean = false,
    /** 0 = не повторяется, 1 = ежедневно, N = каждые N дней */
    val repeatIntervalDays: Int = 0,
    val dueEpochDay: Long? = null,
    val complexity: String = "M",       // S | M | L (босс)
    val done: Boolean = false,
    val doneAtMillis: Long? = null,
    val createdAtMillis: Long,
    /** Последний день закрытия для повторяющихся задач */
    val lastDoneEpochDay: Long? = null,
)

// ---------------- Квесты ----------------

@Serializable
@Entity(tableName = "quests", indices = [Index("dateCreatedEpochDay")])
data class Quest(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long? = null,
    /** AUTO (генератор) | USER (из задачи) | BUDGET (бюджетный вызов) */
    val source: String,
    val templateId: String? = null,
    val title: String,
    val questKey: String,               // PHYSICS|MIND|MONEY|SOCIAL|DISCIPLINE
    val complexity: String,             // S|M|L
    val xpReward: Int,
    val coinReward: Int,
    /** OPEN | DONE | EXPIRED */
    val status: String,
    val dateCreatedEpochDay: Long,
    val closedAtMillis: Long? = null,
    /** Для BUDGET-квестов: ключ периода "YYYY-MM" + id категории */
    val budgetPeriodKey: String? = null,
    val budgetCategoryId: Long? = null,
)

// ---------------- Транзакции ----------------

@Serializable
@Entity(tableName = "transactions", indices = [Index("epochDay")])
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMinor: Long,
    /** EXPENSE | INCOME */
    val type: String,
    val categoryId: Long,
    val epochDay: Long,
    val note: String = "",
    /** MANUAL | BANK_PUSH */
    val source: String = "MANUAL",
    /** Связь с записью пуш-инбокса */
    val pendingId: Long? = null,
    /** ••NNNN карты из AccountsPrefs; null = не привязана */
    val accountLast4: String? = null,
    val isPlanned: Boolean = false,
    val createdAtMillis: Long,
)

// ---------------- Пуш-инбокс банковских уведомлений ----------------

@Serializable
@Entity(tableName = "pending_txn", indices = [Index("status")])
data class PendingTxn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bankPackage: String,
    val title: String,
    val text: String,
    val amountMinor: Long,
    /** EXPENSE | INCOME */
    val type: String,
    val epochDay: Long,
    val receivedMillis: Long,
    /** PENDING | CONFIRMED | DISCARDED */
    val status: String = "PENDING",
)

// ---------------- Копилки ----------------

@Serializable
@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetMinor: Long,
    var savedMinor: Long = 0,
    /** ACTIVE | DONE | ARCHIVED */
    val status: String = "ACTIVE",
)

// ---------------- Личная валюта ----------------

@Serializable
@Entity(tableName = "coins_ledger")
data class CoinsLedger(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val delta: Int,
    /** QUEST_DONE | BUDGET_OK | GOAL_DEPOSIT | SHOP_PURCHASE | ... */
    val reason: String,
    val refId: Long? = null,
    val createdAtMillis: Long,
)
