package com.questline.app.ui.money

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.questline.app.data.AppRepo
import com.questline.app.data.Goal
import com.questline.app.ui.theme.Q
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GoalsViewModel(private val repo: AppRepo) : ViewModel() {

    val goals = repo.goals.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Внести сумму в копилку. Если цель достигнута впервые —
     * статус DONE и бонус 25 монет (GOAL_DEPOSIT), по упрощённой схеме:
     * только порог 100%.
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
            if (!wasReached && isReached) {
                repo.addCoins(25, "GOAL_DEPOSIT", goal.id)
            }
        }
    }
}

/** Копилки: активные цели с прогрессом внесённого к целевой сумме */
@Composable
fun GoalsSection(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val vm: GoalsViewModel = viewModel { GoalsViewModel(AppRepo.get(context)) }

    val goals by vm.goals.collectAsState()

    var depositing by remember { mutableStateOf<Goal?>(null) }

    SectionColumn(modifier = modifier) {
        if (goals.isEmpty()) {
            Text(
                text = "Копилок пока нет",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        goals.forEach { goal ->
            GoalCard(goal = goal, onDeposit = { depositing = goal })
            Spacer(Modifier.height(8.dp))
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
}

@Composable
private fun GoalCard(goal: Goal, onDeposit: () -> Unit) {
    val fraction = if (goal.targetMinor <= 0L) 1f
    else (goal.savedMinor.toFloat() / goal.targetMinor).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
        label = "goalProgress",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
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
    }
}

@Composable
private fun DepositDialog(
    goal: Goal,
    onDismiss: () -> Unit,
    onDeposit: (Long) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val minor = MoneyFormat.parseRubles(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Внести в «${goal.name}»") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    text = input.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }
                },
                label = { Text("Сумма") },
                suffix = { Text("\u20BD") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                isError = text.isNotBlank() && minor == null,
                supportingText = {
                    Text(
                        text = "Осталось ${MoneyFormat.text((goal.targetMinor - goal.savedMinor).coerceAtLeast(0L))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        },
        confirmButton = {
            Button(
                enabled = minor != null && minor > 0L,
                onClick = { onDeposit(minor ?: return@Button) },
            ) { Text("Внести") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
