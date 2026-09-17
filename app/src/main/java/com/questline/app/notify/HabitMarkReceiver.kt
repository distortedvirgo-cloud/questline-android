package com.questline.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.questline.app.data.AppRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * N-01: действие из уведомления-напоминания. ACTION_MARK — отметить привычку
 * за сегодня через [AppRepo.checkHabit] (XP/стрик/вехи — как обычная отметка);
 * ACTION_DISMISS — просто закрыть. В обоих случаях уведомление снимается.
 */
class HabitMarkReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context?, intent: Intent?) {
        val appContext = context?.applicationContext ?: return
        val action = intent?.action ?: return
        val habitId = intent.getLongExtra(EXTRA_HABIT_ID, -1L)
        if (habitId <= 0L) return

        val pending = goAsync()
        scope.launch {
            try {
                val repo = AppRepo.get(appContext)
                val habit = repo.habits.byId(habitId)
                if (habit != null && action == ACTION_MARK) {
                    val today = AppRepo.todayEpochDay
                    // Уже отмечено — ничего не начисляем, просто убираем уведомление.
                    if (repo.habitChecks.byDay(habitId, today) == null) {
                        repo.checkHabit(habit, today)
                    }
                }
                NotificationManagerCompat.from(appContext).cancel(habitId.toInt())
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_MARK = "com.questline.app.HABIT_REMINDER_MARK"
        const val ACTION_DISMISS = "com.questline.app.HABIT_REMINDER_DISMISS"
        const val EXTRA_HABIT_ID = "habitId"
    }
}
