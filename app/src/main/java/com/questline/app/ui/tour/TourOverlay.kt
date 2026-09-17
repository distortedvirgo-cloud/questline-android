package com.questline.app.ui.tour

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.questline.app.ui.theme.questlineQ
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

private val HolePad = 8.dp      // насколько дырка шире цели
private val HoleRadius = 18.dp  // радиус углов дырки и рамки
private val HoleGap = 14.dp     // отступ карточки от дырки
private val ScreenEdge = 16.dp  // отступ карточки от края экрана
private const val GLOW_MID = 0.115f   // середина дыхания свечения (0.05..0.18)
private const val GLOW_AMP = 0.065f

/** Оверлей экскурсии: скрим с дыркой вокруг цели, пульсар и карточка шага. */
@Composable
fun TourOverlay(
    tour: TourController,
    onSwitchTab: (String) -> Unit,
) {
    // Системный BACK закрывает тур целиком
    BackHandler { tour.finish() }

    val step = tour.step
    // Цель не готова, пока её Rect не зафиксирован (нулевой прямоугольник
    // промежуточной раскладки не считаем): карточка показывается после готовности
    var ready by remember(step.id) { mutableStateOf(step.targetId == null) }

    // Ожидание регистрации цели: сначала возможное переключение вкладки
    LaunchedEffect(step.id) {
        if (step.tab != null) {
            onSwitchTab(step.tab)
            delay(450)
        }
        val targetId = step.targetId ?: return@LaunchedEffect
        val budget = if (step.tab != null) 1500L else 800L
        val deadline = System.currentTimeMillis() + budget
        while (System.currentTimeMillis() < deadline) {
            val r = TourRegistry.get(targetId)
            if (r != null && r.width > 0f && r.height > 0f) break
            delay(60)
        }
        ready = true
    }

    // Мягкое появление поверх приложения
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(animationSpec = tween(220)),
        exit = fadeOut(animationSpec = tween(220)),
    ) {
        // Живое чтение реестра: Rect обновляется сам при долейауте цели
        val holeRect = step.targetId
            ?.let { TourRegistry.get(it) }
            ?.takeIf { it.width > 0f && it.height > 0f }
        if (ready) {
            TourContent(tour = tour, step = step, holeRect = holeRect)
        } else {
            TourScrim(holeRect = null)
        }
    }
}

/** Скрим с вырезом: DstOut по прямоугольнику цели, раздутому на 8dp. */
@Composable
private fun TourScrim(holeRect: Rect?) {
    val q = questlineQ()
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        drawRect(color = q.ink.copy(alpha = 0.88f))
        holeRect?.let { r ->
            val pad = HolePad.toPx()
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(r.left - pad, r.top - pad),
                size = Size(r.width + pad * 2, r.height + pad * 2),
                cornerRadius = CornerRadius(HoleRadius.toPx(), HoleRadius.toPx()),
                blendMode = BlendMode.DstOut,
            )
        }
    }
}

@Composable
private fun TourContent(
    tour: TourController,
    step: TourStep,
    holeRect: Rect?,
) {
    val q = questlineQ()
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    var cardHeightPx by remember { mutableIntStateOf(0) }

    // Дыхание пульсара ~1.4s по синусоиде
    val pulse = rememberInfiniteTransition(label = "tourPulse")
    val phase by pulse.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "tourPulsePhase",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // Оверлей съедает все тапы: приложение под ним недоступно
            .clickable(interactionSource = interaction, indication = null, onClick = {}),
    ) {
        // Скрим с вырезом вокруг цели
        TourScrim(holeRect = holeRect)

        // Рамка-пульсар: мягкое свечение 6dp + чёткая линия 2dp
        Canvas(Modifier.fillMaxSize()) {
            holeRect?.let { r ->
                val pad = HolePad.toPx()
                val breath = sin(phase)
                val topLeft = Offset(r.left - pad, r.top - pad)
                val holeSize = Size(r.width + pad * 2, r.height + pad * 2)
                val radius = CornerRadius(HoleRadius.toPx(), HoleRadius.toPx())
                drawRoundRect(
                    color = q.accent.copy(alpha = GLOW_MID + GLOW_AMP * breath),
                    topLeft = topLeft,
                    size = holeSize,
                    cornerRadius = radius,
                    style = Stroke(width = 6.dp.toPx()),
                )
                drawRoundRect(
                    color = q.accent.copy(alpha = 0.85f + 0.15f * breath),
                    topLeft = topLeft,
                    size = holeSize,
                    cornerRadius = radius,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }

        // Карточка: под дыркой, при нехватке места — над ней; без дырки — по центру
        val gapPx = with(density) { HoleGap.roundToPx() }
        val edgePx = with(density) { ScreenEdge.roundToPx() }
        val screenH = constraints.maxHeight
        val rect = holeRect
        val cardModifier = when (rect) {
            null -> Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = ScreenEdge)
            else -> {
                val belowY = rect.bottom + gapPx
                val fitsBelow = belowY + cardHeightPx + edgePx <= screenH
                val aboveY = rect.top - gapPx - cardHeightPx
                val y = if (fitsBelow) belowY else maxOf(aboveY, edgePx.toFloat())
                Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(0, y.toInt()) }
                    .fillMaxWidth()
                    .padding(horizontal = ScreenEdge)
            }
        }
        TourCard(
            step = step,
            index = tour.index,
            total = TOUR_STEPS.size,
            isLast = tour.isLast,
            onNext = { tour.next() },
            onSkip = { tour.finish() },
            modifier = cardModifier.onGloballyPositioned { cardHeightPx = it.size.height },
        )
    }
}
