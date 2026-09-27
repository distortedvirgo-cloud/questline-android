package com.questline.app.ui.shop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.questline.app.data.AppRepo
import com.questline.app.ui.theme.Q
import kotlinx.coroutines.launch

/** Магазин тем акцента: баланс монет и карточки тем (применить/купить). */
@Composable
fun ShopScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { AppRepo.get(context) }
    LaunchedEffect(Unit) { ThemeState.load(context) }

    val coins by repo.coins.observeTotalCoins().collectAsStateWithLifecycle(initialValue = 0)
    val owned = remember(coins) { ThemeState.ownedSet(context) }

    // Покупка/применение празднуются: вибро-тик и подпись «Куплено и применено ✨»
    val haptics = LocalHapticFeedback.current
    var justAppliedId by remember { mutableStateOf<String?>(null) }
    // Покупка в процессе: двойной тап не должен списать монеты дважды
    var purchaseBusy by remember { mutableStateOf(false) }

    Scaffold(containerColor = Q.bg, topBar = { ShopTopBar(onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Text(
                "🪙 $coins",
                style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                color = Q.coin,
            )
            ThemeState.themes.forEachIndexed { index, theme ->
                ThemeCard(
                    theme = theme,
                    coins = coins,
                    owned = owned,
                    isApplied = ThemeState.selectedIndex == index,
                    justApplied = justAppliedId == theme.id,
                    busy = purchaseBusy,
                    onChoose = {
                        if (purchaseBusy) return@ThemeCard
                        scope.launch {
                            purchaseBusy = true
                            try {
                                if (ThemeState.buy(context, theme)) {
                                    ThemeState.selectedIndex = index
                                    ThemeState.persist(context)
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    justAppliedId = theme.id
                                }
                            } finally {
                                purchaseBusy = false
                            }
                        }
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ShopTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Q.bg)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) { Text("←") }
        Text("Магазин тем", style = MaterialTheme.typography.titleLarge, color = Q.ink)
    }
}

@Composable
private fun ThemeCard(
    theme: ThemeState.AccentTheme,
    coins: Int,
    owned: Set<String>,
    isApplied: Boolean,
    justApplied: Boolean,
    busy: Boolean,
    onChoose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Q.surface, RoundedCornerShape(16.dp))
            .border(1.dp, Q.border, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(theme.colorHex)),
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(theme.name, style = MaterialTheme.typography.bodyLarge, color = Q.ink)
        }
        when {
            isApplied -> Text(
                text = if (justApplied) "Куплено и применено ✨" else "Применена",
                color = Q.success,
                style = MaterialTheme.typography.labelLarge,
            )
            theme.id in owned -> TextButton(enabled = !busy, onClick = onChoose) { Text("Применить") }
            else -> BuyAction(theme.price, canAfford = coins >= theme.price, busy = busy, onBuy = onChoose)
        }
    }
}

@Composable
private fun BuyAction(price: Int, canAfford: Boolean, busy: Boolean, onBuy: () -> Unit) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            "$price монет",
            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
            color = if (canAfford) Q.coin else Q.inkMuted,
        )
        // busy — на время запроса покупки: повторный тап не списывает дважды
        TextButton(enabled = canAfford && !busy, onClick = onBuy) { Text("Купить") }
        if (!canAfford) {
            Text("Не хватает монет", color = Q.inkMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}
