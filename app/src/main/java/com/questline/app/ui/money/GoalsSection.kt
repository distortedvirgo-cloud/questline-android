package com.questline.app.ui.money

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.questline.app.data.AppRepo
import com.questline.app.data.Goal
import com.questline.app.domain.finance.goalPace
import com.questline.app.ui.theme.Q
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val DAY_MILLIS = 24L * 60 * 60 * 1000
private const val PACE_WINDOW_DAYS = 30L
private const val PACE_RECENT_LIMIT = 500

/** Каждый взнос даёт малые монеты и XP (SPEC v3: пополнение = монеты + XP) */
private const val DEPOSIT_COINS = 2
private const val DEPOSIT_XP = 5
private const val REASON_GOAL_DEPOSIT = "GOAL_DEPOSIT"
private const val XP_SOURCE_GOAL = "GOAL"

/** Маркер новой копилки для редактора (id = 0 → вставка) */
private val NEW_GOAL = Goal(name = "", targetMinor = 0)

class GoalsViewModel(private val repo: AppRepo) : ViewModel() {

    val goals = repo.goals.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Прогноз темпа: goalId → дней до цели (null — темпа ещё нет).
     * Взносы узнаются в CoinsLedger по записям GOAL_DEPOSIT с refId цели
     * и бонусом [DEPOSIT_COINS] (бонус +25 за достижение фильтруется отдельно);
     * средний взнос = savedMinor / число взносов, темп — по взносам за 30 дней.
     */
    val paceDays: StateFlow<Map<Long, Long?>> = goals.map { list ->
        if (list.isEmpty()) {
            emptyMap()
        } else {
            val recent = repo.coins.observeRecent(PACE_RECENT_LIMIT).first()
            val since = System.currentTimeMillis() - PACE_WINDOW_DAYS * DAY_MILLIS
            list.associate { goal ->
                val deposits = recent.filter {
                    it.reason == REASON_GOAL_DEPOSIT && it.refId == goal.id && it.delta == DEPOSIT_COINS
                }
                val inWindow = deposits.count { it.createdAtMillis >= since }
                val avgMinor = if (deposits.isEmpty()) 0L else goal.savedMinor / deposits.size
                goal.id to goalPace(goal.savedMinor, goal.targetMinor, avgMinor * inWindow)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Внести сумму в копилку. Каждый взнос — монеты и XP через журнал;
     * если цель достигнута впервые — статус DONE и бонус 25 монет
     * (GOAL_DEPOSIT), по упрощённой схеме: только порог 100%.
     */
    fun deposit(goalId: Long, amountMinor: Long) {
        if (amountMinor <= 0L) return
        viewModelScope.launch {
            val goal = repo.goals.observeActive().first().firstOrNull { it.id == goalId } ?: return@launch
            val newSaved = goal.savedMinor + amountMinor
            val wasReached = goal.savedMinor >= goal.targetMinor
            val isReached = newSaved >= goal.targetMinor
            repo.goals.update(
                goal.copy(
                    savedMinor = newSaved,
                    status = if (isReached) "DONE" else goal.status,
                ),
            )
            repo.addCoins(DEPOSIT_COINS, REASON_GOAL_DEPOSIT, goal.id)
            repo.awardXp(XP_SOURCE_GOAL, goal.id, DEPOSIT_XP, AppRepo.todayEpochDay)
            if (!wasReached && isReached) {
                repo.addCoins(25, REASON_GOAL_DEPOSIT, goal.id)
            }
        }
    }

    /** Создать или обновить цель (редактор карточки) */
    fun upsertGoal(goal: Goal) {
        viewModelScope.launch {
            if (goal.id == 0L) repo.goals.insert(goal) else repo.goals.update(goal)
        }
    }

    /** Полное удаление цели по подтверждению */
    fun deleteGoal(goalId: Long) {
        viewModelScope.launch { repo.goals.delete(goalId) }
    }
}

/** Копилки: цели с прогрессом, темпом к дате и пополнением (+монеты/+XP) */
@Composable
fun GoalsSection(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val vm: GoalsViewModel = viewModel { GoalsViewModel(AppRepo.get(context)) }

    val goals by vm.goals.collectAsState()
    val paceDays by vm.paceDays.collectAsState()

    var depositing by remember { mutableStateOf<Goal?>(null) }
    var editing by remember { mutableStateOf<Goal?>(null) }

    GoalsScrollColumn(modifier = modifier) {
        if (goals.isEmpty()) {
            Text(
                text = "Копилок нет — накопи на что-то важное",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            NewGoalButton { editing = NEW_GOAL }
            Spacer(Modifier.height(16.dp))
        } else {
            goals.forEach { goal ->
                GoalCard(
                    goal = goal,
                    paceDays = paceDays[goal.id],
                    onDeposit = { depositing = goal },
                    onOpen = { editing = goal },
                )
                Spacer(Modifier.height(8.dp))
            }
            NewGoalButton { editing = NEW_GOAL }
        }
    }

    depositing?.let { goal ->
        DepositDialog(
            goal = goal,
            onDismiss = { depositing = null },
            onDeposit = { minor ->
                vm.deposit(goal.id, minor)
                depositing = null
            },
        )
    }

    editing?.let { selected ->
        GoalEditorDialog(
            goal = selected,
            onDismiss = { editing = null },
            onSave = { updated ->
                vm.upsertGoal(updated)
                editing = null
            },
            onDelete = {
                vm.deleteGoal(selected.id)
                editing = null
            },
        )
    }
}

@Composable
private fun GoalCard(
    goal: Goal,
    paceDays: Long?,
    onDeposit: () -> Unit,
    onOpen: () -> Unit,
) {
    val fraction = if (goal.targetMinor <= 0L) 1f
    else (goal.savedMinor.toFloat() / goal.targetMinor).coerceIn(0f, 1f)
    val percent = (fraction * 100).roundToInt()
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 200),
        label = "goalProgress",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(goal.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "${MoneyFormat.text(goal.savedMinor)} из ${MoneyFormat.text(goal.targetMinor)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onDeposit, enabled = goal.status != "DONE") {
                Text(if (goal.status == "DONE") "Цель достигнута" else "Внести")
            }
        }

        Spacer(Modifier.height(10.dp))

        LinearProgressIndicator(
            progress = { animatedFraction },
            color = Q.accent,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
        )

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = paceText(goal, paceDays),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = if (goal.status == "DONE") Q.success else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Строка темпа: цель всегда позитив — без warn/danger, тихо без данных */
private fun paceText(goal: Goal, paceDays: Long?): String {
    if (goal.status == "DONE") return "Цель достигнута"
    if (paceDays == null) return "Темп копится"
    return when {
        paceDays < 14L -> "При текущем темпе закроется через ~$paceDays дн"
        paceDays < 365L -> "При текущем темпе закроется через ~${ceil(paceDays / 7.0).toLong()} нед"
        else -> "При текущем темпе закроется больше чем через год"
    }
}

/** Локальная скролл-обёртка секции (MoneyScreen.kt переписывается параллельно) */
@Composable
private fun GoalsScrollColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        content()
        Spacer(Modifier.height(88.dp)) // воздух над FAB
    }
}
