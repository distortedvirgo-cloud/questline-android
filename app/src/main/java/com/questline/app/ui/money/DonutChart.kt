package com.questline.app.ui.money

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.questline.app.ui.theme.QColors
import com.questline.app.ui.theme.questlineQ

/**
 * Нейтральная палитра срезов диаграммы: осветлённые роли STYLE.md.
 * colorIndex категории указывает на индекс в этом наборе.
 */
internal fun slicePalette(q: QColors) = listOf(
    q.accent,          // 0
    q.success,         // 1
    q.warn,            // 2
    q.coin,            // 3
    q.danger,          // 4
    Color(0xFF7B86E8), // 5 — светло-акцентный
    Color(0xFF6699A8), // 6 — приглушённый морской
    Color(0xFF8F8F8F), // 7 — нейтральный серый
)

/**
 * Пончиковая диаграмма расходов: срезы по категориям, в центре —
 * сумма и подпись. Вынесена из OverviewSection (T-10) для переиспользования
 * на экране «Статистика».
 */
@Composable
fun DonutChart(slices: List<ExpenseSlice>, totalMinor: Long, modifier: Modifier = Modifier) {
    val palette = slicePalette(questlineQ())
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 34.dp.toPx()
            val diameter = minOf(size.width, size.height) - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)

            var startAngle = -90f
            slices.forEachIndexed { index, slice ->
                val sweep = slice.amountMinor.toFloat() / totalMinor.toFloat() * 360f
                drawArc(
                    color = palette[(slice.category?.colorIndex ?: 7) % palette.size],
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
                startAngle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = MoneyFormat.text(totalMinor),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "расходы",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
