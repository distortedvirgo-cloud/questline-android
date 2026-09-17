package com.questline.app.ui.onboarding

/* Онбординг v3 (N-03): полноэкранный, без нижней навигации. Три шага —
 * манифест, выбор 1–3 стартеров, финал. Показывается только новой установке
 * (решение — OnboardingGate); флаг onboarding_v3_done пишется при любом
 * способе закрытия: «Начать», «Позже», BACK на первом шаге.
 */

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.questline.app.data.AppRepo
import com.questline.app.domain.advice.StarterHabit
import com.questline.app.ui.theme.Q
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var limitHint by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<StarterHabit>() }
    val created = remember { mutableStateListOf<StarterHabit>() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun close() {
        OnboardingGate.markDone(context)
        onFinish()
    }

    fun toggle(starter: StarterHabit) {
        when {
            starter in selected -> {
                selected.remove(starter)
                limitHint = false
            }
            selected.size < 3 -> {
                selected.add(starter)
                limitHint = false
            }
            else -> limitHint = true // мягкая подсказка вместо четвёртой карточки
        }
    }

    fun proceedFromChoice() {
        if (creating) return
        creating = true
        scope.launch {
            val repo = AppRepo.get(context)
            val today = AppRepo.todayEpochDay
            val picked = selected.toList()
            picked.forEach { repo.upsertHabit(it.toHabit(today)) } // по одной
            created.clear()
            created.addAll(picked)
            selected.clear()
            creating = false
            step = 2
        }
    }

    // BACK: шаг назад внутри онбординга; на первом шаге — закрыть онбординг
    // (флаг ставится, чтобы BACK не возвращал на онбординг при каждом запуске).
    BackHandler {
        if (step > 0) step -= 1 else close()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Q.bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(20.dp))
            StepDots(step)
            Spacer(Modifier.height(28.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            when (step) {
                0 -> ManifestStep()
                1 -> StarterPickSection(selected.toList(), limitHint, ::toggle)
                else -> FinalStep(created.toList())
            }
        }
        BottomNav(
            step = step,
            onBack = { step -= 1 },
            onNext = { if (step == 0) step = 1 else proceedFromChoice() },
            onStart = ::close,
            onLater = ::close,
        )
    }
}

@Composable
private fun StepDots(step: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (i == step) Q.accent else Q.border),
            )
        }
    }
}

/** Шаг 1 «Идея»: манифест + правило 2 минут + мягкое «не пропускай дважды». */
@Composable
private fun ManifestStep() {
    Column {
        Text("Идея", style = MaterialTheme.typography.labelMedium, color = Q.inkMuted)
        Spacer(Modifier.height(10.dp))
        Text(
            "Каждый день —\nхоть немного,\nно лучше.",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "Начни с версии на 2 минуты. Серия важнее объёма.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Крошечный шаг сегодня стоит больше, чем рывок раз в неделю.",
            style = MaterialTheme.typography.bodyMedium,
            color = Q.inkMuted,
        )
        Spacer(Modifier.height(24.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Q.accentSoft)
                .padding(14.dp),
        ) {
            Text(
                "Не пропускай дважды",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Q.accent,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Пропуск — не катастрофа. Просто вернись назавтра.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Шаг 3 «Готово»: расставленные привычки — и встреча завтра в «Сегодня». */
@Composable
private fun FinalStep(created: List<StarterHabit>) {
    Column {
        Text("Готово", style = MaterialTheme.typography.labelMedium, color = Q.inkMuted)
        Spacer(Modifier.height(10.dp))
        Text(
            "Увидимся завтра. Привычки ждут тебя в «Сегодня».",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(18.dp))
        when {
            created.isEmpty() -> Text(
                "Начнёшь и без привычек — загляни в «Привычки», когда захочется.",
                style = MaterialTheme.typography.bodyMedium,
                color = Q.inkMuted,
            )
            else -> {
                Text(
                    if (created.size == 1) "Твоя первая привычка:" else "Теперь у тебя ${created.size} привычки:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                created.forEach { starter ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Q.surfaceAlt)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(starter.emoji, fontSize = 16.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(starter.title, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun BottomNav(
    step: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onStart: () -> Unit,
    onLater: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) {
                TextButton(onClick = onBack) { Text("Назад") }
            } else {
                Spacer(Modifier.width(0.dp))
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = if (step == 2) onStart else onNext,
                modifier = Modifier.width(148.dp).height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(if (step == 2) "Начать" else "Далее")
            }
        }
        if (step == 1) {
            TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) {
                Text("Позже — без привычек", color = Q.inkMuted)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
