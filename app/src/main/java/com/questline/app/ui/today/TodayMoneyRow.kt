package com.questline.app.ui.today

/* Мини-деньги на «Сегодня» (T-06): тихая строка «Баланс: X ₽ · Траты сегодня: Y ₽»,
 * тап открывает вкладку «Деньги». Баланс — та же логика, что и в шапке «Денег»:
 * карты из AccountsPrefs (абсолют с якоря + чистое движение операций) либо
 * legacy-баланс BalancePrefs; траты дня — сумма EXPENSE-транзакций за текущий
 * epochDay. Композиция повторяет MoneyAccountsHeader компактно, без правок ui/money.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.questline.app.data.AppRepo
import com.questline.app.ui.money.AccountsPrefs
import com.questline.app.ui.money.BalancePrefs
import com.questline.app.ui.money.MoneyFormat
import com.questline.app.ui.theme.Q
import kotlinx.coroutines.flow.map

@Composable
internal fun TodayMoneyRow(repo: AppRepo, onClick: () -> Unit) {
    val context = LocalContext.current
    val today = AppRepo.todayEpochDay

    var accounts by remember { mutableStateOf(AccountsPrefs.list(context)) }
    var legacySet by remember { mutableStateOf(BalancePrefs.isSet(context)) }

    // Балансы карт правятся из инбокса/сверки на «Деньгах» — перечитываем при возврате.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accounts = AccountsPrefs.list(context)
                legacySet = BalancePrefs.isSet(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Legacy-баланс без карт: указанное значение + чистый поток операций с якоря
    var legacyMinor by remember { mutableStateOf(0L) }
    LaunchedEffect(legacySet, accounts.size) {
        if (!legacySet || accounts.isNotEmpty()) return@LaunchedEffect
        legacyMinor = BalancePrefs.valueMinor(context)
        repo.txns.observeNetSince(BalancePrefs.anchorMillis(context)).collect { net ->
            legacyMinor = BalancePrefs.valueMinor(context) + net
        }
    }

    // Динамический остаток карты = абсолют с якоря + чистое движение её операций
    val netsById = mutableMapOf<Long, Long>()
    accounts.forEach { acc ->
        val net by repo.txns.observeNetForAccount(acc.last4, acc.anchorMillis)
            .collectAsStateWithLifecycle(initialValue = 0L)
        netsById[acc.id] = net
    }

    val totalMinor = when {
        accounts.isNotEmpty() -> accounts.sumOf { it.balanceMinor + (netsById[it.id] ?: 0L) }
        legacySet -> legacyMinor
        else -> 0L
    }

    // Траты сегодня: сумма EXPENSE-транзакций текущего дня
    val spentToday by repo.txns.observeRange(today, today)
        .map { list -> list.filter { it.type == "EXPENSE" }.sumOf { it.amountMinor } }
        .collectAsStateWithLifecycle(initialValue = 0L)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CHIP_SHAPE)
            .background(Q.surfaceAlt)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (accounts.isEmpty() && !legacySet) "Баланс: укажи в «Деньгах»"
            else "Баланс: ${MoneyFormat.text(totalMinor)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(10.dp))
        Text("·", style = MaterialTheme.typography.bodyMedium, color = Q.inkMuted)
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Траты сегодня: ${MoneyFormat.text(spentToday)}",
            style = MaterialTheme.typography.bodyMedium,
            color = Q.inkMuted,
        )
        Spacer(Modifier.weight(1f))
        Text("›", style = MaterialTheme.typography.titleMedium, color = Q.inkMuted)
    }
}
