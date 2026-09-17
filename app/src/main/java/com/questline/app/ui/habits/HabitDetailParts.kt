package com.questline.app.ui.habits

/* Части экрана привычки (N-02), вынесенные из HabitDetailScreen ради лимита
 * 300 строк: heatmap последних 18 недель (Canvas) и список последних отметок.
 */

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.questline.app.data.habits.Habit
import com.questline.app.data.habits.HabitCheck
import com.questline.app.domain.habits.HabitEngine
import com.questline.app.ui.money.MoneyFormat
import com.questline.app.ui.theme.Q
import com.questline.app.ui.theme.questlineQ
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val HEAT_WEEKS = 18
private const val HEAT_ROWS = 7

/** «янв», «фев» — подписи месяцев под колонками heatmap */
private val monthShort = DateTimeFormatter.ofPattern("LLL", Locale("ru"))

/**
 * Heatmap последних 18 недель (Loop-стиль): недели — колонки, понедельник
 * сверху. Отмечено — accent, заморозка — accentSoft, пропущенный due-день —
 * surfaceAlt с обводкой border, будущий/не по расписанию — surfaceAlt без
 * обводки, сегодня — обводка ink. Дни до создания привычки не рисуются.
 * Цвета захватываются через questlineQ() до DrawScope.
 */
@Composable
internal fun HabitHeatmap(
    habit: Habit,
    checks: List<HabitCheck>,
    today: Long,
    modifier: Modifier = Modifier,
) {
    val q = questlineQ()
    val byDay = remember(checks) { checks.associateBy { it.epochDay } }
    val flexible = habit.scheduleType == HabitEngine.SCHEDULE_TIMES_PER_WEEK
    val todayMonday = today - (LocalDate.ofEpochDay(today).dayOfWeek.value - 1L)
    val firstWeekStart = remember(today) { todayMonday - (HEAT_WEEKS - 1) * 7L }

    val monthLabels = remember(firstWeekStart) {
        (0 until HEAT_WEEKS).map { col ->
            val date = LocalDate.ofEpochDay(firstWeekStart + col * 7L)
            val prev = if (col == 0) null else LocalDate.ofEpochDay(firstWeekStart + (col - 1) * 7L)
            if (prev == null || date.month != prev.month) monthShort.format(date) else null
        }
    }

    Column(modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp),
        ) {
            val gap = 2.dp.toPx()
            val pitchX = size.width / HEAT_WEEKS
            val pitchY = size.height / HEAT_ROWS
            val cell = minOf(pitchX, pitchY) - gap
            val corner = CornerRadius(cell / 4f)

            for (col in 0 until HEAT_WEEKS) {
                val weekStart = firstWeekStart + col * 7L
                for (row in 0 until HEAT_ROWS) {
                    val day = weekStart + row
                    if (day > today || day < habit.createdAt) continue
                    val check = byDay[day]
                    val fill = when {
                        check == null -> q.surfaceAlt
                        check.frozen -> q.accentSoft
                        else -> q.accent
                    }
                    val topLeft = Offset(
                        col * pitchX + (pitchX - cell) / 2f,
                        row * pitchY + (pitchY - cell) / 2f,
                    )
                    drawRoundRect(
                        color = fill,
                        topLeft = topLeft,
                        size = Size(cell, cell),
                        cornerRadius = corner,
                    )
                    val strokeColor = when {
                        day == today -> q.ink
                        check == null && !flexible && HabitEngine.isDue(habit, day) -> q.border
                        else -> null
                    }
                    if (strokeColor != null) {
                        drawRoundRect(
                            color = strokeColor,
                            topLeft = topLeft,
                            size = Size(cell, cell),
                            cornerRadius = corner,
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    }
                }
            }
        }

        // Подписи месяцев: короткое имя под колонкой, где начинается месяц
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            monthLabels.forEach { label ->
                Box(Modifier.weight(1f)) {
                    if (label != null) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = Q.inkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Visible,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Список последних 10 отметок: «Сегодня» / «Вчера» / «14 августа», рядом
 * «❄ заморозка» или прогресс количественной цели («3/30 мин»).
 */
@Composable
internal fun RecentChecksList(
    habit: Habit,
    recent: List<HabitCheck>,
    today: Long,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        if (recent.isEmpty()) {
            Text(
                text = "Отметок пока нет — всё впереди.",
                style = MaterialTheme.typography.bodyMedium,
                color = Q.inkMuted,
            )
            return
        }
        recent.forEachIndexed { index, check ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    text = dayLabel(check.epochDay, today),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = when {
                        check.frozen -> "❄ заморозка"
                        else -> progressLabel(habit, check.value) ?: ""
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Q.inkMuted,
                )
            }
            if (index < recent.lastIndex) HorizontalDivider(color = Q.border, thickness = 1.dp)
        }
    }
}

/** «Сегодня» / «Вчера» / «14 августа» — как в операциях */
private fun dayLabel(epochDay: Long, today: Long): String = when (epochDay) {
    today -> "Сегодня"
    today - 1 -> "Вчера"
    else -> MoneyFormat.dayWords(epochDay)
}
