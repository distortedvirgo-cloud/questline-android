package com.questline.app.ui.habits

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.questline.app.data.habits.Habit
import com.questline.app.domain.habits.HabitEngine
import com.questline.app.ui.money.colorForIndex
import com.questline.app.ui.theme.Q

/* Подписи, общие для карточки и редактора */
internal val weekdayShort = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

internal val characteristicLabels = linkedMapOf(
    "PHYSICS" to "Физика",
    "MIND" to "Разум",
    "MONEY" to "Деньги",
    "SOCIAL" to "Харизма",
    "DISCIPLINE" to "Дисциплина",
)

/** Расписание коротко: «Ежедневно» / «Пн, Ср, Пт» / «3×/нед» / «Каждые 3 дн» */
internal fun scheduleLabel(habit: Habit): String = when (habit.scheduleType) {
    HabitEngine.SCHEDULE_WEEKDAYS -> weekdaysLabel(habit.weekdaysMask)
    HabitEngine.SCHEDULE_TIMES_PER_WEEK -> "${habit.timesPerWeek}×/нед"
    HabitEngine.SCHEDULE_INTERVAL -> "Каждые ${habit.intervalDays} дн"
    else -> "Ежедневно"
}

internal fun weekdaysLabel(mask: Int): String {
    val days = (0..6).filter { mask and (1 shl it) != 0 }
    return if (days.isEmpty()) "—" else days.joinToString(", ") { weekdayShort[it] }
}

/** «3/8 мин» для количественных; null — обычная чек-привычка */
internal fun progressLabel(habit: Habit, value: Double?): String? {
    val target = habit.targetValue ?: return null
    val unit = habit.unit.orEmpty()
    val head = "${fmt(value ?: 0.0)}/${fmt(target)}"
    return if (unit.isEmpty()) head else "$head $unit"
}

private fun fmt(x: Double): String =
    if (x % 1.0 == 0.0) x.toLong().toString() else x.toString().replace('.', ',')

/** Карточка привычки: чек-кнопка, эмодзи в цветном круге, стрик, неделя,
 *  при пропущенном запланированном дне — чип платной заморозки (T-07).
 *  Тап по телу карточки (не по чеку) — экран привычки (N-02), долгий тап — архив. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HabitCard(
    card: HabitCardUi,
    pulse: HabitsViewModel.HabitXpPulse?,
    canFreeze: Boolean,
    onCheck: () -> Unit,
    onUncheck: () -> Unit,
    onOpenDetail: () -> Unit = {},
    onArchive: () -> Unit,
    onFreeze: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Q.border),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpenDetail, onLongClick = onArchive),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    HabitCheckButton(
                        card = card,
                        onClick = onCheck,
                        onLongClick = onUncheck,
                    )
                    XpPulseText(pulse)
                }
                Spacer(Modifier.size(12.dp))
                EmojiCircle(emoji = card.habit.emoji, colorIndex = card.habit.colorIndex)
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = card.habit.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = metaLine(card),
                        style = MaterialTheme.typography.labelSmall,
                        color = Q.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.size(8.dp))
                StreakBadge(card.streakCurrent)
            }
            Spacer(Modifier.height(8.dp))
            WeekBar(card.weekConsistency)
            if (card.freezeDay != null) {
                Spacer(Modifier.height(8.dp))
                FreezeChip(canAfford = canFreeze, onConfirm = onFreeze)
            }
        }
    }
}

private fun metaLine(card: HabitCardUi): String {
    val schedule = scheduleLabel(card.habit)
    val progress = progressLabel(card.habit, card.todayCheck?.value)
    return if (progress == null) schedule else "$schedule · $progress"
}

/** Чек-кнопка: тап — отметка/+1, долгий тап — снятие; заморозка — снежинка */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HabitCheckButton(
    card: HabitCardUi,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val progress by animateFloatAsState(
        targetValue = if (card.doneToday) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "habitCheckConfirm",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .border(
                width = 2.dp,
                color = if (!card.frozenToday && progress > 0.5f) Q.success else Q.border,
                shape = CircleShape,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        if (card.frozenToday) {
            // Заморозка — нейтральный статус: снежинка на surfaceAlt
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .background(color = Q.surfaceAlt, shape = CircleShape),
            ) {
                Text("❄", style = MaterialTheme.typography.labelMedium, color = Q.inkMuted)
            }
        } else {
            // Мягкий confirm: заливка появляется со scale 0.9 → 1, 200 мс
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer {
                        alpha = progress
                        val scale = 0.9f + 0.1f * progress
                        scaleX = scale
                        scaleY = scale
                    }
                    .background(color = Q.success, shape = CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Q.surface,
                    modifier = Modifier.size(16.dp),
                )
            }
            // Ниже цели — контурная кнопка со счётчиком значения
            if (!card.doneToday && card.habit.targetValue != null) {
                Text(
                    text = fmt(card.todayCheck?.value ?: 0.0),
                    style = MaterialTheme.typography.labelSmall,
                    color = Q.inkMuted,
                )
            }
        }
    }
}

/** Пульс «+N XP» над чеком: всплытие и затухание, без конфетти (оно — вехи T-07) */
@Composable
private fun BoxScope.XpPulseText(pulse: HabitsViewModel.HabitXpPulse?) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(pulse?.seq) {
        if (pulse == null) return@LaunchedEffect
        anim.snapTo(0f)
        anim.animateTo(1f, tween(700, easing = LinearEasing))
    }
    if (pulse == null || anim.value >= 1f) return
    Text(
        text = "+${pulse.xp} XP",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = Q.accent,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .graphicsLayer {
                alpha = 1f - anim.value
                translationY = -34.dp.toPx() * anim.value
            },
    )
}

/** Эмодзи в цветном круге: colorIndex → палитра срезов (переиспользуем утилиту денег) */
@Composable
private fun EmojiCircle(emoji: String, colorIndex: Int) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(colorForIndex(colorIndex).copy(alpha = 0.16f)),
    ) {
        Text(text = emoji.ifEmpty { "🔁" }, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Бейдж стрика «🔥 N» */
@Composable
private fun StreakBadge(streak: Int) {
    Text(
        text = "🔥 $streak",
        style = MaterialTheme.typography.labelMedium,
        color = if (streak > 0) Q.ink else Q.inkMuted,
    )
}

/** Доля выполненного за неделю — тонкая шкала как в «Сегодня» */
@Composable
private fun WeekBar(fraction: Double) {
    val f = fraction.toFloat().coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Q.surfaceAlt),
    ) {
        Box(
            Modifier
                .fillMaxWidth(f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(Q.success),
        )
    }
}
