package com.questline.app.ui.mirror

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.questline.app.data.AppRepo
import com.questline.app.data.Category
import com.questline.app.data.Quest
import com.questline.app.domain.ProgressionEngine
import com.questline.app.ui.money.MoneyFormat
import com.questline.app.ui.theme.Q
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/* ============================================================
 * T-13 «Зеркало недели»: сводка прогресса, расходов и советов.
 * Все данные вычисляются на месте из истории закрытых квестов,
 * транзакций недели и бюджетов месяца — ничего не хранится.
 * ============================================================ */

/** Сводные цифры недели. questsByKey — счётчик закрытых по questKey. */
data class WeekStats(
    val weekQuestsDone: Int,
    val weekXp: Int,
    val streakDays: Int,
    val questsByKey: Map<String, Int>,
    /** Категории с расходом >= 80% месячного бюджета: «emoji Имя» */
    val budgetsAtRisk: List<String>,
    val weekExpenseMinor: Long,
    /** Топ-2 расходов недели: «emoji Имя» → сумма */
    val topCategories: List<Pair<String, Long>>,
)

suspend fun buildWeekStats(repo: AppRepo, todayEpochDay: Long): WeekStats {
    val weekFrom = todayEpochDay - 6

    var weekQuestsDone = 0
    var weekXp = 0
    val byKey = HashMap<String, Int>()
    val closedDays = HashSet<Long>()
    for (quest in repo.quests.allDone()) {
        val day = quest.closedEpochDay() ?: continue
        closedDays += day
        if (day in weekFrom..todayEpochDay) {
            weekQuestsDone++
            weekXp += quest.xpReward
            byKey[quest.questKey] = (byKey[quest.questKey] ?: 0) + 1
        }
    }

    val txns = repo.txns.observeRange(weekFrom, todayEpochDay).first()
    val expenses = txns.filter { it.type == "EXPENSE" }
    val weekExpenseMinor = expenses.sumOf { it.amountMinor }

    // Топ-2 категории расходов недели
    val idToName = HashMap<Long, String>()
    for (id in expenses.map { it.categoryId }.distinct()) {
        idToName[id] = repo.categories.byId(id)?.let { displayName(it) } ?: "Прочее"
    }
    val sumsById = HashMap<Long, Long>()
    for (t in expenses) sumsById[t.categoryId] = (sumsById[t.categoryId] ?: 0L) + t.amountMinor
    val topCategories = sumsById.entries
        .sortedByDescending { it.value }
        .take(2)
        .map { (idToName[it.key] ?: "Прочее") to it.value }

    // Бюджеты под давлением: месяц от 1-го числа до сегодня
    val monthStart = LocalDate.ofEpochDay(todayEpochDay).withDayOfMonth(1).toEpochDay()
    val budgetsAtRisk = repo.categories.all()
        .filter { it.kind == "FINANCE" && it.budgetMonthlyMinor != null }
        .filter { c ->
            val spent = repo.txns.spentInCategory(c.id, monthStart, todayEpochDay)
            spent * 100 >= c.budgetMonthlyMinor!! * 80
        }
        .map { displayName(it) }

    return WeekStats(
        weekQuestsDone = weekQuestsDone,
        weekXp = weekXp,
        streakDays = ProgressionEngine.currentStreak(closedDays, todayEpochDay),
        questsByKey = byKey,
        budgetsAtRisk = budgetsAtRisk,
        weekExpenseMinor = weekExpenseMinor,
        topCategories = topCategories,
    )
}

private fun Quest.closedEpochDay(): Long? =
    closedAtMillis?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
    }

/** «☕️ Кафе и рестораны»: имя с эмодзи, если он есть */
private fun displayName(category: Category): String =
    if (category.emoji.isBlank()) category.name else "${category.emoji} ${category.name}"

// ---------------- Экран ----------------

@Composable
fun WeekMirrorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var stats by remember { mutableStateOf<WeekStats?>(null) }
    LaunchedEffect(Unit) {
        stats = buildWeekStats(AppRepo.get(context), AppRepo.todayEpochDay)
    }
    Scaffold(
        containerColor = Q.bg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Q.bg)
                    .statusBarsPadding()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Назад",
                        tint = Q.ink,
                    )
                }
                Text(
                    "Зеркало недели",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        },
    ) { innerPadding ->
        val s = stats
        // До загрузки данных карточки не рисуем вовсе
        if (s != null) Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            MirrorCard {
                Text("Квесты за неделю", style = MaterialTheme.typography.labelMedium, color = Q.inkMuted)
                Text(
                    "${s.weekQuestsDone} · +${s.weekXp} XP",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Q.accent,
                )
            }
            MirrorCard {
                Text("Стрик", style = MaterialTheme.typography.labelMedium, color = Q.inkMuted)
                Text(
                    "\uD83D\uDD25 ${s.streakDays} ${questsWord(s.streakDays)} подряд",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (s.streakDays > 0) Q.ink else Q.inkMuted,
                )
            }
            MirrorCard {
                Text("Характеристики", style = MaterialTheme.typography.labelMedium, color = Q.inkMuted)
                characteristicRows.forEach { (key, label) ->
                    val done = s.questsByKey[key] ?: 0
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "$done ${questsWord(done)}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Q.inkMuted,
                        )
                    }
                }
            }
            MirrorCard {
                Text("Расходы недели", style = MaterialTheme.typography.labelMedium, color = Q.inkMuted)
                Text(
                    MoneyFormat.text(s.weekExpenseMinor),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                s.topCategories.forEach { (name, sum) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.weight(1f))
                        Text(MoneyFormat.text(sum), style = MaterialTheme.typography.bodyLarge, color = Q.inkMuted)
                    }
                }
            }
            MirrorCard {
                Text("Бюджеты под давлением", style = MaterialTheme.typography.labelMedium, color = Q.inkMuted)
                if (s.budgetsAtRisk.isEmpty()) {
                    Text(
                        "Все бюджеты в норме",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Q.success,
                    )
                } else {
                    s.budgetsAtRisk.forEach { name ->
                        Text(name, style = MaterialTheme.typography.bodyLarge, color = Q.warn)
                    }
                }
            }
            MirrorCard {
                Text("Советы недели", style = MaterialTheme.typography.labelMedium, color = Q.inkMuted)
                weekTips(s).forEach { tip ->
                    Text("• $tip", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
            AiAdviceCard(stats = s)
        }
    }
}

/** AI-совет недели: виден только с настроенным ключом, тихо скрывается при ошибке */
@Composable
private fun AiAdviceCard(stats: WeekStats) {
    val context = androidx.compose.ui.platform.LocalContext.current
    if (!com.questline.app.ai.AiPrefs.isConfigured(context)) return
    val scope = rememberCoroutineScope()
    var advice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    MirrorCard {
        Text("AI-коуч", style = MaterialTheme.typography.labelMedium, color = Q.inkMuted)
        Spacer(Modifier.height(4.dp))
        val text = advice
        if (text != null) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
        } else {
            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            advice = com.questline.app.ai.AiFeatures.weekAdvice(
                                context,
                                "Квестов закрыто: ${stats.weekQuestsDone}, XP: ${stats.weekXp}, стрик: ${stats.streakDays} дн. " +
                                    "Расходы за неделю: ${com.questline.app.ui.money.MoneyFormat.text(stats.weekExpenseMinor)}. " +
                                    if (stats.budgetsAtRisk.isEmpty()) "Бюджеты в норме."
                                    else "Бюджеты под давлением: ${stats.budgetsAtRisk.joinToString(", ")}.",
                            )
                        } catch (_: Exception) {
                            advice = "AI недоступен: проверь ключ и сеть в Настройках."
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text(if (busy) "Думаю…" else "✨ Спросить совет") }
        }
    }
}

@Composable
private fun MirrorCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = RoundedCornerShape(16.dp),
        color = Q.surface,
        border = BorderStroke(1.dp, Q.border),
        content = { Column(Modifier.padding(16.dp), content = content) },
    )
}

/** PHYSICS/MIND/MONEY/SOCIAL/DISCIPLINE в порядке сидирования AppRepo */
private val characteristicRows = listOf(
    "PHYSICS" to "💪 Физика",
    "MIND" to "🧠 Разум",
    "MONEY" to "💰 Деньги",
    "SOCIAL" to "💬 Харизма",
    "DISCIPLINE" to "🎯 Дисциплина",
)

private fun questsWord(n: Int): String {
    val tens = n % 10
    val hundreds = n % 100
    return when {
        tens == 1 && hundreds != 11 -> "квест"
        tens in 2..4 && hundreds !in 12..14 -> "квеста"
        else -> "квестов"
    }
}

/** Правила без ИИ: максимум три строки, «иначе» — только когда пусто */
private fun weekTips(s: WeekStats): List<String> = buildList {
    if (s.budgetsAtRisk.isNotEmpty()) {
        add("Держи категорию «${s.budgetsAtRisk.first()}» под контролем до конца месяца")
    }
    if (s.weekQuestsDone < 3) {
        add("Закрой хотя бы 3 квеста в неделю — маленькие победы складываются в привычку")
    }
    if (s.questsByKey["MONEY"] == null) {
        add("Попробуй денежный квест — например, записать все траты")
    }
    if (isEmpty()) add("Отличная неделя — держи ритм!")
}
