package com.questline.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.questline.app.data.habits.Habit
import com.questline.app.data.habits.HabitCheck
import com.questline.app.data.habits.HabitCheckDao
import com.questline.app.data.habits.HabitDao
import com.questline.app.data.xp.XpLedger
import com.questline.app.data.xp.XpLedgerDao

@Database(
    entities = [
        Category::class, Task::class, Quest::class,
        Txn::class, PendingTxn::class, Goal::class, CoinsLedger::class,
        Habit::class, HabitCheck::class, XpLedger::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class QuestlineDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun taskDao(): TaskDao
    abstract fun questDao(): QuestDao
    abstract fun txnDao(): TxnDao
    abstract fun pendingTxnDao(): PendingTxnDao
    abstract fun goalDao(): GoalDao
    abstract fun coinsLedgerDao(): CoinsLedgerDao
    abstract fun habitDao(): HabitDao
    abstract fun habitCheckDao(): HabitCheckDao
    abstract fun xpLedgerDao(): XpLedgerDao

    companion object {
        @Volatile private var instance: QuestlineDatabase? = null

        /** v1 → v2: интервал повтора задачи; старый флаг repeatDaily = 1 день */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN repeatIntervalDays INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE tasks SET repeatIntervalDays = 1 WHERE repeatDaily = 1")
            }
        }

        /** v2 → v3: атрибуция транзакции карте-источнику («••5129» из AccountsPrefs) */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN accountLast4 TEXT")
            }
        }

        /**
         * v3 → v4: привычки + отметки + журнал XP (аддитивно, SPEC v3 «Данные»).
         * SQL повторяет то, что Room генерирует по сущностям (имена колонок = полям).
         * Backfill: по одной записи XpLedger на закрытый квест v2 — итог XP не меняется.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `habits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, `emoji` TEXT NOT NULL, `colorIndex` INTEGER NOT NULL, " +
                        "`characteristic` TEXT NOT NULL, `scheduleType` TEXT NOT NULL, " +
                        "`weekdaysMask` INTEGER NOT NULL, `timesPerWeek` INTEGER NOT NULL, " +
                        "`intervalDays` INTEGER NOT NULL, `targetValue` REAL, `unit` TEXT, " +
                        "`complexity` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `archivedAt` INTEGER)",
                )
                // frozen без SQL DEFAULT: значение по умолчанию задаёт Kotlin-поле,
                // иначе Room-валидация миграции ловит расхождение с ожидаемой схемой.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `habit_checks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`habitId` INTEGER NOT NULL, `epochDay` INTEGER NOT NULL, `value` REAL, " +
                        "`createdAtMillis` INTEGER NOT NULL, `frozen` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_habit_checks_habitId_epochDay` " +
                        "ON `habit_checks` (`habitId`, `epochDay`)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `xp_ledger` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`delta` INTEGER NOT NULL, `source` TEXT NOT NULL, `refId` INTEGER, " +
                        "`epochDay` INTEGER NOT NULL, `createdAtMillis` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_xp_ledger_epochDay` ON `xp_ledger` (`epochDay`)",
                )
                // В quests нет dateClosed — есть closedAtMillis (epochMillis);
                // epochDay = millis / millisPerDay (UTC-округление, сумма XP не зависит от зоны).
                db.execSQL(
                    "INSERT INTO xp_ledger (delta, source, refId, epochDay, createdAtMillis) " +
                        "SELECT xpReward, 'QUEST', id, closedAtMillis / 86400000, closedAtMillis " +
                        "FROM quests WHERE status = 'DONE' AND closedAtMillis IS NOT NULL",
                )
            }
        }

        fun get(context: Context): QuestlineDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    QuestlineDatabase::class.java,
                    "questline.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
    }
}
