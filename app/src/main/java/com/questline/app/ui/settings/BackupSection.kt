package com.questline.app.ui.settings

import android.content.Context
import android.net.Uri
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.questline.app.data.AppRepo
import com.questline.app.data.Backup
import com.questline.app.ui.theme.Q
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Бэкап: экспорт всех таблиц в JSON через SAF, импорт с подтверждением
 * перезаписи. Данные не покидают телефон — файл выбирает пользователь.
 */
@Composable
fun BackupSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            Backup.exportTo(AppRepo.get(context), out)
                        } ?: throw RuntimeException("Не удалось открыть файл")
                    }
                    message = "Экспорт готов ✓"
                } catch (e: Exception) {
                    message = "Ошибка экспорта: ${e.message?.take(80)}"
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) pendingRestoreUri = uri
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Q.surface, RoundedCornerShape(16.dp))
            .border(1.dp, Q.border, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text("Данные и бэкап", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Все данные хранятся только на телефоне. Сохраняйте копию перед сменой устройства.",
            style = MaterialTheme.typography.bodySmall,
            color = Q.inkMuted,
        )
        Spacer(Modifier.height(8.dp))
        Row {
            TextButton(onClick = { exportLauncher.launch("questline-backup.json") }) {
                Text("Экспорт в файл")
            }
            TextButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                Text("Импорт", color = Q.danger)
            }
        }
        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Q.inkMuted)
        }
    }

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("Восстановить из файла?") },
            text = { Text("Все текущие данные будут заменены содержимым файла. Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestoreUri = null
                    scope.launch {
                        try {
                            val count = withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    Backup.restore(AppRepo.get(context), context, input)
                                } ?: throw RuntimeException("Не удалось открыть файл")
                            }
                            message = "Восстановлено записей: $count"
                        } catch (e: Exception) {
                            message = "Ошибка импорта: ${e.message?.take(80)}"
                        }
                    }
                }) { Text("Заменить", color = Q.danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) { Text("Отмена") }
            },
        )
    }
}
