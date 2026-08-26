package com.questline.app.ui.profile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.questline.app.data.AppRepo
import com.questline.app.data.CoinsLedger
import com.questline.app.domain.ProgressionEngine
import com.questline.app.ui.theme.Q
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Экран профиля: уровень, монеты, радар 5 характеристик,
 * история последних действий. Дизайн по game-ui-ux: элементы anchored,
 * данные событийно через Flows; единственный акцент на экране — радар.
 */
@Composable
fun ProfileScreen(
    onNavigateToMoney: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
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
            keyXp = state.keyXp,
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp),
        )

        Spacer(Modifier.height(14.dp))

        val keysOrdered = listOf("PHYSICS" to "💪 Физика", "MIND" to "🧠 Разум", "MONEY" to "💰 Деньги", "SOCIAL" to "💬 Харизма", "DISCIPLINE" to "🎯 Дисциплина")
        keysOrdered.forEach { (key, label) ->
            val xp = state.keyXp[key] ?: 0
            val maxOfAll = (state.keyXp.values.maxOrNull() ?: 1).coerceAtLeast(1)
            val barFraction by animateFloatAsState(targetValue = xp.toFloat() / maxOfAll, animationSpec = tween(250), label = key)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Text("$xp XP", style = MaterialTheme.typography.labelMedium, color = Q.inkMuted)
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
        Text(
            "⚙ Настройки",
            style = MaterialTheme.typography.titleMedium,
            color = Q.accent,
            modifier = Modifier.clickable { onNavigateToSettings() },
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

@Composable
private fun CoinHistoryRow(entry: CoinsLedger) {
    val time = remember(entry.createdAtMillis) {
        Instant.ofEpochMilli(entry.createdAtMillis).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d MMM HH:mm"))
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
    else -> reason
}

/** Радар характеристик (Canvas), центр = слабость к краю сила */
@Composable
fun RadarChart(keyXp: Map<String, Int>, modifier: Modifier = Modifier) {
    var animated by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animated = true }
    val fraction by animateFloatAsState(if (animated) 1f else 0f, tween(400), label = "radar")

    Canvas(modifier) {
        val keys = listOf("PHYSICS", "MIND", "MONEY", "SOCIAL", "DISCIPLINE")
        val maxXp = (keyXp.values.maxOrNull() ?: 10).coerceAtLeast(1)
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = minOf(size.width, size.height) / 2f - 40f

        fun point(idx: Int, valueFraction: Float): Offset {
            val angle = Math.toRadians(-90.0 + idx * 72.0)
            val r = radius * valueFraction
            return Offset(center.x + (r * kotlin.math.cos(angle)).toFloat(), center.y + (r * kotlin.math.sin(angle)).toFloat())
        }

        // Сетка: 3 кольца
        for (ring in listOf(1 / 3f, 2 / 3f, 1f)) {
            val path = Path()
            keys.indices.forEach { i ->
                val p = point(i, ring)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close()
            drawPath(path, Q.border, style = Stroke(width = 1.dp.toPx()))
        }

        // Данные
        val dataPath = Path()
        keys.forEachIndexed { i, k ->
            val v = ((keyXp[k] ?: 0).toFloat() / maxXp).coerceIn(0.05f, 1f) * fraction
            val p = point(i, v)
            if (i == 0) dataPath.moveTo(p.x, p.y) else dataPath.lineTo(p.x, p.y)
        }
        dataPath.close()
        drawPath(dataPath, Q.accent.copy(alpha = 0.22f))
        drawPath(dataPath, Q.accent, style = Stroke(width = 2.dp.toPx()))

        // Вершины
        keys.forEachIndexed { i, k ->
            val v = ((keyXp[k] ?: 0).toFloat() / maxXp).coerceIn(0.05f, 1f) * fraction
            drawCircle(Q.accent, radius = 5f, center = point(i, v))
        }
    }
}
