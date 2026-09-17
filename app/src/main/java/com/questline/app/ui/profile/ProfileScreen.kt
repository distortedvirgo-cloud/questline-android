package com.questline.app.ui.profile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.questline.app.data.AppRepo
import com.questline.app.data.CoinsLedger
import com.questline.app.domain.ProgressionEngine
import com.questline.app.ui.theme.Q
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Экран профиля: уровень, монеты, радар 5 характеристик,
 * история последних действий. Дизайн по game-ui-ux: элементы anchored,
 * данные событийно через Flows; единственный акцент на экране — радар.
 */
@Composable
fun ProfileScreen(
    onNavigateToMoney: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToMirror: () -> Unit = {},
    onNavigateToShop: () -> Unit = {},
    onNavigateToAssistant: () -> Unit = {},
) {
    val profileContext = LocalContext.current
    val vm: ProfileViewModel = viewModel(key = "profile", factory = profileVmFactory(profileContext))
    val state by vm.state
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Q.bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Профиль", style = MaterialTheme.typography.headlineSmall)

        Spacer(Modifier.height(12.dp))

        // Шапка: уровень и монеты
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(Modifier.weight(1f), label = "Уровень", value = "${state.level}", sub = "+${state.xpIntoLevel}/${state.xpNeeded?.xpNeeded ?: ProgressionEngine.xpToNext(state.level)} XP")
            StatCard(Modifier.weight(1f), label = "Монеты", value = "${state.coins}", sub = "Копи на темы и рамки")
        }

        Spacer(Modifier.height(18.dp))
        Text("Характеристики", style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.height(8.dp))

        RadarChart(
            keyXp = state.characteristics,
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp),
            maxValue = 100,
        )

        Spacer(Modifier.height(14.dp))

        val keysOrdered = listOf("PHYSICS" to "💪 Физика", "MIND" to "🧠 Разум", "MONEY" to "💰 Деньги", "SOCIAL" to "💬 Харизма", "DISCIPLINE" to "🎯 Дисциплина")
        keysOrdered.forEach { (key, label) ->
            val value = state.characteristics[key] ?: 0
            // Шкала абсолютная 0..100: сила сферы, а не относительный XP из квестов
            val barFraction by animateFloatAsState(targetValue = value / 100f, animationSpec = tween(250), label = key)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Text("$value%", style = MaterialTheme.typography.labelMedium, color = Q.inkMuted)
            }
            Box(
                Modifier
                    .padding(top = 4.dp, bottom = 10.dp)
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(Q.surfaceAlt, RoundedCornerShape(3.dp)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(barFraction.coerceIn(0f, 1f))
                        .height(6.dp)
                        .background(Q.accent, RoundedCornerShape(3.dp)),
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text("История наград", style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.height(6.dp))

        if (state.recentCoins.isEmpty()) {
            HintCard("Закрывай квесты — награды появятся здесь.")
        } else {
            Column {
                state.recentCoins.take(20).forEach { entry ->
                    CoinHistoryRow(entry)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("Вехи стрика", style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.height(8.dp))

        if (state.streakMilestones.isEmpty()) {
            HintCard("Держи серию 7 дней — вехи появятся здесь.")
        } else {
            Column {
                state.streakMilestones.forEach { row ->
                    StreakMilestonesRow(row)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("Меню", style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.height(8.dp))

        ProfileMenu(
            onMirror = onNavigateToMirror,
            onShop = onNavigateToShop,
            onAssistant = onNavigateToAssistant,
            onSettings = onNavigateToSettings,
        )
    }
}

@Composable
private fun StatCard(modifier: Modifier, label: String, value: String, sub: String) {
    Column(
        modifier = modifier
            .background(Q.surface, RoundedCornerShape(16.dp))
            .border(1.dp, Q.border, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Q.inkMuted)
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(sub, style = MaterialTheme.typography.labelSmall, color = Q.inkMuted)
    }
}

@Composable
private fun HintCard(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = Q.inkMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
    )
}

/** Строка вех: эмодзи + название привычки + бейджи достигнутых вех 7/30/100 */
@Composable
private fun StreakMilestonesRow(row: HabitStreakMilestones) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${row.emoji.ifEmpty { "🔁" }} ${row.title}",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        row.achieved.forEach { milestone -> MilestoneBadge(milestone) }
    }
}

/** Бейдж вехи: тихая плашка accentSoft, без красного/янтарного (не статус данных) */
@Composable
private fun MilestoneBadge(milestone: Int) {
    Text(
        text = "$milestone",
        style = MaterialTheme.typography.labelSmall,
        color = Q.accent,
        modifier = Modifier
            .padding(start = 4.dp)
            .background(Q.accentSoft, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun CoinHistoryRow(entry: CoinsLedger) {
    val time = remember(entry.createdAtMillis) {
        Instant.ofEpochMilli(entry.createdAtMillis).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d MMM HH:mm", Locale("ru")))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            reasonText(entry.reason),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(time, style = MaterialTheme.typography.labelSmall, color = Q.inkMuted)
        Spacer(Modifier.size(10.dp))
        Text(
            (if (entry.delta >= 0) "+" else "") + "${entry.delta}",
            color = if (entry.delta >= 0) Q.success else Q.danger,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
    }
}

private fun reasonText(reason: String): String = when (reason) {
    "QUEST_DONE" -> "Квест выполнен"
    "BUDGET_OK" -> "Бюджет сошёлся"
    "GOAL_DEPOSIT" -> "Взнос в копилку"
    "SHOP_PURCHASE" -> "Покупка в магазине"
    "MILESTONE" -> "Веха стрика"
    "HABIT_FREEZE" -> "Заморозка дня"
    else -> reason
}
