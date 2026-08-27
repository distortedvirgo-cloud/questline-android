package com.questline.app.ui.today

/* Главный экран «Сегодня»: сводка дня одним скроллом.
 * Приветствие по времени суток + уровень/шкала XP, страйк и монеты,
 * квесты дня с пульсом при закрытии, свои задачи на сегодня с чекбоксами.
 */

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.questline.app.data.AppRepo
import com.questline.app.data.Quest
import com.questline.app.data.Task
import com.questline.app.domain.ProgressionEngine
import com.questline.app.domain.QuestGenerator
import com.questline.app.ui.theme.Q
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

private const val PULSE_HALF_MS = 125 // половина пульса: 125*2 = 250 мс
private const val SCALE_FILL_MS = 250
private const val PULSE_PEAK = 1.06f
private const val MAX_TODAY_TASKS = 5

private val CARD_SHAPE = RoundedCornerShape(16.dp)
private val BAR_SHAPE = RoundedCornerShape(50)
private val CHIP_SHAPE = RoundedCornerShape(12.dp)
@Composable
private fun cardColors() = CardDefaults.outlinedCardColors(containerColor = Q.surface)

/** Цифры крупной суммы XP — табличные цифры, крупный размер. */
private val SUM_NUMERAL_STYLE = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")

/** Приветствие по времени суток. */
private fun dayGreeting(now: LocalTime): String = when (now.hour) {
    in 5..11 -> "Доброе утро"
    in 12..17 -> "Добрый день"
    else -> "Добрый вечер"
}

/** Одноразовый срез прогресса: всегда вычисляется из истории закрытых квестов. */
data class ProgressSnapshot(
    val level: Int = 1,
    val xpIntoLevel: Int = 0,
    val xpNeeded: Int = 120,
    val totalXp: Int = 0,
    val streakDays: Int = 0,
) {
    val fraction: Float get() = if (xpNeeded <= 0) 0f else xpIntoLevel.toFloat() / xpNeeded
}

class TodayViewModel(private val repo: AppRepo) : ViewModel() {

    private val todayEpochDay: Long = AppRepo.todayEpochDay

    /** Открытые авто-квесты сегодняшнего дня. */
    val questsOfDay: StateFlow<List<Quest>> = repo.quests.observeOpen()
        .map { list -> list.filter { it.source == "AUTO" && it.dateCreatedEpochDay == todayEpochDay } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Задачи на сегодня — максимум 5 строк. */
    val tasksToday: StateFlow<List<Task>> = repo.tasks.observeForToday(todayEpochDay)
        .map { list -> list.take(MAX_TODAY_TASKS) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Общий баланс монет. */
    val coins: StateFlow<Int> = repo.coins.observeTotalCoins()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** questKey -> эмодзи характеристики (сидируемые категории + запасные значения). */
    val keyEmoji: StateFlow<Map<String, String>> = repo.categories.observeQuest()
        .map { list ->
            val fallback = mapOf("PHYSICS" to "💪", "MIND" to "🧠", "MONEY" to "💰", "SOCIAL" to "💬", "DISCIPLINE" to "🎯")
            fallback + list.mapNotNull { c -> c.questKey?.let { it to c.emoji } }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Квесты, по которым сейчас идёт пульс/закрытие (защита от двойного тапа). */
    private val _busyQuestIds = MutableStateFlow<Set<Long>>(emptySet())
    val busyQuestIds: StateFlow<Set<Long>> = _busyQuestIds.asStateFlow()

    private val _progress = MutableStateFlow<ProgressSnapshot?>(null)
    val progress: StateFlow<ProgressSnapshot?> = _progress.asStateFlow()

    /** Один раз собрать все DONE-квесты: уровень, XP, стрик. */
    fun loadProgressOnce() {
        viewModelScope.launch { _progress.value = buildProgressSnapshot() }
    }

    fun markQuestBusy(id: Long) = _busyQuestIds.update { it + id }
    fun releaseQuestBusy(id: Long) = _busyQuestIds.update { it - id }

    /** Закрыть квест и пересчитать прогресс (XP и стрик растут из новой записи DONE). */
    fun completeQuest(quest: Quest) {
        viewModelScope.launch {
            repo.completeQuest(quest)
            _progress.value = buildProgressSnapshot()
        }
    }

    /** Чекбокс задачи: закрытие создаёт USER-квест с XP (кор-луп); снятие — просто откат статуса. */
    fun toggleTask(task: Task, checked: Boolean) {
        viewModelScope.launch {
            if (checked) {
                repo.completeTaskAsQuest(task)
                _progress.value = buildProgressSnapshot()
            } else if (task.repeatDaily) {
                repo.tasks.update(task.copy(lastDoneEpochDay = null))
            } else {
                repo.tasks.update(task.copy(done = false, doneAtMillis = null))
            }
        }
    }

    private suspend fun buildProgressSnapshot(): ProgressSnapshot {
        val done = repo.quests.allDone()
        val closedDays = done.mapNotNull { q ->
            q.closedAtMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay() }
        }.toSet()
        val state = ProgressionEngine.levelFromTotal(ProgressionEngine.totalXp(done))
        return ProgressSnapshot(
            level = state.level,
            xpIntoLevel = state.xpIntoLevel,
            xpNeeded = state.xpNeeded,
            totalXp = state.totalXp,
            streakDays = ProgressionEngine.currentStreak(closedDays, todayEpochDay),
        )
    }
}

// ------------------ Экран ------------------

@Composable
fun TodayScreen() {
    val context = LocalContext.current
    val repo = remember { AppRepo.get(context) }
    val vm: TodayViewModel = viewModel(initializer = { TodayViewModel(repo) })

    // Генерация авто-квестов дня + первичный расчёт уровня/стрика.
    LaunchedEffect(Unit) {
        QuestGenerator.ensureTodayQuests(repo, AppRepo.todayEpochDay)
        com.questline.app.domain.BudgetQuestEngine.ensureBudgetQuests(repo, AppRepo.todayEpochDay)
        com.questline.app.domain.BudgetQuestEngine.resolveBudgetQuests(repo, AppRepo.todayEpochDay)
        vm.loadProgressOnce()
    }

    val quests by vm.questsOfDay.collectAsStateWithLifecycle()
    val tasks by vm.tasksToday.collectAsStateWithLifecycle()
    val coins by vm.coins.collectAsStateWithLifecycle()
    val keyEmoji by vm.keyEmoji.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val busyIds by vm.busyQuestIds.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
    ) {
        Text(dayGreeting(LocalTime.now()), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        ProgressCard(progress ?: ProgressSnapshot())
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(Modifier.weight(1f), emoji = "🔥", value = "${progress?.streakDays ?: 0} дней")
            StatCard(Modifier.weight(1f), emoji = "🪙", value = "$coins монет")
        }
        Spacer(Modifier.height(20.dp))

        if (quests.isNotEmpty()) {
            Text("Квест дня", style = MaterialTheme.typography.titleMedium, color = Q.inkMuted)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                quests.forEach { quest ->
                    QuestCard(quest, emoji = keyEmoji[quest.questKey].orEmpty(), busy = quest.id in busyIds, vm = vm)
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        Text("Мои задачи на сегодня", style = MaterialTheme.typography.titleMedium, color = Q.inkMuted)
        Spacer(Modifier.height(8.dp))
        TasksSection(tasks, vm)
    }
}

// ------------------ Карточки ------------------

@Composable
private fun ProgressCard(snapshot: ProgressSnapshot) {
    val fillFraction by animateFloatAsState(
        targetValue = snapshot.fraction.coerceIn(0f, 1f),
        animationSpec = tween(SCALE_FILL_MS),
        label = "xpScaleFill",
    )
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = CARD_SHAPE, colors = cardColors()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Уровень ${snapshot.level}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("${snapshot.totalXp} XP", style = SUM_NUMERAL_STYLE, color = Q.accent)
            }
            Spacer(Modifier.height(10.dp))
            XpBar(fillFraction)
            Spacer(Modifier.height(8.dp))
            Text(
                "${snapshot.xpIntoLevel} / ${snapshot.xpNeeded} XP до уровня ${snapshot.level + 1}",
                style = MaterialTheme.typography.bodySmall,
                color = Q.inkMuted,
            )
        }
    }
}

@Composable
private fun XpBar(fraction: Float) {
    Box(Modifier.fillMaxWidth().height(10.dp).clip(BAR_SHAPE).background(Q.surfaceAlt)) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(BAR_SHAPE).background(Q.accent))
    }
}

@Composable
private fun StatCard(modifier: Modifier = Modifier, emoji: String, value: String) {
    OutlinedCard(modifier = modifier, shape = CARD_SHAPE, colors = cardColors()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(emoji, fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun QuestCard(quest: Quest, emoji: String, busy: Boolean, vm: TodayViewModel) {
    val pulse = remember(quest.id) { Animatable(1f) }
    val scope = rememberCoroutineScope()

    fun close() {
        if (busy) return
        scope.launch {
            try {
                vm.markQuestBusy(quest.id)
                pulse.animateTo(PULSE_PEAK, tween(PULSE_HALF_MS))
                vm.completeQuest(quest)
                pulse.animateTo(1f, tween(PULSE_HALF_MS))
            } finally {
                vm.releaseQuestBusy(quest.id)
            }
        }
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = pulse.value; scaleY = pulse.value },
        shape = CARD_SHAPE,
        colors = cardColors(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CHIP_SHAPE).background(Q.surfaceAlt), contentAlignment = Alignment.Center) {
                Text(emoji, fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(quest.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                Spacer(Modifier.height(2.dp))
                Text("+${quest.xpReward} XP", style = MaterialTheme.typography.bodySmall, color = Q.accent)
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = { close() },
                enabled = !busy,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text("Выполнить", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun TasksSection(tasks: List<Task>, vm: TodayViewModel) {
    if (tasks.isEmpty()) {
        OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = CARD_SHAPE, colors = cardColors()) {
            Text(
                "Задач на сегодня нет. Добавь в разделе Задачи",
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Q.inkMuted,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = CARD_SHAPE, colors = cardColors()) {
        Column {
            tasks.forEachIndexed { index, task ->
                if (index > 0) HorizontalDivider(color = Q.border, thickness = 1.dp)
                TaskRow(task, vm)
            }
        }
    }
}

@Composable
private fun TaskRow(task: Task, vm: TodayViewModel) {
    val checked = task.repeatDaily && task.lastDoneEpochDay == AppRepo.todayEpochDay
    Row(modifier = Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { vm.toggleTask(task, it) })
        Text(
            task.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (checked) Q.inkMuted else Q.ink,
            textDecoration = if (checked) TextDecoration.LineThrough else null,
            maxLines = 2,
        )
    }
}
