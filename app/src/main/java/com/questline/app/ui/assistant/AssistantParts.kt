package com.questline.app.ui.assistant

/* Подсекции экрана «AI-коуч» (T-15): карточка «Как прошла неделя»,
 * карточка офлайн-совета, ответ коуча и мягкие пометки.
 * Стиль карточек — как в ui/today/TodayProgress.kt, цвета только Q.*.
 * Тон поддерживающий, без наказаний и критики.
 */

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.questline.app.domain.advice.Tip
import com.questline.app.domain.finance.BurnRate
import com.questline.app.ui.money.MoneyFormat
import com.questline.app.ui.theme.Q

internal val COACH_CARD_SHAPE = RoundedCornerShape(16.dp)
internal val COACH_NOTE_SHAPE = RoundedCornerShape(12.dp)

@Composable
private fun cardColors() = CardDefaults.outlinedCardColors(containerColor = Q.surface)

/** Верхняя карточка «Как прошла неделя»: XP, чеки, стрик, темп трат, копилки. */
@Composable
internal fun WeekSummaryCard(summary: WeekSummary) {
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = COACH_CARD_SHAPE, colors = cardColors()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Как прошла неделя",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            SummaryStatRow("🏅", "XP за неделю", "+${summary.weekXp}", Q.accent)
            SummaryStatRow("✅", "Отметки привычек", "${summary.checksDone}", Q.ink)
            SummaryStatRow("🔥", "Лучший стрик", "${summary.bestStreak} дн.", Q.ink)
            SummaryStatRow("💸", "Темп трат месяца", burnLabel(summary), burnColor(summary))
            if (summary.goals.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Копилки", style = MaterialTheme.typography.labelMedium, color = Q.inkMuted)
                summary.goals.take(3).forEach { GoalRow(it) }
            }
        }
    }
}

private fun burnLabel(summary: WeekSummary): String = when (summary.burn.status) {
    BurnRate.Status.OVER -> "план пробит"
    BurnRate.Status.FAST -> "быстрее плана на ${summary.burn.overspendPercent} п.п."
    BurnRate.Status.CALM -> "спокойный"
}

@Composable
private fun burnColor(summary: WeekSummary) = when (summary.burn.status) {
    BurnRate.Status.OVER -> Q.danger
    BurnRate.Status.FAST -> Q.warn
    BurnRate.Status.CALM -> Q.success
}

@Composable
private fun SummaryStatRow(emoji: String, label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 13.sp)
        Spacer(Modifier.height(0.dp))
        Text(
            " $label",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

@Composable
private fun GoalRow(goal: GoalProgress) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row {
            Text("🏺 ${goal.name}", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(0.dp))
            Text(
                "${goal.percent}%",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = Q.accent,
            )
        }
        Text(
            "${MoneyFormat.text(goal.savedMinor)} из ${MoneyFormat.text(goal.targetMinor)}",
            style = MaterialTheme.typography.bodySmall,
            color = Q.inkMuted,
        )
    }
}

/** Офлайн-совет NudgeEngine: заголовок + мягкая формулировка. */
@Composable
internal fun TipCard(tip: Tip) {
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = COACH_CARD_SHAPE, colors = cardColors()) {
        Column(Modifier.padding(14.dp)) {
            Text(tip.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(tip.body, style = MaterialTheme.typography.bodyMedium, color = Q.ink)
        }
    }
}

/** Ответ коуча: 3–5 коротких пунктов без markdown. */
@Composable
internal fun CoachAnswerCard(lines: List<String>) {
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = COACH_CARD_SHAPE, colors = cardColors()) {
        Column(Modifier.padding(14.dp)) {
            Text("Коуч отвечает", style = MaterialTheme.typography.labelMedium, color = Q.inkMuted)
            Spacer(Modifier.height(6.dp))
            lines.forEach { line ->
                Text("• $line", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

/** Мягкая пометка на пёстрой подложке surfaceAlt: офлайн-режим или ошибка сети. */
@Composable
internal fun SoftNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = Q.inkMuted,
        modifier = Modifier
            .fillMaxWidth()
            .clip(COACH_NOTE_SHAPE)
            .padding(horizontal = 2.dp, vertical = 4.dp),
    )
}
