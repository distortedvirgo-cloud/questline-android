package com.questline.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Category::class, Task::class, Quest::class,
        Txn::class, PendingTxn::class, Goal::class, CoinsLedger::class,
    ],
    version = 1,
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

        fun get(context: Context): QuestlineDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    QuestlineDatabase::class.java,
                    "questline.db",
                ).build().also { instance = it }
            }
    }
}
