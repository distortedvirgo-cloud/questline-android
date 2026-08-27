package com.questline.app.ui.assistant

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.questline.app.ai.AiAssistant
import com.questline.app.ai.AiClient
import com.questline.app.ai.AiPrefs
import com.questline.app.data.AppRepo
import com.questline.app.data.Task
import com.questline.app.data.Txn
import com.questline.app.ui.theme.Q
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Глобальный AI-ассистент: чат с контекстом приложения и применяемые действия. */
@Composable
fun AssistantScreen() {
    val context = LocalContext.current
    val vm: AssistantViewModel = viewModel(key = "assistant", factory = assistantVmFactory(context.applicationContext))

    if (!AiPrefs.isConfigured(context)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("🤖", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "Ассистент работает через AI-модель GLM.\nДобавь API-ключ в Настройках —\nи он будет знать все твои задачи,\nквесты и бюджеты.",
                style = MaterialTheme.typography.bodyLarge,
                color = Q.inkMuted,
            )
        }
        return
    }

    val messages by vm.messages
    val busy by vm.busy
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp),
    ) {
        LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { msg ->
                MessageBubble(
                    text = msg.text,
                    fromUser = msg.role == "user",
                    action = msg.action,
                    actionApplied = msg.actionApplied,
                    onApply = { vm.applyAction(msg) },
                )
            }
            if (busy) {
                item {
                    Text("Ассистент печатает…", style = MaterialTheme.typography.labelSmall, color = Q.inkMuted)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Спроси о чём угодно…") },
                maxLines = 3,
            )
            Spacer(Modifier.padding(4.dp))
            IconButton(
                enabled = !busy && input.isNotBlank(),
                onClick = {
                    vm.send(input.trim())
                    input = ""
                },
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Отправить", tint = Q.accent)
            }
        }
    }
}

@Composable
private fun MessageBubble(
    text: String,
    fromUser: Boolean,
    action: AiAssistant.SuggestedAction?,
    actionApplied: Boolean,
    onApply: () -> Unit,
) {
    Column(horizontalAlignment = if (fromUser) Alignment.End else Alignment.Start, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .background(
                    if (fromUser) Q.accentSoft else Q.surface,
                    RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (fromUser) 16.dp else 4.dp,
                        bottomEnd = if (fromUser) 4.dp else 16.dp,
                    ),
                )
                .border(1.dp, if (fromUser) Q.accent.copy(alpha = 0.25f) else Q.border, RoundedCornerShape(16.dp))
                .padding(12.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
        if (action != null && !fromUser) {
            ActionChip(action, actionApplied, onApply)
        }
    }
}

@Composable
private fun ActionChip(action: AiAssistant.SuggestedAction, applied: Boolean, onApply: () -> Unit) {
    val label = if (action.kind == "add_task") {
        "＋ Задача: ${action.title}"
    } else {
        "＋ Расход: ${MoneyFmt(action.amountRub)} ₽ — ${action.categoryName}"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 4.dp)
            .background(Q.accentSoft, RoundedCornerShape(12.dp))
            .border(1.dp, Q.accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        if (applied) {
            Text(" ✓", color = Q.success, style = MaterialTheme.typography.labelMedium)
        } else {
            TextButton(onClick = onApply) { Text("Применить") }
        }
    }
}

private fun MoneyFmt(rub: Double?): String =
    if (rub == null) "?" else com.questline.app.ui.money.MoneyFormat.text((rub * 100).toLong())

class AssistantViewModel(
    private val app: Context,
    private val repo: AppRepo,
) : ViewModel() {

    data class Msg(
        val role: String, // user | assistant
        val text: String,
        val action: AiAssistant.SuggestedAction? = null,
        val actionApplied: Boolean = false,
    )

    val messages = androidx.compose.runtime.mutableStateOf(
        listOf(
            Msg(
                "assistant",
                "Привет! Я ассистент Questline. Вижу твои квесты, задачи и бюджеты. " +
                    "Могу подсказать план на день, разобрать расходы или добавить задачу — просто попроси.",
            ),
        ),
    )
    val busy = androidx.compose.runtime.mutableStateOf(false)

    fun send(text: String) {
        if (text.isBlank() || busy.value) return
        messages.value = messages.value + Msg("user", text)
        busy.value = true
        viewModelScope.launch {
            try {
                val context = AiAssistant.buildContext(repo)
                val history = messages.value.drop(1).takeLast(10).map { it.role to it.text }
                val reply = AiClient.chat(
                    baseUrl = AiPrefs.baseUrl(app),
                    apiKey = AiPrefs.apiKey(app),
                    model = AiPrefs.model(app),
                    messages = listOf("system" to (AiAssistant.SYSTEM_PROMPT + "\n\nДанные пользователя:\n$context")) + history,
                )
                val (cleanText, action) = AiAssistant.parseReply(reply)
                messages.value = messages.value + Msg("assistant", cleanText.ifEmpty { "…" }, action)
            } catch (e: Exception) {
                messages.value = messages.value + Msg(
                    "assistant",
                    "Не получилось связаться с моделью: ${e.message?.take(120)}. Проверь ключ и сеть в Настройках.",
                )
            } finally {
                busy.value = false
            }
        }
    }

    /** Применить предложенное действие (только по явному тапу). */
    fun applyAction(msg: Msg) {
        val action = msg.action ?: return
        viewModelScope.launch {
            try {
                when (action.kind) {
                    "add_task" -> {
                        val keyCat = repo.categories.observeQuest().first()
                            .firstOrNull { it.questKey == action.questKey }
                        repo.tasks.insert(
                            Task(
                                title = action.title,
                                categoryId = keyCat?.id,
                                complexity = "M",
                                createdAtMillis = System.currentTimeMillis(),
                            ),
                        )
                    }
                    "add_expense" -> {
                        val name = action.categoryName.lowercase()
                        val cat = repo.categories.observeFinance().first()
                            .firstOrNull { it.name.lowercase().contains(name) || name.contains(it.name.lowercase()) }
                            ?: repo.categories.observeFinance().first().firstOrNull { it.name == "Прочее" }
                        cat?.let {
                            repo.txns.insert(
                                Txn(
                                    amountMinor = ((action.amountRub ?: 0.0) * 100).toLong(),
                                    type = "EXPENSE",
                                    categoryId = it.id,
                                    epochDay = AppRepo.todayEpochDay,
                                    note = "Через ассистента",
                                    source = "MANUAL",
                                    createdAtMillis = System.currentTimeMillis(),
                                ),
                            )
                        }
                    }
                }
                messages.value = messages.value.map {
                    if (it === msg) it.copy(actionApplied = true) else it
                }
            } catch (_: Exception) {
                // Тихо: действие можно применить повторно
            }
        }
    }
}

fun assistantVmFactory(context: Context): androidx.lifecycle.ViewModelProvider.Factory = viewModelFactory {
    initializer { AssistantViewModel(context.applicationContext, AppRepo.get(context.applicationContext)) }
}
