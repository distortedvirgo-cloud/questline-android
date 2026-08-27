package com.questline.app.ui.today

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import com.questline.app.ai.AiFeatures
import com.questline.app.ai.AiPrefs
import com.questline.app.data.AppRepo
import com.questline.app.ui.theme.Q
import kotlinx.coroutines.launch

/**
 * Кнопка «AI-квест дня»: видна только если задан API-ключ в Настройках.
 * Лимит один квест в день — проверяется в AiFeatures.
 */
@Composable
fun AiQuestButton(repo: AppRepo, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    if (!AiPrefs.isConfigured(context)) return

    Row(modifier = modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
            enabled = !busy,
            onClick = {
                busy = true
                status = null
                scope.launch {
                    try {
                        val quest = AiFeatures.generateDailyQuest(context, repo, AppRepo.todayEpochDay)
                        status = "Добавлен: ${quest.title}"
                    } catch (e: Exception) {
                        status = if (e.message?.contains("уже создан") == true) {
                            "AI-квест на сегодня уже есть"
                        } else {
                            "Ошибка: ${e.message?.take(80)}"
                        }
                    } finally {
                        busy = false
                    }
                }
            },
        ) {
            Text(if (busy) "Придумываю…" else "✨ AI-квест дня")
        }
    }
    status?.let {
        Text(it, style = MaterialTheme.typography.labelSmall, color = Q.inkMuted, modifier = Modifier.padding(horizontal = 8.dp))
    }
}
