package com.questline.app.ui.today

/* Секция «Привычки дня» (T-06): компактные ряды привычек, назначенных на
 * сегодня (HabitEngine.isDue). Тап = отметка через AppRepo.checkHabit — XP
 * и дневной кап считаются внутри repo; количественная цель накапливает
 * значение по +1 за тап; долгий тап снимает отметку (repo.uncheckHabit).
 * Все закрыты — секция сворачивается в строку «Привычки: N/N ✨» (тап раскрывает).
 * XP-полёт при закрытии — переиспользованный QuestEffects, как у квестов.
 * Пропущенный запланированный день — чип платной заморозки (T-07).
 */

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.questline.app.data.habits.Habit
import com.questline.app.data.habits.HabitCheck
import com.questline.app.domain.habits.HabitEngine
import com.questline.app.ui.habits.FreezeChip
import com.questline.app.ui.habits.canAffordFreeze
import com.questline.app.ui.money.colorForIndex
import com.questline.app.ui.theme.Q
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Привычка закрыта сегодня: обычный чек или количественное значение достигло цели. */
internal fun isHabitDoneToday(habit: Habit, check: HabitCheck?): Boolean =
    check != null && (habit.targetValue == null || (check.value ?: 0.0) >= habit.targetValue)

/** Подпись прогресса количественной цели: «3/8 стаканов» (дробные — как есть). */
private fun quantityLabel(habit: Habit, check: HabitCheck?): String {
    val target = habit.targetValue ?: return ""
    fun fmt(v: Double): String {
        val whole = v.roundToInt()
        return if (v == whole.toDouble()) whole.toString() else v.toString()
    }
    val value = fmt(check?.value ?: 0.0)
    val unit = habit.unit?.let { " $it" } ?: ""
    return "$value/${fmt(target)}$unit"
}

/**
 * Секция привычек дня. Показывается только если на сегодня есть назначенные
 * привычки; свернулась — значит, всё выполнено.
 */
@Composable
internal fun TodayHabitsSection(
    habits: List<Habit>,
    checks: List<HabitCheck>,
    streaks: Map<Long, Int>,
    freezeOffers: Map<Long, Long>,
    coins: Int,
    today: Long,
    vm: TodayViewModel,
) {
    val dueHabits = habits.filter { HabitEngine.isDue(it, today) }
    if (dueHabits.isEmpty()) return
    val checksByHabit = checks.associateBy { it.habitId }
    val doneCount = dueHabits.count { isHabitDoneToday(it, checksByHabit[it.id]) }
    val allDone = doneCount >= dueHabits.size

    var expanded by remember { mutableStateOf(false) }

    Text("Привычки дня", style = MaterialTheme.typography.titleMedium, color = Q.inkMuted)
    Spacer(Modifier.height(8.dp))

    if (allDone && !expanded) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CHIP_SHAPE)
                .background(Q.surfaceAlt)
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Привычки: $doneCount/${dueHabits.size} ✨", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            Text("⌄", style = MaterialTheme.typography.titleMedium, color = Q.inkMuted)
        }
        return
    }

    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = CARD_SHAPE, colors = cardColors()) {
        Column {
            dueHabits.forEachIndexed { index, habit ->
                if (index > 0) HorizontalDivider(color = Q.border, thickness = 1.dp)
                TodayHabitRow(
                    habit = habit,
                    check = checksByHabit[habit.id],
                    streak = streaks[habit.id] ?: 0,
                    freezeDay = freezeOffers[habit.id],
                    canFreeze = canAffordFreeze(coins),
                    vm = vm,
                )
            }
        }
    }
}

/**
 * Ряд привычки: эмодзи в цветном круге (colorIndex, маппинг как у карточек
 * привычек), название, стрик «🔥N», количественный прогресс, чек-кнопка справа.
 * Тап по ряду/кнопке = отметка (+1 для количественной); долгий тап = снятие.
 * Пропущенный запланированный день — компактный чип заморозки (T-07);
 * замороженный день — нейтральная снежинка вместо галочки.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TodayHabitRow(
    habit: Habit,
    check: HabitCheck?,
    streak: Int,
    freezeDay: Long?,
    canFreeze: Boolean,
    vm: TodayViewModel,
) {
    val scope = rememberCoroutineScope()
    val burst = rememberQuestBurstState(seed = habit.id * 31 + 7)
    val done = isHabitDoneToday(habit, check)
    val frozen = check?.frozen == true
    var completing by remember { mutableStateOf(false) } // анимация закрытия
    var rowTopLeft by remember { mutableStateOf(Offset.Zero) }
    var buttonCenter by remember { mutableStateOf(Offset.Zero) }

    val ringColor = colorForIndex(habit.colorIndex)

    fun onCheckTap() {
        if (habit.targetValue == null) {
            // Простая привычка: эффекты сначала, реальная отметка — по onFinished (как у квестов).
            if (done || completing) return
            completing = true
            return
        }
        // Количественная: тап = +1 к значению, отметка пишется сразу (≤2 касания).
        if (done) return
        val newValue = (check?.value ?: 0.0) + 1.0
        if (newValue >= habit.targetValue) completing = true // только визуал; XP — внутри repo
        vm.checkHabit(habit, newValue)
    }

    fun onUncheck() {
        if (check != null && !completing) {
            scope.launch { vm.uncheckHabit(habit.id) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { rowTopLeft = it.boundsInWindow().topLeft },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { onCheckTap() }, onLongClick = { onUncheck() })
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(ringColor.copy(alpha = 0.16f))
                    .border(1.dp, ringColor.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(habit.emoji, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    habit.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (done) Q.inkMuted else Q.ink,
                    textDecoration = if (done && !frozen) TextDecoration.LineThrough else null,
                    maxLines = 2,
                )
                if (streak > 0 || habit.targetValue != null) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (streak > 0) {
                            Text(
                                "🔥$streak",
                                style = MaterialTheme.typography.labelMedium,
                                color = Q.inkMuted,
                            )
                        }
                        if (streak > 0 && habit.targetValue != null) Spacer(Modifier.width(8.dp))
                        if (habit.targetValue != null) {
                            Text(
                                quantityLabel(habit, check),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (done) Q.success else Q.inkMuted,
                            )
                        }
                    }
                }
                // Пропущенный запланированный день: платная заморозка за монеты (T-07)
                if (freezeDay != null) {
                    Spacer(Modifier.height(4.dp))
                    FreezeChip(
                        canAfford = canFreeze,
                        onConfirm = { vm.freezeHabit(habit, freezeDay) },
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            HabitCheckButton(
                done = done,
                frozen = frozen,
                modifier = Modifier.onGloballyPositioned { buttonCenter = it.boundsInWindow().center },
            )
        }
        QuestCompletionOverlay(
            visible = completing,
            xpText = "+${HabitEngine.xpForHabit(habit.complexity)} XP",
            origin = buttonCenter - rowTopLeft,
            onFinished = {
                if (habit.targetValue == null) {
                    vm.checkHabit(habit, null) // простая: отметка по окончании анимации
                }
                // Количественная: отметка уже в repo (vm.checkHabit), эффекты догорают.
            },
            state = burst,
        )
    }
}

/** Круглая чек-кнопка: выполнено — success-заливка с галочкой; заморозка —
 *  нейтральная снежинка на surfaceAlt; иначе тихий круг с «+». */
@Composable
private fun HabitCheckButton(done: Boolean, frozen: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(
                when {
                    frozen -> Q.surfaceAlt
                    done -> Q.success
                    else -> Q.surfaceAlt
                },
            )
            .border(1.dp, if (done && !frozen) Q.success else Q.border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            when {
                frozen -> "❄"
                done -> "✓"
                else -> "+"
            },
            color = when {
                frozen -> Q.inkMuted
                done -> Q.surface
                else -> Q.inkMuted
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
