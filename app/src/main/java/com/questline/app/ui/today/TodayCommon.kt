package com.questline.app.ui.today

/* Общие формы, цвета и расчёт плана дня для экрана «Сегодня» (T-06). Секции
 * разбиты по файлам, чтобы держать лимит STYLE.md ≤300 строк на файл.
 */

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.questline.app.data.Task
import com.questline.app.data.habits.Habit
import com.questline.app.data.habits.HabitCheck
import com.questline.app.domain.habits.HabitEngine
import com.questline.app.ui.theme.Q

internal val CARD_SHAPE = RoundedCornerShape(16.dp)
internal val BAR_SHAPE = RoundedCornerShape(50)
internal val CHIP_SHAPE = RoundedCornerShape(12.dp)

@Composable
internal fun cardColors() = CardDefaults.outlinedCardColors(containerColor = Q.surface)

/** Сколько из сегодняшнего плана закрыто: привычки isDue + задачи на сегодня. */
internal fun dayProgress(
    habits: List<Habit>,
    checks: List<HabitCheck>,
    tasks: List<Task>,
    today: Long,
): Pair<Int, Int> {
    val dueHabits = habits.filter { HabitEngine.isDue(it, today) }
    val checksByHabit = checks.associateBy { it.habitId }
    val done = dueHabits.count { isHabitDoneToday(it, checksByHabit[it.id]) } +
        tasks.count { it.done || (it.repeatIntervalDays > 0 && it.lastDoneEpochDay == today) }
    return done to dueHabits.size + tasks.size
}

