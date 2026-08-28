package com.questline.app.ui.money

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.questline.app.data.AppRepo
import com.questline.app.ui.theme.Q
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

private val ACCOUNT_SHAPE = RoundedCornerShape(14.dp)

/**
 * Компактный верх вкладки «Деньги»: строка «Баланс: <сумма>» (≤44dp) и чипы
 * карт/счетов. Карт нет — тап открывает диалог «Сколько денег сейчас?» и сумма
 * считается по-старому (указанное значение + чистый поток операций с якоря);
 * карты заведены — общий баланс равен сумме их остатков и отдельно не правится,
 * тап по строке раскрывает карты (правится остаток каждой).
 */
@Composable
fun MoneyAccountsHeader(repo: AppRepo) {
    val context = LocalContext.current
    var accounts by remember { mutableStateOf(AccountsPrefs.list(context)) }
    var legacySet by remember { mutableStateOf(BalancePrefs.isSet(context)) }

    var showBalanceEdit by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Account?>(null) }
    var showAddCard by remember { mutableStateOf(false) }

    // Пуши могут обновить баланс карты (upsertBalance) из секции инбокса ниже —
    // перечитываем заведённые карты при возврате на экран.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) accounts = AccountsPrefs.list(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Инбокс меняет балансы карт вживую (подтверждение, сверка, перевод своей) —
    // перечитываем карты при любом изменении очереди pending.
    val pendingTick by repo.pending.observePending().collectAsStateWithLifecycle(emptyList())
    LaunchedEffect(pendingTick.map { it.id to it.status }) {
        accounts = AccountsPrefs.list(context)
    }

    // Legacy-баланс без карт: указанное значение + чистый поток с якоря
    var legacyMinor by remember { mutableStateOf(0L) }
    LaunchedEffect(legacySet) {
        if (!legacySet) return@LaunchedEffect
        legacyMinor = BalancePrefs.valueMinor(context)
        repo.txns.observeNetSince(BalancePrefs.anchorMillis(context)).collect { net ->
            legacyMinor = BalancePrefs.valueMinor(context) + net
        }
    }

    val totalMinor = if (accounts.isEmpty()) legacyMinor else accounts.sumOf { it.balanceMinor }

    fun reloadAccounts() {
        accounts = AccountsPrefs.list(context)
    }

    // Карты свёрнуты по умолчанию: сводка не должна съедать экран с тратами.
    var cardsExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Одна строка ≤44dp: «Баланс: <сумма> ✎   💳N/＋»
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp, max = 44.dp)
                .clip(ACCOUNT_SHAPE)
                .background(Q.accentSoft)
                .border(1.dp, Q.accent.copy(alpha = 0.35f), ACCOUNT_SHAPE)
                .clickable {
                    // С картами общий баланс — сумма остатков, правится только
                    // через карты; без карт — диалог ручного ввода.
                    if (accounts.isEmpty()) showBalanceEdit = true
                    else cardsExpanded = !cardsExpanded
                }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Баланс:",
                style = MaterialTheme.typography.labelMedium,
                color = Q.inkMuted,
            )
            Spacer(Modifier.width(6.dp))
            if (!legacySet && accounts.isEmpty()) {
                Text(
                    text = "укажи, сколько у тебя сейчас денег",
                    style = MaterialTheme.typography.bodySmall,
                    color = Q.accent,
                    maxLines = 1,
                )
            } else {
                Text(
                    text = MoneyFormat.text(totalMinor),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.weight(1f))
            // Карандаш уместен только там, где тап реально правит сумму вручную.
            if (accounts.isEmpty()) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "Указать баланс",
                    tint = Q.accent,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            // Триггер карт: «+» если карт нет, «💳 N ›/⌄» если есть. Клик не редактирует баланс.
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Q.surface)
                    .border(1.dp, Q.accent.copy(alpha = 0.35f), RoundedCornerShape(50))
                    .clickable { cardsExpanded = !cardsExpanded }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = if (accounts.isEmpty()) "+ Карта"
                    else "💳 ${accounts.size} " + if (cardsExpanded) "⌄" else "›",
                    style = MaterialTheme.typography.labelMedium,
                    color = Q.accent,
                    maxLines = 1,
                )
            }
        }

        androidx.compose.animation.AnimatedVisibility(visible = cardsExpanded) {
            Column {
                Spacer(Modifier.height(6.dp))
                AccountsBar(
                    accounts = accounts,
                    onEdit = { account -> editing = account },
                    onDelete = { account ->
                        AccountsPrefs.save(context, AccountsPrefs.list(context).filterNot { it.id == account.id })
                        reloadAccounts()
                    },
                    onAdd = { showAddCard = true },
                )
            }
        }
    }

    if (showBalanceEdit) {
        // Существующий диалог правки общего баланса из BalanceCard.kt
        BalanceEditDialog(
            currentValue = if (legacySet) legacyMinor else null,
            onDismiss = { showBalanceEdit = false },
            onSave = { newMinor ->
                BalancePrefs.save(context, newMinor)
                legacySet = true
                showBalanceEdit = false
            },
        )
    }

    editing?.let { account ->
        CardBalanceDialog(
            account = account,
            onDismiss = { editing = null },
            onSave = { newMinor, transferTarget ->
                AccountsPrefs.upsertBalance(context, account.id, newMinor, System.currentTimeMillis())
                // Перевод между своими: деньги, ушедшие с источника, приходят на
                // цель и наоборот — разница переносится на выбранную карту.
                if (transferTarget != null) {
                    val moved = account.balanceMinor - newMinor
                    AccountsPrefs.upsertBalance(
                        context,
                        transferTarget.id,
                        transferTarget.balanceMinor + moved,
                        System.currentTimeMillis(),
                    )
                }
                reloadAccounts()
                editing = null
            },
            onDelete = {
                AccountsPrefs.save(context, AccountsPrefs.list(context).filterNot { it.id == account.id })
                reloadAccounts()
                editing = null
            },
        )
    }

    if (showAddCard) {
        AddCardDialog(
            onDismiss = { showAddCard = false },
            onAdd = { name, last4, balanceMinor ->
                val account = Account(
                    id = System.currentTimeMillis(),
                    name = name,
                    last4 = last4,
                    balanceMinor = balanceMinor ?: 0L,
                    anchorMillis = System.currentTimeMillis(),
                )
                AccountsPrefs.save(context, AccountsPrefs.list(context) + account)
                reloadAccounts()
                showAddCard = false
            },
        )
    }
}

/** Лента чипов заведённых карт/счетов + компактный чип «+ Карта» (показывается всегда). */
@Composable
fun AccountsBar(
    accounts: List<Account>,
    onEdit: (Account) -> Unit,
    onDelete: (Account) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 44.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(accounts, key = { it.id }) { account ->
            AccountChip(
                account = account,
                onClick = { onEdit(account) },
                onLongClick = { onDelete(account) },
            )
        }
        item(key = "add_card") {
            AssistChip(
                onClick = onAdd,
                label = { Text("+ Карта") },
            )
        }
    }
}

/** Чип карты: «💳 ••5129  548,04 ₽» (с именем, если задано). Тап — правка, долгий тап — удалить. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountChip(account: Account, onClick: () -> Unit, onLongClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Q.accentSoft)
            .border(1.dp, Q.accent.copy(alpha = 0.35f), RoundedCornerShape(50))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = buildString {
                append("\uD83D\uDCB3 ")
                if (account.name.isNotBlank()) {
                    append(account.name.trim())
                    append(" ")
                }
                append("••")
                append(account.last4)
                append("  ")
                append(MoneyFormat.text(account.balanceMinor))
            },
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

/** Диалог правки баланса карты: ввод рублей, ✕ в заголовке — удалить карту. Есть режим «Перевод своей». */
@Composable
private fun CardBalanceDialog(
    account: Account,
    onDismiss: () -> Unit,
    onSave: (newMinor: Long, transferTarget: Account?) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var text by remember(account.id) { mutableStateOf(rublesInput(account.balanceMinor)) }
    val parsed = MoneyFormat.parseRubles(text)

    // Другие карты для режима «Перевод своей» (пусто — режим не показываем вовсе).
    val others = remember(account.id) {
        AccountsPrefs.list(context).filter { it.id != account.id }
    }
    var transferMode by remember(account.id) { mutableStateOf(false) }
    var targetId by remember(account.id) { mutableStateOf<Long?>(null) }
    val target = others.firstOrNull { it.id == targetId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = buildString {
                        if (account.name.isNotBlank()) {
                            append(account.name.trim())
                            append(" ")
                        }
                        append("••")
                        append(account.last4)
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Удалить карту",
                        tint = Q.accent,
                    )
                }
            }
        },
        text = {
            Column {
                Text(
                    "Укажи актуальный остаток по карте. Остаток из банковского пуша обновит его автоматически.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Q.inkMuted,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text("Сумма, ₽") },
                )
                if (others.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectableChip(
                            text = "Обновить",
                            selected = !transferMode,
                            onClick = { transferMode = false },
                            modifier = Modifier.weight(1f),
                        )
                        SelectableChip(
                            text = "Перевод своей",
                            selected = transferMode,
                            onClick = { transferMode = true },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (transferMode && others.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    others.forEach { candidate ->
                        SelectableChip(
                            text = "\uD83D\uDCB3 ••${candidate.last4}, остаток " +
                                MoneyFormat.text(candidate.balanceMinor),
                            selected = targetId == candidate.id,
                            onClick = {
                                targetId = if (targetId == candidate.id) null else candidate.id
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        "Разница между старым и новым остатком уйдёт на выбранную карту (или с неё), обе сойдутся.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Q.inkMuted,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null && !(transferMode && target == null),
                onClick = {
                    parsed?.let { minor -> onSave(minor, if (transferMode) target else null) }
                },
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

/**
 * Локальная копия SelectableChip из ui/tasks/AddTaskDialog.kt (тот internal и живёт
 * в другом пакете): нейтральная пилюля выбора, выбранная — surfaceAlt без второго цвета.
 */
@Composable
private fun SelectableChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    textPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Q.surfaceAlt else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Q.border),
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = textStyle,
            color = if (selected) Q.ink else Q.inkMuted,
            maxLines = 1,
            modifier = Modifier.padding(textPadding),
        )
    }
}

/** Диалог добавления карты/счёта: имя (необязательно), 4 цифры (обязательно), баланс (необязательно). */
@Composable
private fun AddCardDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, last4: String, balanceMinor: Long?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var last4 by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf("") }
    val balanceParsed = MoneyFormat.parseRubles(balance)
    val last4Valid = last4.length == 4

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая карта/счёт") },
        text = {
            Column {
                Text(
                    "Последние 4 цифры нужны, чтобы распознавать остаток в банковских пушах.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Q.inkMuted,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Название (необязательно)") },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = last4,
                    onValueChange = { input ->
                        if (input.length <= 4 && input.all(Char::isDigit)) last4 = input
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("Последние 4 цифры") },
                    supportingText = { Text("Например, 5129 из «•• 5129»") },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = balance,
                    onValueChange = { balance = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text("Текущий баланс, ₽ (необязательно)") },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = last4Valid,
                onClick = {
                    onAdd(
                        name.trim(),
                        last4,
                        if (balance.isBlank()) null else balanceParsed,
                    )
                },
            ) { Text("Добавить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

/** «548,04 ₽» → «548,04» — стартовое значение поля ввода рублей. */
private fun rublesInput(minor: Long): String =
    MoneyFormat.text(minor).replace(" ₽", "").replace('\u20BD', ' ').trim()
