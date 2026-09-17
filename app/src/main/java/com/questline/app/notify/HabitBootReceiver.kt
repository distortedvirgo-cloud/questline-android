package com.questline.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * N-01: после перезагрузки устройства алармы AlarmManager сбрасываются —
 * перепланируем напоминания всех активных привычек.
 */
class HabitBootReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val appContext = context?.applicationContext ?: return

        val pending = goAsync()
        scope.launch {
            try {
                HabitReminderScheduler.rescheduleAll(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}
