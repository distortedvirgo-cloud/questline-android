package com.questline.app.ui.money

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.questline.app.data.AppRepo
import com.questline.app.ui.theme.Q
import com.questline.app.ui.theme.questlineQ
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Статистика (T-10): пончик расходов месяца, топ-3 с дельтами к прошлому
 * месяцу, тренд 12 месяцев столбиками, средние траты в день.
 */
@Composable
fun StatsScreen(onBack: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val vm: StatsViewModel = viewModel { StatsViewModel(AppRepo.get(context)) }

    val month by vm.month.collectAsState()
    val stats by vm.stats.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Назад")
            }
            Text(
                text = "Статистика",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
        }

        // Шапка месяца ‹ › — как на вкладке «Деньги»
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.shiftMonth(-1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Предыдущий месяц")
            }
            Text(
                text = MoneyFormat.monthTitle(month),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { vm.shiftMonth(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Следующий месяц")
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            if (stats.totalExpenseMinor <= 0L) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "В этом месяце расходов пока нет",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Q.inkMuted,
                )
            } else {
                Spacer(Modifier.height(12.dp))
                DonutChart(
                    slices = stats.slices,
                    totalMinor = stats.totalExpenseMinor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp),
                )

                Spacer(Modifier.height(16.dp))
                Text("Топ категорий", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                stats.top.forEach { delta ->
                    TopDeltaRow(delta = delta, totalMinor = stats.totalExpenseMinor)
                    Spacer(Modifier.height(6.dp))
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Тренд 12 месяцев", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            TrendBars(bars = stats.trend)

            Spacer(Modifier.height(20.dp))
            AverageRow(avgMinor = stats.avgPerDayMinor)
            Spacer(Modifier.height(88.dp))
        }
    }
}

/** Строка топ-категории: цветной маркер, доля, дельта к прошлому месяцу */
@Composable
private fun TopDeltaRow(delta: CategoryDelta, totalMinor: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .background(colorForIndex(delta.category?.colorIndex ?: 7), CircleShape),
        )
        Spacer(Modifier.padding(4.dp))
        Column(Modifier.weight(1f)) {
            Text(delta.category?.let { "${it.emoji} ${it.name}" } ?: "Прочее",
                style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "к прошлому месяцу ${delta.label}",
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    delta.deltaPercent == null -> Q.inkMuted
                    delta.deltaPercent!! > 0 -> Q.warn      // расходы выросли
                    else -> Q.success                        // расходы снизились
                },
            )
        }
        Text(
            text = "${MoneyFormat.text(delta.currentMinor)} · ${delta.currentMinor * 100 / totalMinor}%",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = Q.inkMuted,
        )
    }
}

/** Мини-столбики 12 месяцев: высота по доле максимума, подпись раз в 3 */
@Composable
private fun TrendBars(bars: List<TrendBar>) {
    val q = questlineQ()
    val maxMinor = bars.maxOfOrNull { it.expenseMinor }?.coerceAtLeast(1L) ?: 1L

    Column {
        Canvas(Modifier.fillMaxWidth().height(96.dp)) {
            val slot = size.width / bars.size
            val barWidth = slot * 0.6f
            bars.forEachIndexed { index, bar ->
                if (bar.expenseMinor <= 0L) return@forEachIndexed
                val h = size.height * (bar.expenseMinor.toFloat() / maxMinor)
                drawRoundRect(
                    color = if (index == bars.lastIndex) q.accent else q.accent.copy(alpha = 0.35f),
                    topLeft = Offset(index * slot + (slot - barWidth) / 2f, size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            bars.forEachIndexed { index, bar ->
                Text(
                    text = if (index % 3 == 0) monthShortLabel(bar.month) else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = Q.inkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** «Среднее траты в день» одной строкой с моноширинной суммой */
@Composable
private fun AverageRow(avgMinor: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Среднее траты в день", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(
            text = MoneyFormat.text(avgMinor),
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace,
        )
    }
}

/** «Янв», «Фев»… — короткое имя месяца для подписей тренда */
private fun monthShortLabel(month: LocalDate): String {
    val raw = DateTimeFormatter.ofPattern("LLL", Locale("ru")).format(month)
    return raw.trimEnd('.').replaceFirstChar { it.uppercase(Locale("ru")) }
}
