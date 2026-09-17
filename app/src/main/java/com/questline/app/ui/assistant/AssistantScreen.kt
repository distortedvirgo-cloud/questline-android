package com.questline.app.ui.assistant

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.questline.app.ai.AiClient
import com.questline.app.ai.AiPrefs
import com.questline.app.data.AppRepo
import com.questline.app.domain.advice.BudgetPressure
import com.questline.app.domain.advice.LaggingGoal
import com.questline.app.domain.advice.NudgeEngine
import com.questline.app.domain.advice.NudgeInput
import com.questline.app.domain.advice.Tip
import com.questline.app.domain.finance.BurnRate
import com.questline.app.domain.finance.burnRate
import com.questline.app.domain.habits.HabitEngine
import com.questline.app.ui.money.MoneyFormat
import com.questline.app.ui.theme.Q
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Сводка недели для верхней карточки: собирается локально, без сети. */
data class WeekSummary(
    val weekXp: Int,
    /** Успешных отметок привычек за 7 дней (заморозки не считаются). */
    val checksDone: Int,
    /** Лучший стрик среди активных привычек. */
    val bestStreak: Int,
    /** Темп трат месяца (burn rate по всем категориям с планом). */
    val burn: BurnRate,
    val monthSpentMinor: Long,
    val monthPlanMinor: Long,
    /** Активные копилки с ненулевой целью и их прогресс. */
    val goals: List<GoalProgress>,
)

data class GoalProgress(val name: String, val savedMinor: Long, val targetMinor: Long) {
    val percent: Int get() = if (targetMinor <= 0) 0 else ((savedMinor * 100 / targetMinor).toInt()).coerceIn(0, 100)
}

/** Состояние экрана AI-коуча: сводка + ветка советов (офлайн или LLM). */
data class CoachUi(
    val keyConfigured: Boolean = false,
    val summary: WeekSummary? = null,
    /** Офлайн-советы NudgeEngine — только когда ключ не настроен. */
    val offlineTips: List<Tip> = emptyList(),
    val coachBusy: Boolean = false,
    /** 3–5 коротких пунктов ответа модели. */
    val coachAnswer: List<String> = emptyList(),
    val coachError: String? = null,
)

private val COACH_SYSTEM_PROMPT = """
    Ты — добрый AI-коуч приложения Questline (привычки + финансы). Тон
    поддерживающий, без стыда, наказаний и критики. По сводке недели
    пользователя дай 3–5 коротких советов на следующую неделю.
    Правила: по-русски; каждый пункт — одна строка, начинается с «•», без
    markdown и нумерации; конкретно и выполнимо за шаг до 10 минут;
    максимум 500 символов. Не придумывай данные, которых нет в сводке.
""".trimIndent()

/** AI-коуч v3: локальная сводка недели + советы (офлайн NudgeEngine или один LLM-вызов). */
@Composable
fun AssistantScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val vm: AssistantViewModel = viewModel(key = "assistant-coach", factory = assistantVmFactory(context.applicationContext))
    val ui by vm.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        val summary = ui.summary
        if (summary == null) {
            Box(
                modifier = Modifier.fillMaxWidth().height(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Q.accent)
            }
        } else {
            WeekSummaryCard(summary)
        }
        Spacer(Modifier.height(16.dp))
        Text("Советы", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        if (!ui.keyConfigured) {
            ui.offlineTips.forEach { tip ->
                TipCard(tip)
                Spacer(Modifier.height(8.dp))
            }
            SoftNote("Офлайн-советы: подключи ключ в Настройках — и коуч подстроит советы под твои данные.")
        } else {
            Button(
                onClick = vm::askCoach,
                enabled = !ui.coachBusy && summary != null,
                colors = ButtonDefaults.buttonColors(containerColor = Q.accent),
            ) {
                Text(if (ui.coachBusy) "Коуч думает…" else "Спросить коуча")
            }
            if (ui.coachBusy) {
                Spacer(Modifier.height(8.dp))
                CircularProgressIndicator(color = Q.accent, strokeWidth = 2.dp, modifier = Modifier.height(18.dp))
            }
            if (ui.coachAnswer.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                CoachAnswerCard(ui.coachAnswer)
            }
            ui.coachError?.let { error ->
                Spacer(Modifier.height(8.dp))
                SoftNote(error)
            }
            Spacer(Modifier.height(4.dp))
            SoftNote("Ответ модели — только идея: решения принимаешь ты.")
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** Мозг экрана: сводка недели из DAO + один LLM-вызов по кнопке. */
class AssistantViewModel(
    private val app: Context,
    private val repo: AppRepo,
) : ViewModel() {

    private val today = AppRepo.todayEpochDay
    private val monthDate = LocalDate.ofEpochDay(today)
    private val monthStart = monthDate.withDayOfMonth(1).toEpochDay()

    private val _state = MutableStateFlow(CoachUi())
    val state = _state.asStateFlow()

    /** Компактный контекст для коуча (≤1КБ), собирается один раз при входе. */
    private var coachContext: String = ""

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val habits = repo.habits.observeActive().first()
        val checks = repo.habitChecks.observeAll().first()
        val checksByHabit = checks.groupBy { it.habitId }
        val txns = repo.txns.observeRange(monthStart, today).first()
        val finance = repo.categories.observeFinance().first()
        val goals = repo.goals.observeActive().first()
        val weekStart = today - 6

        val planMinor = finance.filter { !it.isIncome }.sumOf { it.budgetMonthlyMinor ?: 0L }
        val spentMinor = txns.filter { it.type == "EXPENSE" }.sumOf { it.amountMinor }
        val consistency = HabitEngine.consistencyByCharacteristic(habits, checks, weekStart)
        val input = NudgeInput(
            habits = habits,
            checks = checks,
            consistency = consistency,
            budgets = budgetPressures(finance, txns),
            laggingGoals = goals
                .filter { it.status == "ACTIVE" && it.targetMinor > 0 && it.savedMinor <= 0L }
                .map { LaggingGoal(it.name) },
            today = today,
        )
        val summary = WeekSummary(
            weekXp = repo.xpLedger.sumBetween(weekStart, today),
            checksDone = checks.count { it.epochDay in weekStart..today && !it.frozen },
            bestStreak = habits.maxOfOrNull {
                HabitEngine.streak(it, checksByHabit[it.id].orEmpty(), today).second
            } ?: 0,
            burn = burnRate(spentMinor, planMinor, monthDate.dayOfMonth, monthDate.lengthOfMonth()),
            monthSpentMinor = spentMinor,
            monthPlanMinor = planMinor,
            goals = goals
                .filter { it.status == "ACTIVE" && it.targetMinor > 0 }
                .map { GoalProgress(it.name, it.savedMinor, it.targetMinor) },
        )
        val configured = AiPrefs.isConfigured(app)
        coachContext = buildCoachContext(summary, habits)
        _state.value = CoachUi(
            keyConfigured = configured,
            summary = summary,
            offlineTips = if (configured) emptyList() else NudgeEngine.weeklyTips(input),
        )
    }

    /** Категории с планом, по которым burn rate FAST или OVER (та же математика T-08). */
    private fun budgetPressures(finance: List<com.questline.app.data.Category>, txns: List<com.questline.app.data.Txn>): List<BudgetPressure> {
        val spentByCategory = txns
            .filter { it.type == "EXPENSE" }
            .groupBy { it.categoryId }
            .mapValues { (_, list) -> list.sumOf { it.amountMinor } }
        return finance.filter { !it.isIncome && (it.budgetMonthlyMinor ?: 0L) > 0L }.mapNotNull { category ->
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

    private fun buildCoachContext(summary: WeekSummary, habits: List<com.questline.app.data.habits.Habit>): String {
        val burnText = when (summary.burn.status) {
            BurnRate.Status.OVER -> "план трат пробит"
            BurnRate.Status.FAST -> "траты быстрее плана на ${summary.burn.overspendPercent} п.п."
            BurnRate.Status.CALM -> "темп трат спокойный"
        }
        val habitsText = habits.take(6)
            .joinToString("; ") { "${it.title} (${it.complexity})" }
            .ifEmpty { "нет активных" }
        val goalsText = summary.goals.joinToString("; ") { "${it.name} ${it.percent}%" }.ifEmpty { "нет" }
        return "Сводка недели: XP ${summary.weekXp}, отметок привычек ${summary.checksDone}, " +
            "лучший стрик ${summary.bestStreak} дн. ${burnText}: " +
            "потрачено ${MoneyFormat.text(summary.monthSpentMinor)} из плана " +
            "${MoneyFormat.text(summary.monthPlanMinor)}. " +
            "Привычки: $habitsText. Копилки: $goalsText."
    }

    /** «Спросить коуча»: один LLM-вызов с компактной сводкой. Повтор — новый ответ. */
    fun askCoach() {
        val ui = _state.value
        if (ui.coachBusy || !ui.keyConfigured || ui.summary == null) return
        _state.value = ui.copy(coachBusy = true, coachError = null)
        viewModelScope.launch {
            try {
                val reply = AiClient.chat(
                    baseUrl = AiPrefs.baseUrl(app),
                    apiKey = AiPrefs.apiKey(app),
                    model = AiPrefs.model(app),
                    messages = listOf(
                        "system" to COACH_SYSTEM_PROMPT,
                        "user" to "$coachContext\n\nДай 3–5 коротких советов на следующую неделю.",
                    ),
                )
                _state.value = _state.value.copy(coachBusy = false, coachAnswer = parseCoachReply(reply))
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    coachBusy = false,
                    coachError = "Коуч не ответил: ${e.message?.take(120) ?: "связь пропала"}. " +
                        "Проверь ключ и сеть в Настройках — а офлайн-советы всегда рядом.",
                )
            }
        }
    }

    private fun parseCoachReply(reply: String): List<String> = reply
        .lines()
        .map { it.trim().removePrefix("•").removePrefix("-").removePrefix("*").trim() }
        .filter { it.isNotBlank() }
        .take(6)
}

fun assistantVmFactory(context: Context): androidx.lifecycle.ViewModelProvider.Factory = viewModelFactory {
    initializer { AssistantViewModel(context.applicationContext, AppRepo.get(context.applicationContext)) }
}
