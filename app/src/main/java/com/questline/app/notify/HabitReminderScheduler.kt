package com.questline.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.questline.app.data.AppRepo
import com.questline.app.data.habits.Habit

/**
 * N-01: планирование напоминаний о привычках. AlarmManager.setAndAllowWhileIdle
 * (inexact — без SCHEDULE_EXACT_ALARM). На каждую активную привычку с
 * reminderMinOfDay != null — один аларм на ближайшее время триггера (сегодня
 * или завтра, если время уже прошло). requestCode = habitId, поэтому повторный
 * set перезаписывает прежний аларм той же привычки.
 */
object HabitReminderScheduler {

    /** Запланировать (или перезапланировать) аларм одной привычки. */
    fun schedule(context: Context, habit: Habit) {
        val minOfDay = habit.reminderMinOfDay ?: run {
            cancel(context, habit.id)
            return
        }
        val at = nextTriggerMillis(minOfDay, System.currentTimeMillis())
        val pi = alarmPendingIntent(context, habit.id)
        alarmManager(context).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    /** Снять аларм привычки (reminder выключен / архив / удаление). */
    fun cancel(context: Context, habitId: Long) {
        alarmManager(context).cancel(alarmPendingIntent(context, habitId))
    }

    /** Перепланировать все активные привычки с включённым напоминанием. */
    suspend fun rescheduleAll(context: Context) {
        val repo = AppRepo.get(context)
        val active = repo.habits.activeOnce()
        active.filter { it.reminderMinOfDay != null }.forEach { schedule(context, it) }
    }

    private fun alarmManager(context: Context): AlarmManager =
        context.applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun alarmPendingIntent(context: Context, habitId: Long): PendingIntent {
        val intent = Intent(context, HabitAlarmReceiver::class.java)
            .setAction(HabitAlarmReceiver.ACTION_FIRE)
            .putExtra(HabitAlarmReceiver.EXTRA_HABIT_ID, habitId)
        return PendingIntent.getBroadcast(
            context,
            habitId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Чистая логика следующего триггера (unit-тестируется без Android):
     * UTC-миллис «сегодня/завтра» в указанную минуту дня по локали.
     * Если время сегодня уже прошло (или ровно сейчас) — берём завтра.
     */
    fun nextTriggerMillis(minOfDay: Int, nowMillis: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Long {
        val now = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone)
        val todayAt = now.toLocalDate().atStartOfDay(zone).plusSeconds(minOfDay * 60L)
        val trigger = if (todayAt.toInstant().toEpochMilli() > nowMillis) {
            todayAt
        } else {
            todayAt.plusSeconds(86_400L)
        }
        return trigger.toInstant().toEpochMilli()
    }
}
