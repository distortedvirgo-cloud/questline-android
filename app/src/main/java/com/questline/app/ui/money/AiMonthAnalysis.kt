package com.questline.app.ui.money

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.questline.app.ai.AiClient
import com.questline.app.ai.AiPrefs
import com.questline.app.data.AppRepo
import com.questline.app.ui.theme.Q
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Результат разбора месяца: пункты советов, нечего разбирать или ошибка. */
sealed class MonthAnalysis {
    data class Done(val points: List<String>) : MonthAnalysis()
    data class Failure(val message: String) : MonthAnalysis()
    object NoData : MonthAnalysis()
}

/** Траты месяца по категории */
private data class CategoryTotal(val label: String, val amountMinor: Long)

/** Бюджет категории и его заполнение за месяц */
private data class BudgetLine(val label: String, val spentMinor: Long, val budgetMinor: Long) {
    val percent: Int get() = if (budgetMinor <= 0L) 0 else (spentMinor * 100 / budgetMinor).toInt()
}

/** Сжатая выжимка месяца для промпта */
private data class MonthSnapshot(
    val incomeMinor: Long,
    val expenseMinor: Long,
    val categories: List<CategoryTotal>,
    val budgets: List<BudgetLine>,
)

/**
 * ViewModel секции «AI-анализ месяца»: собирает все транзакции месяца,
 * строит выжимку (топ категорий, итоги, бюджеты) и одним запросом спрашивает модель.
 */
class AiMonthAnalysisViewModel(private val repo: AppRepo) : ViewModel() {

    suspend fun analyze(ctx: Context, month: LocalDate): MonthAnalysis {
        val (from, to) = MoneyFormat.monthBounds(month)
        val txns = repo.txns.observeRange(from, to).first()
        val categories = repo.categories.observeFinance().first()

        val expenses = txns.filter { it.type == "EXPENSE" }
        val expenseMinor = expenses.sumOf { it.amountMinor }
        val incomeMinor = txns.filter { it.type == "INCOME" }.sumOf { it.amountMinor }
        if (expenseMinor <= 0L) return MonthAnalysis.NoData

        // Все траты месяца по категориям: имя → сумма, большие сверху
        val byCategory = expenses.groupBy { it.categoryId }
            .map { (categoryId, list) ->
                val cat = categories.firstOrNull { it.id == categoryId }
                CategoryTotal(
                    label = cat?.let { "${it.emoji} ${it.name}" } ?: "Прочее",
                    amountMinor = list.sumOf { it.amountMinor },
                )
            }
            .sortedByDescending { it.amountMinor }

        // Бюджеты категорий и % исчерпания (тот же источник, что в BudgetsSection)
        val budgets = categories
            .filter { (it.budgetMonthlyMinor ?: 0L) > 0L }
            .map { cat ->
                BudgetLine(
                    label = "${cat.emoji} ${cat.name}",
                    spentMinor = expenses.filter { it.categoryId == cat.id }.sumOf { it.amountMinor },
                    budgetMinor = cat.budgetMonthlyMinor ?: 0L,
                )
            }

        val snapshot = MonthSnapshot(incomeMinor, expenseMinor, byCategory, budgets)
        val monthTitleText = MoneyFormat.monthTitle(month)

        val answer = AiClient.chat(
            baseUrl = AiPrefs.baseUrl(ctx),
            apiKey = AiPrefs.apiKey(ctx),
            model = AiPrefs.model(ctx),
            messages = listOf(
                "system" to systemPrompt(monthTitleText, snapshot),
                "user" to "Разбери мои траты за $monthTitleText.",
            ),
        )

        val points = parsePoints(answer)
            .ifEmpty { return MonthAnalysis.Failure("Пустой ответ модели") }
        return MonthAnalysis.Done(points)
    }

    /** Системный промпт: инструкция + выжимка данных месяца */
    private fun systemPrompt(monthTitleText: String, s: MonthSnapshot): String = """
        Ты финансовый помощник. Проанализируй расходы пользователя за $monthTitleText.
        Данные:
        ${summaryText(s)}
        Верни 3-5 коротких практичных пунктов по одному на строку, БЕЗ markdown, каждый пункт максимум 140 символов, первый пункт — главный вывод о том, куда уходят деньги. Пиши по-дружески, без осуждения.
    """.trimIndent()

    /** Выжимка: итоги, топ категорий с %, бюджеты с заполнением */
    private fun summaryText(s: MonthSnapshot): String = buildString {
        append("Доходы: ${MoneyFormat.text(s.incomeMinor)}, расходы: ${MoneyFormat.text(s.expenseMinor)}.")
        if (s.categories.isNotEmpty()) {
            append("\nТоп категорий расходов:")
            s.categories.take(8).forEach { c ->
                val percent = c.amountMinor * 100 / s.expenseMinor
                append("\n- ${c.label}: ${MoneyFormat.text(c.amountMinor)} ($percent%)")
            }
        }
        if (s.budgets.isNotEmpty()) {
            append("\nБюджеты и их заполнение:")
            s.budgets.forEach { b ->
                append("\n- ${b.label}: ${MoneyFormat.text(b.spentMinor)} из ${MoneyFormat.text(b.budgetMinor)} (${b.percent}%)")
            }
        }
    }

    /** Ответ модели → чистые строки без буллетов/нумерации/заборов markdown */
    private fun parsePoints(raw: String): List<String> = raw
        .lines()
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("```") }
        .map { line ->
            line.replaceFirst(Regex("^[•\\-*–—]+\\s*"), "")
                .replaceFirst(Regex("^\\d{1,2}[.)]\\s*"), "")
                .trim()
        }
        .filter { it.isNotBlank() }
}

/**
 * «✨ AI-анализ месяца» на вкладке «Деньги»: AI разбирает все траты месяца
 * и даёт 3-5 практичных пунктов. Виден только при настроенном API-ключе.
 * Результат кэшируется в remember(monthKey) — смена месяца сбрасывает секцию;
 * повторный клик по кнопке перегенерирует разбор.
 */

/**
 * Контент таба «✨ AI»: если ключ не настроен — приглашение вместо пустоты,
 * иначе обычная секция разбора месяца.
 */
@Composable
fun AiMonthTabContent(month: LocalDate, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (!AiPrefs.isConfigured(context)) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        ) {
            Text(
                "✨ AI-анализ работает после вставки API-ключа.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Настройки → AI-коуч → вставь ключ OpenCode. Ключ хранится только на телефоне.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        AiMonthAnalysisSection(month, modifier)
    }
}

@Composable
fun AiMonthAnalysisSection(month: LocalDate, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (!AiPrefs.isConfigured(context)) return

    val vm: AiMonthAnalysisViewModel = viewModel { AiMonthAnalysisViewModel(AppRepo.get(context)) }
    val scope = rememberCoroutineScope()

    val monthKey = month.withDayOfMonth(1).toEpochDay()
    var busy by remember(monthKey) { mutableStateOf(false) }
    var result by remember(monthKey) { mutableStateOf<MonthAnalysis?>(null) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Q.surface,
        border = BorderStroke(1.dp, Q.border),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "✨ AI-анализ месяца",
                style = MaterialTheme.typography.titleMedium,
                color = Q.ink,
            )
            Text(
                text = "Разберу все траты за ${MoneyFormat.monthTitle(month)} и подскажу, куда уходят деньги.",
                style = MaterialTheme.typography.bodySmall,
                color = Q.inkMuted,
            )

            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    result = null
                    scope.launch {
                        result = try {
                            vm.analyze(context, month)
                        } catch (e: Exception) {
                            MonthAnalysis.Failure(e.message?.take(120) ?: "нет связи")
                        } finally {
                            busy = false
                        }
                    }
                },
            ) {
                Text(if (busy) "Анализирую…" else "Разобрать месяц")
            }

            when (val r = result) {
                is MonthAnalysis.Done -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    r.points.forEach { point ->
                        Text(
                            text = "• $point",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Q.ink,
                        )
                    }
                }
                MonthAnalysis.NoData -> Text(
                    text = "В этом месяце пока нет трат — анализировать нечего.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Q.inkMuted,
                )
                is MonthAnalysis.Failure -> Text(
                    text = "Не получилось: ${r.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Q.danger,
                )
                null -> Unit
            }
        }
    }
}
