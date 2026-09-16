package com.questline.app.ui.today

/* Карточка квеста дня (v2, без изменений логики): эмодзи характеристики,
 * кнопка «Выполнить», закрытие с конфетти и всплывающим «+XP» (QuestEffects);
 * реальное закрытие квеста происходит по окончании анимации.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.questline.app.data.Quest
import com.questline.app.ui.theme.Q
import kotlinx.coroutines.launch

@Composable
internal fun QuestCard(quest: Quest, emoji: String, busy: Boolean, vm: TodayViewModel) {
    val scope = rememberCoroutineScope()
    val burst = rememberQuestBurstState(seed = quest.id)
    var completing by remember { mutableStateOf(false) }
    var cardTopLeft by remember { mutableStateOf(Offset.Zero) }
    var buttonCenter by remember { mutableStateOf(Offset.Zero) }

    fun close() {
        if (busy || completing) return
        completing = true // эффекты; реальное закрытие — по окончании анимации (onFinished)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { cardTopLeft = it.boundsInWindow().topLeft },
    ) {
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = burst.cardScale
                    scaleY = burst.cardScale
                    alpha = burst.cardAlpha
                },
            shape = CARD_SHAPE,
            colors = cardColors(),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CHIP_SHAPE).background(Q.surfaceAlt), contentAlignment = Alignment.Center) {
                    Text(emoji, fontSize = 20.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    // Заголовок без обрезки: переносится на столько строк, сколько нужно.
                    Text(quest.title, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(2.dp))
                    Text("+${quest.xpReward} XP", style = MaterialTheme.typography.bodySmall, color = Q.accent)
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = { close() },
                    enabled = !busy && !completing,
                    modifier = Modifier.onGloballyPositioned { buttonCenter = it.boundsInWindow().center },
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text("Выполнить", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        QuestCompletionOverlay(
            visible = completing,
            xpText = "+${quest.xpReward} XP",
            origin = buttonCenter - cardTopLeft,
            onFinished = {
                scope.launch {
                    try {
                        vm.markQuestBusy(quest.id)
                        vm.completeQuest(quest)
                    } finally {
                        vm.releaseQuestBusy(quest.id)
                    }
                }
            },
            state = burst,
        )
    }
}
