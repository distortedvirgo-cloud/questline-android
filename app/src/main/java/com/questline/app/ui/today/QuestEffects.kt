package com.questline.app.ui.today

/* Эффекты выполнения квеста: схлопывание карточки, всплывающий «+XP»
 * и конфетти из центра карточки. Только Compose-анимации, без зависимостей.
 */

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.questline.app.ui.theme.Q
import com.questline.app.ui.theme.questlineQ
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val COLLAPSE_MS = 300
private const val BURST_MS = 700
private const val CONFETTI_COUNT = 24
private const val COLLAPSE_SCALE = 0.92f // карточка сжимается 1.0 -> 0.92
private const val SPREAD_DP = 70f        // базовый радиус разлёта конфетти
private const val GRAVITY_DP = 48f       // довесок вниз к концу разлёта
private val XP_RISE = 48.dp              // высота всплытия «+XP»

/** Частица конфетти: параметры разлёта (сидируются quest.id). */
internal class Particle(
    val angleRad: Float,
    val speed: Float,
    val sizeDp: Float,
    val colorIndex: Int,
    val circle: Boolean,
    val spinDeg: Float,
)

/** Прогресс эффектов одного закрытия: читается карточкой (graphicsLayer) и оверлеем. */
class QuestBurstState(seed: Long) {

    private val random = Random(seed)

    /** 0..1 — схлопывание карточки: scale 1→0.92 и alpha 1→0 за COLLAPSE_MS. */
    val collapse = Animatable(0f)

    /** 0..1 — xp-полёт и конфетти за BURST_MS. */
    val burst = Animatable(0f)

    internal val particles: List<Particle> = List(CONFETTI_COUNT) {
        Particle(
            angleRad = random.nextFloat() * 2f * PI.toFloat(),
            speed = 0.6f + random.nextFloat() * 0.7f,
            sizeDp = 4f + random.nextFloat() * 2f, // 4..6dp
            colorIndex = random.nextInt(5),
            circle = random.nextBoolean(),
            spinDeg = (random.nextFloat() - 0.5f) * 720f,
        )
    }

    val cardScale: Float get() = 1f - (1f - COLLAPSE_SCALE) * collapse.value
    val cardAlpha: Float get() = 1f - collapse.value
    val xpAlpha: Float get() = 1f - burst.value

    suspend fun reset() {
        collapse.snapTo(0f)
        burst.snapTo(0f)
    }
}

/** Состояние эффектов карточки; seed = quest.id даёт предсказуемый разлёт. */
@Composable
fun rememberQuestBurstState(seed: Long = 0L): QuestBurstState =
    remember(seed) { QuestBurstState(seed = seed) }

/** Оверлей выполнения: конфетти из центра карточки + всплывающий «+XP».
 *  Пока visible, карточка снаружи гасится через state (cardScale/cardAlpha);
 *  по завершении BURST_MS вызывается onFinished (реальное закрытие квеста). */
@Composable
fun QuestCompletionOverlay(
    visible: Boolean,
    xpText: String,
    origin: Offset = Offset.Zero,
    onFinished: () -> Unit,
    state: QuestBurstState = rememberQuestBurstState(),
) {
    LaunchedEffect(visible) {
        if (!visible) {
            state.reset()
            return@LaunchedEffect
        }
        val collapseJob = launch { state.collapse.animateTo(1f, tween(COLLAPSE_MS)) }
        state.burst.animateTo(1f, tween(BURST_MS, easing = LinearEasing))
        collapseJob.join()
        onFinished()
    }
    if (!visible) return

    // Точка старта xp-текста фиксируется на момент старта эффекта.
    val start = remember(visible) { origin }
    val q = questlineQ()
    val palette = remember(q) { listOf(q.accent, q.success, q.coin, q.warn, q.danger) }

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.matchParentSize()) {
            val t = state.burst.value
            if (t <= 0f) return@Canvas
            val spread = SPREAD_DP.dp.toPx()
            val gravity = GRAVITY_DP.dp.toPx()
            val cx = size.width / 2f
            val cy = size.height / 2f
            state.particles.forEach { p ->
                val distance = spread * p.speed * t
                val x = cx + cos(p.angleRad) * distance
                val y = cy + sin(p.angleRad) * distance + gravity * t * t
                val s = p.sizeDp.dp.toPx()
                val color = palette[p.colorIndex % palette.size]
                val alpha = (1f - t).coerceIn(0f, 1f)
                if (p.circle) {
                    drawCircle(color, radius = s / 2f, center = Offset(x, y), alpha = alpha)
                } else {
                    rotate(degrees = p.spinDeg * t, pivot = Offset(x, y)) {
                        drawRect(color, topLeft = Offset(x - s / 2f, y - s / 2f), size = Size(s, s), alpha = alpha)
                    }
                }
            }
        }
        Text(
            xpText,
            modifier = Modifier
                .align(Alignment.TopStart)
                .graphicsLayer {
                    alpha = state.xpAlpha
                    translationX = start.x - size.width / 2f
                    translationY = start.y - size.height - XP_RISE.toPx() * state.burst.value
                },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Q.accent,
        )
    }
}
