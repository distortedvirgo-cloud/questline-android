package com.questline.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Category::class, Task::class, Quest::class,
        Txn::class, PendingTxn::class, Goal::class, CoinsLedger::class,
    ],
    version = 3,
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

        fun get(context: Context): QuestlineDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    QuestlineDatabase::class.java,
                    "questline.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
