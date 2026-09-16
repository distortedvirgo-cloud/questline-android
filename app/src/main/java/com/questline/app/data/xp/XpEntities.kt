package com.questline.app.data.xp

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/* ============================================================
 * Questline v3 — журнал XP. Схема v4, аддитивно к Entities.kt.
 * Уровень считается on-demand суммой по журналу (SPEC v3).
 * ============================================================ */

// ---------------- Журнал XP ----------------

/** Единый журнал начислений XP. Уровень = 120 + 30*N поверх суммы delta. */
@Serializable
@Entity(tableName = "xp_ledger", indices = [Index("epochDay")])
data class XpLedger(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val delta: Int,
    /** TASK | HABIT | BUDGET | GOAL | MILESTONE | QUEST */
    val source: String,
    /** Ссылка на запись-источник (id задачи/привычки/цели...); null = без привязки */
    val refId: Long? = null,
    val epochDay: Long,
    val createdAtMillis: Long,
)
