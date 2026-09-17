package com.questline.app.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.questline.app.MainActivity
import com.questline.app.R
import com.questline.app.data.AppRepo
import com.questline.app.data.habits.Habit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** N-01: канал напоминаний о привычках */
const val CHANNEL_HABITS = "habits"

/**
 * N-01: приёмник аларма напоминания. Если привычка ещё не отмечена сегодня —
 * уведомление с действиями «Отметить ✓» и «Пропустить»; тап по телу открывает
 * приложение. После срабатывания сразу планирует следующий триггер (завтра),
 * чтобы серия алармов не истощалась.
 */
class HabitAlarmReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context?, intent: Intent?) {
        val appContext = context?.applicationContext ?: return
        if (intent?.action != ACTION_FIRE) return
        val habitId = intent.getLongExtra(EXTRA_HABIT_ID, -1L)
        if (habitId <= 0L) return

        val pending = goAsync()
        scope.launch {
            try {
                handle(appContext, habitId)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, habitId: Long) {
        val repo = AppRepo.get(context)
        val habit = repo.habits.byId(habitId) ?: return
        if (habit.reminderMinOfDay == null || habit.archivedAt != null) return

        ensureChannel(context)
        val today = AppRepo.todayEpochDay
        if (repo.habitChecks.byDay(habitId, today) == null) {
            notify(context, habit)
        }
        // Следующее напоминание этой же привычки — на завтра.
        HabitReminderScheduler.schedule(context, habit)
    }

    private fun notify(context: Context, habit: Habit) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val openApp = PendingIntent.getActivity(
            context,
            habit.id.toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val mark = PendingIntent.getBroadcast(
            context,
            habit.id.toInt(),
            Intent(context, HabitMarkReceiver::class.java)
                .setAction(HabitMarkReceiver.ACTION_MARK)
                .putExtra(HabitMarkReceiver.EXTRA_HABIT_ID, habit.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismiss = PendingIntent.getBroadcast(
            context,
            habit.id.toInt(),
            Intent(context, HabitMarkReceiver::class.java)
                .setAction(HabitMarkReceiver.ACTION_DISMISS)
                .putExtra(HabitMarkReceiver.EXTRA_HABIT_ID, habit.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = "⏰ ${habit.emoji} ${habit.title}".trim()
        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_HABITS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText("Время для привычки — отметь выполнение")
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(0, "Отметить ✓", mark)
            .addAction(0, "Пропустить", dismiss)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(habit.id.toInt(), notification)
        }
    }

    companion object {
        const val ACTION_FIRE = "com.questline.app.HABIT_REMINDER_FIRE"
        const val EXTRA_HABIT_ID = "habitId"

        /** Канал «Напоминания о привычках» — создаётся лениво перед показом. */
        fun ensureChannel(context: Context) {
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_HABITS) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_HABITS,
                    "Напоминания о привычках",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
    }
}
