package com.questline.app.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.questline.app.ui.theme.Q

/**
 * N-01: карточка напоминаний в настройках. Время каждого напоминания
 * задаётся в редакторе привычки; тут — пояснение и переход к системным
 * настройкам уведомлений приложения.
 */
@Composable
internal fun ReminderSettingsSection() {
    val context = LocalContext.current
    SectionCard(title = "Напоминания о привычках") {
        Text(
            "Включи напоминание в редакторе привычки — приложение напомнит о ней " +
                "в выбранное время. Сразу в уведомлении можно отметить выполнение: " +
                "XP и серия дней начислятся, как обычная отметка.",
            style = MaterialTheme.typography.bodySmall,
            color = Q.inkMuted,
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { openAppNotificationSettings(context) },
            colors = ButtonDefaults.buttonColors(containerColor = Q.accent),
        ) {
            Text("Разрешить уведомления")
        }
    }
}

/** Системные настройки уведомлений приложения; при сбое — настройки системы. */
private fun openAppNotificationSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        } catch (_: Exception) {
            // мягкий fallback ниже
        }
    }
    try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: Exception) {
        // никуда не переходим — уведомления можно включить из ланчера системы
    }
}
