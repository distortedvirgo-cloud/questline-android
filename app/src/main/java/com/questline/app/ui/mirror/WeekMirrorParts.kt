package com.questline.app.ui.mirror

/* Секции «Зеркала недели» v3 (T-14) и сбор данных для них.
 * Итог недели (XP из xp_ledger, чеки привычек, дни «без нуля»),
 * консистентность по 5 характеристикам за последние 7 дней, советы
 * NudgeEngine.weeklyTips и карточка-акцент «Фокус недели».
 * Тон без наказаний, цвета только из палитры Q.*.
 */

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.questline.app.data.AppRepo
import com.questline.app.data.Category
import com.questline.app.data.Txn
import com.questline.app.domain.advice.BudgetPressure
import com.questline.app.domain.advice.LaggingGoal
import com.questline.app.domain.advice.NudgeEngine
import com.questline.app.domain.advice.NudgeInput
import com.questline.app.domain.advice.Tip
import com.questline.app.domain.finance.BurnRate
import com.questline.app.domain.finance.burnRate
import com.questline.app.domain.habits.HabitEngine
import com.questline.app.ui.theme.Q
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlin.math.roundToInt

/** Подписи характеристик с эмодзи (готовый маппинг v2-зеркала). */
internal val characteristicRows = listOf(
    "PHYSICS" to "💪 Физика",
    "MIND" to "🧠 Разум",
    "MONEY" to "💰 Деньги",
    "SOCIAL" to "💬 Харизма",
    "DISCIPLINE" to "🎯 Дисциплина",
)

/** Готовые данные зеркала: экран только рисует, вся арифметика здесь. */
data class WeekMirrorUi(
    val weekXp: Int,
    /** Чеков привычек за 7 дней (заморозки — не выполнение, не считаются). */
    val checksDone: Int,
    /** Дней недели «без нуля»: была отметка/заморозка или начисление XP. */
    val activeDays: Int,
    /** 0..100 по ключам характеристик, окно — последние 7 дней. */
    val consistency: Map<String, Double>,
    /** Три совета NudgeEngine.weeklyTips; первый — «Фокус недели». */
    val tips: List<Tip>,
) {
    val focus: Tip get() = tips.first()
}

/**
 * Один расчёт на вход на экран: советы строятся от даты (seed = epochDay)
 * и снапшота данных, поэтому между пересозданиями экрана не «прыгают».
 */
suspend fun buildWeekMirrorUi(repo: AppRepo, today: Long): WeekMirrorUi {
    val weekStart = today - 6
    val habits = repo.habits.observeActive().first()
    val checks = repo.habitChecks.observeAll().first()
    val weekChecks = checks.filter { it.epochDay in weekStart..today }
    val monthStart = LocalDate.ofEpochDay(today).withDayOfMonth(1).toEpochDay()
    val txns = repo.txns.observeRange(monthStart, today).first()
    val finance = repo.categories.observeFinance().first()
    val goals = repo.goals.observeActive().first()

    val consistency = HabitEngine.consistencyByCharacteristic(habits, checks, weekStart)
    val input = NudgeInput(
        habits = habits,
        checks = checks,
        consistency = consistency,
        budgets = budgetPressures(finance, txns, today),
        laggingGoals = goals
            .filter { it.status == "ACTIVE" && it.targetMinor > 0 && it.savedMinor <= 0L }
            .map { LaggingGoal(it.name) },
        today = today,
    )
    return WeekMirrorUi(
        weekXp = repo.xpLedger.sumBetween(weekStart, today),
        checksDone = weekChecks.count { !it.frozen },
        activeDays = (repo.xpLedger.daysWithXpBetween(weekStart, today).toSet() +
            weekChecks.map { it.epochDay }).size,
        consistency = consistency,
        tips = NudgeEngine.weeklyTips(input),
    )
}

/** Категории с планом, по которым burn rate FAST или OVER (та же математика T-08). */
private fun budgetPressures(
    categories: List<Category>,
    txns: List<Txn>,
    today: Long,
): List<BudgetPressure> {
    val monthDate = LocalDate.ofEpochDay(today)
    val spentByCategory = txns
        .filter { it.type == "EXPENSE" }
        .groupBy { it.categoryId }
        .mapValues { (_, list) -> list.sumOf { it.amountMinor } }
    return categories.filter { !it.isIncome && (it.budgetMonthlyMinor ?: 0L) > 0L }.mapNotNull { category ->
        val rate = burnRate(
            spentMinor = spentByCategory[category.id] ?: 0L,
            planMinor = category.budgetMonthlyMinor ?: 0L,
            dayOfMonth = monthDate.dayOfMonth,
            daysInMonth = monthDate.lengthOfMonth(),
        )
        when (rate.status) {
            BurnRate.Status.CALM -> null
            else -> BudgetPressure(category.name, rate.status, rate.overspendPercent)
        }
    }
}

// ---------------- Секции экрана ----------------

/** Секция 1: итог недели — XP, чеки привычек, дни «без нуля». */
@Composable
internal fun WeekSummarySection(ui: WeekMirrorUi) {
    MirrorCard(Q.surface, Q.border) {
        Text("Итог недели", style = MaterialTheme.typography.labelMedium, color = Q.inkMuted)
        Spacer(Modifier.height(8.dp))
        Text(
            "+${ui.weekXp} XP",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Q.accent,
        )
        Spacer(Modifier.height(8.dp))
        SummaryRow("Отметки привычек", "${ui.checksDone}")
        SummaryRow("Дней без нуля", "${ui.activeDays} из 7")
        if (ui.weekXp == 0 && ui.checksDone == 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Пока тихо — всё получится с первого малого шага",
                style = MaterialTheme.typography.bodySmall,
                color = Q.inkMuted,
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

/** Секция 2: консистентность по 5 характеристикам за последние 7 дней. */
@Composable
internal fun ConsistencySection(ui: WeekMirrorUi) {
    MirrorCard(Q.surface, Q.border) {
        Text(
            "Консистентность по характеристикам",
            style = MaterialTheme.typography.labelMedium,
            color = Q.inkMuted,
        )
        Spacer(Modifier.height(8.dp))
        characteristicRows.forEach { (key, label) ->
            val percent = (ui.consistency[key] ?: 0.0).roundToInt()
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row {
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.weight(1f))
                    Text("$percent%", style = MaterialTheme.typography.bodyLarge, color = Q.inkMuted)
                }
                Spacer(Modifier.height(4.dp))
                ConsistencyBar(percent / 100f)
            }
        }
    }
}

/** Тонкая шкала той же формы, что XP-бар на «Сегодня». */
@Composable
private fun ConsistencyBar(fraction: Float) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(BAR_SHAPE).background(Q.surfaceAlt)) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(BAR_SHAPE)
                .background(Q.accent),
        )
    }
}

/** Секция 3: советы на следующую неделю — советы 2–3 из weeklyTips. */
@Composable
internal fun WeekTipsSection(ui: WeekMirrorUi) {
    MirrorCard(Q.surface, Q.border) {
        Text("Советы на следующую неделю", style = MaterialTheme.typography.labelMedium, color = Q.inkMuted)
        Spacer(Modifier.height(8.dp))
        ui.tips.drop(1).forEach { tip ->
            Column(Modifier.padding(vertical = 6.dp)) {
                Text(tip.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(tip.body, style = MaterialTheme.typography.bodyMedium, color = Q.inkMuted)
            }
        }
    }
}

/** Секция 4: фокус недели — первый совет weeklyTips, карточка-акцент. */
@Composable
internal fun WeekFocusCard(ui: WeekMirrorUi) {
    MirrorCard(Q.accentSoft, Q.border) {
        Text(
            "Фокус недели",
            style = MaterialTheme.typography.labelMedium,
            color = Q.accent,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            ui.focus.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Q.ink,
        )
        Spacer(Modifier.height(4.dp))
        Text(ui.focus.body, style = MaterialTheme.typography.bodyMedium, color = Q.ink)
    }
}

/** Карточка зеркала: тот же паттерн, что карточки «Сегодня» (граница вместо тени). */
@Composable
internal fun MirrorCard(
    containerColor: Color,
    borderColor: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        content = { Column(Modifier.padding(16.dp), content = content) },
    )
}

private val BAR_SHAPE = RoundedCornerShape(50)
