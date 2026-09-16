package com.questline.app.ui.profile

/* Радар 5 характеристик (Canvas): центр = слабость, к краю сила.
 * Вынесен из ProfileScreen.kt (лимит 300 строк, T-07).
 */
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.questline.app.ui.theme.Q

/** Радар характеристик (Canvas), центр = слабость к краю сила */
@Composable
fun RadarChart(keyXp: Map<String, Int>, modifier: Modifier = Modifier) {
    var animated by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animated = true }
    val fraction by animateFloatAsState(if (animated) 1f else 0f, tween(400), label = "radar")

    // Цвета захватываются до DrawScope: внутри Canvas @Composable недоступны.
    val borderColor = Q.border
    val accentColor = Q.accent

    Canvas(modifier) {
        val keys = listOf("PHYSICS", "MIND", "MONEY", "SOCIAL", "DISCIPLINE")
        val maxXp = (keyXp.values.maxOrNull() ?: 10).coerceAtLeast(1)
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = minOf(size.width, size.height) / 2f - 40f

        fun point(idx: Int, valueFraction: Float): Offset {
            val angle = Math.toRadians(-90.0 + idx * 72.0)
            val r = radius * valueFraction
            return Offset(center.x + (r * kotlin.math.cos(angle)).toFloat(), center.y + (r * kotlin.math.sin(angle)).toFloat())
        }

        // Сетка: 3 кольца
        for (ring in listOf(1 / 3f, 2 / 3f, 1f)) {
            val path = Path()
            keys.indices.forEach { i ->
                val p = point(i, ring)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close()
            drawPath(path, borderColor, style = Stroke(width = 1.dp.toPx()))
        }

        // Данные
        val dataPath = Path()
        keys.forEachIndexed { i, k ->
            val v = ((keyXp[k] ?: 0).toFloat() / maxXp).coerceIn(0.05f, 1f) * fraction
            val p = point(i, v)
            if (i == 0) dataPath.moveTo(p.x, p.y) else dataPath.lineTo(p.x, p.y)
        }
        dataPath.close()
        drawPath(dataPath, accentColor.copy(alpha = 0.22f))
        drawPath(dataPath, accentColor, style = Stroke(width = 2.dp.toPx()))

        // Вершины
        keys.forEachIndexed { i, k ->
            val v = ((keyXp[k] ?: 0).toFloat() / maxXp).coerceIn(0.05f, 1f) * fraction
            drawCircle(accentColor, radius = 5f, center = point(i, v))
        }
    }
}
