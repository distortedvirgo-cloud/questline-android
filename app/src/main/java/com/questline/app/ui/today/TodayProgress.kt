package com.questline.app.ui.today

/* Утро-блок «Сегодня»: дата + приветствие по времени суток, уровень с XP-шкалой,
 * строка «Прогресс дня: N/M» (мягкий тон: полный день — «День закрыт ✨»,
 * ноль — просто строка, без упрёков) и стики v2: страйк и монеты.
 */

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.questline.app.data.AppRepo
import com.questline.app.ui.profile.RadarChart
import com.questline.app.ui.theme.Q
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val SCALE_FILL_MS = 250

/** Цифры крупной суммы XP — табличные цифры, крупный размер. */
private val SUM_NUMERAL_STYLE = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")

/** Одноразовый срез прогресса: всегда вычисляется из истории закрытых квестов. */
data class ProgressSnapshot(
    val level: Int = 1,
    val xpIntoLevel: Int = 0,
    val xpNeeded: Int = 120,
    val totalXp: Int = 0,
    val streakDays: Int = 0,
) {
    val fraction: Float get() = if (xpNeeded <= 0) 0f else xpIntoLevel.toFloat() / xpNeeded
}

/** Приветствие по времени суток (ночью — «Доброй ночи»). */
internal fun dayGreeting(now: LocalTime): String = when (now.hour) {
    in 5..11 -> "Доброе утро"
    in 12..17 -> "Добрый день"
    in 18..23 -> "Добрый вечер"
    else -> "Доброй ночи"
}

/** «17 сентября, среда» — дата дня словами. */
internal fun dateWords(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("d MMMM, EEEE", Locale("ru")))

/** Шапка утра: дата дня словами + приветствие. */
@Composable
internal fun MorningHeader(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            dateWords(AppRepo.todayEpochDay),
            style = MaterialTheme.typography.labelMedium,
            color = Q.inkMuted,
        )
        Text(dayGreeting(LocalTime.now()), style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
internal fun ProgressCard(snapshot: ProgressSnapshot) {
    val fillFraction by animateFloatAsState(
        targetValue = snapshot.fraction.coerceIn(0f, 1f),
        animationSpec = tween(SCALE_FILL_MS),
        label = "xpScaleFill",
    )
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = CARD_SHAPE, colors = cardColors()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Уровень ${snapshot.level}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Text("${snapshot.totalXp} XP", style = SUM_NUMERAL_STYLE, color = Q.accent)
            }
            Spacer(Modifier.height(10.dp))
            XpBar(fillFraction)
            Spacer(Modifier.height(8.dp))
            Text(
                "${snapshot.xpIntoLevel} / ${snapshot.xpNeeded} XP до уровня ${snapshot.level + 1}",
                style = MaterialTheme.typography.bodySmall,
                color = Q.inkMuted,
            )
        }
    }
}

@Composable
private fun XpBar(fraction: Float) {
    Box(Modifier.fillMaxWidth().height(10.dp).clip(BAR_SHAPE).background(Q.surfaceAlt)) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(BAR_SHAPE).background(Q.accent))
    }
}

/**
 * «Прогресс дня: N/M» — сколько из сегодняшнего плана закрыто.
 * Полный день отмечается тихим «День закрыт ✨» на accentSoft; при нуле
 * никаких упрёков — просто строка.
 */
@Composable
internal fun DayProgressRow(done: Int, total: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Прогресс дня: $done/$total",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(CHIP_SHAPE)
                .background(Q.surfaceAlt)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        if (total > 0 && done >= total) {
            Text(
                "День закрыт ✨",
                style = MaterialTheme.typography.bodyMedium,
                color = Q.accent,
                modifier = Modifier
                    .clip(CHIP_SHAPE)
                    .background(Q.accentSoft)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

/** Стик-статистика v2: стрик дней и монеты. */
@Composable
internal fun StatCard(modifier: Modifier = Modifier, emoji: String, value: String) {
    OutlinedCard(modifier = modifier, shape = CARD_SHAPE, colors = cardColors()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(emoji, fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Мини-радар характеристик v3 (T-13): тот же RadarChart, компактный, без подписей. */
@Composable
internal fun MiniRadarCard(characteristics: Map<String, Int>) {
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = CARD_SHAPE, colors = cardColors()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                "Баланс характеристик",
                style = MaterialTheme.typography.labelMedium,
                color = Q.inkMuted,
            )
            RadarChart(
                keyXp = characteristics,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                maxValue = 100,
            )
        }
    }
}
