package com.questline.app.data.xp

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface XpLedgerDao {
    @Insert
    suspend fun insert(entry: XpLedger): Long

    /** Весь XP игрока — уровень считается on-demand */
    @Query("SELECT COALESCE(SUM(delta),0) FROM xp_ledger")
    fun observeSumTotal(): Flow<Int>

    /** XP с заданного дня (включительно) — «Прогресс дня», недельные сводки */
    @Query("SELECT COALESCE(SUM(delta),0) FROM xp_ledger WHERE epochDay >= :fromDay")
    fun observeSumSince(fromDay: Long): Flow<Int>

    /** XP в интервале дат (включительно) — зеркало недели/статистика */
    @Query("SELECT COALESCE(SUM(delta),0) FROM xp_ledger WHERE epochDay BETWEEN :fromDay AND :toDay")
    suspend fun sumBetween(fromDay: Long, toDay: Long): Int

    /** Сумма за день по одному источнику — дневной кап XP привычек (T-04) */
    @Query("SELECT COALESCE(SUM(delta),0) FROM xp_ledger WHERE epochDay = :epochDay AND source = :source")
    suspend fun sumBySource(epochDay: Long, source: String): Int

    /** Убрать запись источника по refId — снятие отметки привычки */
    @Query("DELETE FROM xp_ledger WHERE source = :source AND refId = :refId")
    suspend fun deleteByRef(source: String, refId: Long)

    /** Есть ли запись источника по refId — XP начисляется один раз на отметку */
    @Query("SELECT COUNT(*) FROM xp_ledger WHERE source = :source AND refId = :refId")
    suspend fun countByRef(source: String, refId: Long): Int
}
