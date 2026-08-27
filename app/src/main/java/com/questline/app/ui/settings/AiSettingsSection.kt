package com.questline.app.ui.settings

import android.content.Context
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.questline.app.ai.AiPrefs
import com.questline.app.ui.theme.Q

/**
 * Секция настроек AI (GLM через OpenAI-совместимый API).
 * Без ключа все AI-фичи просто скрыты в интерфейсе.
 */
@Composable
fun AiSettingsSection() {
    val context = LocalContext.current
    var baseUrl by remember { mutableStateOf(AiPrefs.baseUrl(context)) }
    var apiKey by remember { mutableStateOf(AiPrefs.apiKey(context)) }
    var model by remember { mutableStateOf(AiPrefs.model(context)) }
    var saved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Q.surface, RoundedCornerShape(16.dp))
            .border(1.dp, Q.border, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text("AI-коуч (необязательно)", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Ключ хранится только на телефоне. Без ключа приложение полностью работает офлайн. По умолчанию — Z.ai (GLM); подойдёт любой OpenAI-совместимый API.",
            style = MaterialTheme.typography.bodySmall,
            color = Q.inkMuted,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it; saved = false },
            label = { Text("API-ключ") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = model,
            onValueChange = { model = it; saved = false },
            label = { Text("Модель") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it; saved = false },
            label = { Text("Базовый URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Row {
            TextButton(
                onClick = {
                    AiPrefs.save(context, baseUrl, apiKey, model)
                    saved = true
                },
            ) { Text("Сохранить") }
            TextButton(onClick = {
                AiPrefs.save(context, AiPrefs.DEFAULT_BASE_URL, "", AiPrefs.DEFAULT_MODEL)
                apiKey = ""
                baseUrl = AiPrefs.DEFAULT_BASE_URL
                model = AiPrefs.DEFAULT_MODEL
                saved = false
            }) { Text("Сбросить", color = Q.inkMuted) }
            if (saved) {
                TextButton(onClick = {}, enabled = false) { Text("Сохранено ✓", color = Q.success) }
            }
        }
    }
}
