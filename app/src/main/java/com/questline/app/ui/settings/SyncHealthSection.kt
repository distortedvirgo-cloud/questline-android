package com.questline.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.questline.app.ui.theme.Q

/**
 * Самодиагностика фоновых каналов автучёта: слушатель уведомлений,
 * SMS-доступ, оптимизация батареи. Помогает найти причину, если
 * операции перестали приходить (особенно на агрессивных прошивках).
 */
@Composable
fun SyncHealthSection() {
    val context = LocalContext.current
    var refresh by remember { mutableStateOf(0) }

    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refresh++ }

    var listenerOk by remember(refresh) { mutableStateOf(false) }
    var smsOk by remember(refresh) { mutableStateOf(false) }
    var batteryOk by remember(refresh) { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(refresh) {
        listenerOk = hasNotificationListenerAccess(context)
        smsOk = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.READ_SMS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        batteryOk = pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Q.surface, RoundedCornerShape(16.dp))
            .border(1.dp, Q.border, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Фоновые каналы", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { refresh++ }) { Text("Обновить") }
        }
        HealthRow(
            title = "Слушатель уведомлений (пуши банков)",
            ok = listenerOk,
            fixLabel = "Открыть",
            onFix = {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )
        HealthRow(
            title = "SMS от банков (номер 900)",
            ok = smsOk,
            fixLabel = "Разрешить",
            onFix = {
                smsLauncher.launch(arrayOf(android.Manifest.permission.RECEIVE_SMS, android.Manifest.permission.READ_SMS))
            },
        )
        HealthRow(
            title = "Не убивать в фоне (батарея)",
            ok = batteryOk,
            fixLabel = "Разрешить",
            onFix = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )
        if (android.os.Build.MANUFACTURER.contains("samsung", ignoreCase = true)) {
            TextButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }) { Text("Samsung: открыть страницу приложения") }
            Text(
                "На Samsung дополнительно проверь: страница приложения → Батарея → «Без ограничений»; Настройки → Батарея → Ограничения в фоне — отключи для Questline; «Переводить в сон» — исключи приложение.",
                style = MaterialTheme.typography.labelSmall,
                color = Q.inkMuted,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Достаточно включить те каналы, которыми пользуешься. Приложение не висит в памяти — система будит его сама при SMS/уведомлении. Режим энергосбережения Samsung может глушить фоновые приложения даже с галочкой выше — тогда отключи энергосбережение или добавь Questline в исключения.",
            style = MaterialTheme.typography.labelSmall,
            color = Q.inkMuted,
        )
    }
}

@Composable
private fun HealthRow(title: String, ok: Boolean, fixLabel: String, onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            if (ok) "✓" else "✗",
            color = if (ok) Q.success else Q.danger,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(end = 10.dp),
        )
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (!ok) {
            TextButton(onClick = onFix) { Text(fixLabel) }
        }
    }
}
