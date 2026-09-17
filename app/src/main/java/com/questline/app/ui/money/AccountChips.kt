package com.questline.app.ui.money

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.questline.app.ui.theme.Q

/** Лента чипов заведённых карт/счетов + компактный чип «+ Карта» (показывается всегда). */
@Composable
fun AccountsBar(
    accounts: List<Account>,
    onEdit: (Account) -> Unit,
    onDelete: (Account) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    /** id карты → чистое движение её операций с якоря (динамическая часть баланса). */
    netsById: Map<Long, Long> = emptyMap(),
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
                displayedMinor = account.balanceMinor + (netsById[account.id] ?: 0L),
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
private fun AccountChip(
    account: Account,
    displayedMinor: Long,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
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
                append(MoneyFormat.text(displayedMinor))
            },
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}
