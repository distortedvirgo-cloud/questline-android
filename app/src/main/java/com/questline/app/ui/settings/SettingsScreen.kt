package com.questline.app.ui.settings

import android.app.Application
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.questline.app.BuildConfig
import com.questline.app.notify.BankPrefs
import com.questline.app.update.UpdateChecker
import com.questline.app.update.UpdateConfig
import com.questline.app.ui.theme.Q
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Настройки: доступ к уведомлениям банков + обновления с GitHub. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onOpenMirror: () -> Unit = {},
    onOpenShop: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = remember { CoroutineScope(Dispatchers.Main) }

    var bankEnabled by remember { mutableStateOf(BankPrefs.isEnabled(context)) }
    var listenerGranted by remember {
        mutableStateOf(hasNotificationListenerAccess(context))
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* результат не критичен: без SMS работает канал пушей */ }

    // Апдейтер
    var updateStatus by remember { mutableStateOf("Проверить обновления") }
    var updateBusy by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableStateOf(-1) }
    var releaseNotes by remember { mutableStateOf<String?>(null) }
    var pendingRelease by remember {
        mutableStateOf<com.questline.app.update.ReleaseInfo?>(null)
    }
    var errorText by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Q.bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        com.questline.app.ui.settings.AiSettingsSection()
        Spacer(Modifier.height(12.dp))
        com.questline.app.ui.settings.BackupSection()
        Spacer(Modifier.height(12.dp))

        SectionCard(title = "Ещё") {
            TextButton(onClick = onOpenMirror) { Text("🪞 Зеркало недели") }
            TextButton(onClick = onOpenShop) { Text("🎨 Магазин тем") }
        }

        Spacer(Modifier.height(12.dp))

        // --- Банковские пуши и SMS ---
        SectionCard(title = "Автоучёт расходов") {
            Text(
                "Приложение читает уведомления и SMS только от банков — конкретно Сбербанк (пуш-уведомления приложения) и номер 900. Прочие SMS и уведомления не открываются. Операции попадают во вкладку «Деньги», каждую подтверждаешь вручную. Данные не покидают телефон.",
                style = MaterialTheme.typography.bodySmall,
                color = Q.inkMuted,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Разбирать пуши и SMS Сбера", modifier = Modifier.weight(1f))
                Switch(
                    checked = bankEnabled,
                    onCheckedChange = { checked ->
                        if (checked && !listenerGranted) {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        } else {
                            BankPrefs.setEnabled(context, checked)
                            bankEnabled = checked
                            if (checked) {
                                smsPermissionLauncher.launch(
                                    arrayOf(
                                        android.Manifest.permission.RECEIVE_SMS,
                                        android.Manifest.permission.READ_SMS,
                                    ),
                                )
                            }
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Q.accent,
                        uncheckedTrackColor = Q.surfaceAlt,
                    ),
                )
            }
            if (!listenerGranted) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Нужен доступ к уведомлениям: включите Questline в системных настройках.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Q.warn,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // --- Обновления ---
        SectionCard(title = "Обновление") {
            Text(
                "Текущая версия ${BuildConfig.VERSION_NAME}. Обновления приходят из GitHub Releases.",
                style = MaterialTheme.typography.bodySmall,
                color = Q.inkMuted,
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    if (updateBusy) return@Button
                    updateBusy = true
                    errorText = null
                    scope.launch {
                        try {
                            val release = UpdateChecker.checkLatest(UpdateConfig.REPO)
                            if (release == null || !UpdateChecker.isNewerVersion(release.version, BuildConfig.VERSION_NAME)) {
                                updateStatus = "У вас последняя версия"
                            } else if (release.apkUrl.isEmpty()) {
                                updateStatus = "В релизе v${release.version} нет APK"
                            } else {
                                pendingRelease = release
                            }
                        } catch (e: Exception) {
                            errorText = "Ошибка сети: ${e.message}"
                        } finally {
                            updateBusy = false
                        }
                    }
                },
                enabled = !updateBusy,
                colors = ButtonDefaults.buttonColors(containerColor = Q.accent),
            ) {
                Text(if (updateBusy) "Работаю…" else updateStatus)
            }
            if (updateProgress >= 0 && updateProgress < 100) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { updateProgress / 100f },
                    color = Q.accent,
                    trackColor = Q.surfaceAlt,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            errorText?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = Q.danger, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack) { Text("← Назад") }
    }

    // Диалог нового релиза
    pendingRelease?.let { release ->
        AlertDialog(
            onDismissRequest = { pendingRelease = null },
            title = { Text("Доступна версия ${release.version}") },
            text = {
                Column {
                    if (release.notes.isNotBlank()) {
                        Text(
                            release.notes.take(500),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (updateProgress >= 0 && updateProgress < 100) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { updateProgress / 100f },
                            color = Q.accent,
                            trackColor = Q.surfaceAlt,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = updateProgress in -1..0 || updateProgress >= 100,
                    onClick = {
                        updateProgress = 0
                        scope.launch {
                            try {
                                val file = UpdateChecker.downloadApk(context, release.apkUrl) { pct ->
                                    updateProgress = pct
                                }
                                updateProgress = 100
                                if (UpdateChecker.canInstall(context)) {
                                    UpdateChecker.installApk(context, file)
                                } else {
                                    UpdateChecker.openInstallUnknownAppsSettings(context)
                                }
                            } catch (e: Exception) {
                                errorText = "Не удалось скачать: ${e.message}"
                                updateProgress = -1
                            } finally {
                                pendingRelease = null
                            }
                        }
                    },
                ) { Text("Скачать и установить") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRelease = null }) { Text("Позже") }
            },
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Q.surface, RoundedCornerShape(16.dp))
            .border(1.dp, Q.border, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

private fun hasNotificationListenerAccess(context: Context): Boolean =
    androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context)
        .contains(context.packageName)
