package com.questline.app.ui.onboarding

import android.content.Context
import com.questline.app.data.AppRepo
import kotlinx.coroutines.flow.first

/**
 * N-03: решение «показывать ли онбординг v3». Онбординг — только для новой
 * установки: флаг `onboarding_v3_done` отсутствует И БД пуста (0 привычек,
 * 0 записей xp_ledger, 0 транзакций, 0 задач). Апгрейд с данными (v2.8/v3.0)
 * флаг не имеет, но БД непуста — флаг ставится сразу, экран не показывается.
 */
object OnboardingGate {
    const val PREFS_FILE = "onboarding_prefs"
    const val KEY_DONE = "onboarding_v3_done"

    /** Чистая функция решения; покрыта unit-тестом OnboardingGateTest. */
    fun shouldShow(done: Boolean, dbEmpty: Boolean): Boolean = !done && dbEmpty

    fun isDone(context: Context): Boolean =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_DONE, false)

    fun markDone(context: Context) {
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DONE, true).apply()
    }

    /**
     * Пуста ли БД с точки зрения онбординга — по существующим DAO без новых
     * счётчиков: привычки (включая архивные), сумма xp_ledger, транзакции
     * (netSpent по всему диапазону возвращает null только на пустой таблице),
     * задачи. Проверка однократная, при старте.
     */
    suspend fun dbEmpty(repo: AppRepo): Boolean =
        repo.habits.all().isEmpty() &&
            repo.xpLedger.observeSumTotal().first() == 0 &&
            repo.txns.netSpent(0L, Long.MAX_VALUE) == null &&
            repo.tasks.all().isEmpty()

    /** Решение при запуске: апгрейд с данными закрывается флагом сразу. */
    suspend fun resolve(context: Context, repo: AppRepo): Boolean {
        val done = isDone(context)
        val empty = dbEmpty(repo)
        if (!empty) markDone(context)
        return shouldShow(done, empty)
    }
}
