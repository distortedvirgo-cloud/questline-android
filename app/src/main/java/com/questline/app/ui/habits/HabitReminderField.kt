package com.questline.app.ui.habits

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.questline.app.ui.theme.Q

/** Минута дня по умолчанию при первом включении напоминания — 09:00 */
private const val DEFAULT_REMINDER_MIN = 9 * 60

/**
 * Поле «Напоминание» (N-01): переключатель + время. При первом включении
 * на SDK 33+ запрашивается POST_NOTIFICATIONS; отказ не блокирует сохранение —
 * напоминание просто не покажется, пока уведомления запрещены.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderField(
    minOfDay: Int?,
    onMinOfDay: (Int?) -> Unit,
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* отказ не блокирует: напоминание просто не сработает */ }

    var showPicker by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text = "Напоминание", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (minOfDay != null) {
                    "Каждый день в ${formatReminderTime(minOfDay)}"
                } else {
                    "Уведомление в выбранное время"
                },
                style = MaterialTheme.typography.labelSmall,
                color = Q.inkMuted,
            )
        }
        Switch(
            checked = minOfDay != null,
            onCheckedChange = { enabled ->
                if (enabled) {
                    onMinOfDay(minOfDay ?: DEFAULT_REMINDER_MIN)
                    requestNotifPermission(context) { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                } else {
                    onMinOfDay(null)
                }
            },
        )
    }
    if (minOfDay != null) {
        TextButton(onClick = { showPicker = true }) {
            Text(text = "Время: ${formatReminderTime(minOfDay)}")
        }
    }

    if (showPicker && minOfDay != null) {
        val state = rememberTimePickerState(
            initialHour = minOfDay / 60,
            initialMinute = minOfDay % 60,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Время напоминания") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    onMinOfDay(state.hour * 60 + state.minute)
                    showPicker = false
                }) { Text("Готово") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Отмена") }
            },
        )
    }
}

private fun requestNotifPermission(context: android.content.Context, request: () -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val granted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
    if (!granted) request()
}

/** 9*60+5 → «09:05» */
internal fun formatReminderTime(minOfDay: Int): String =
    "%02d:%02d".format(minOfDay / 60, minOfDay % 60)
