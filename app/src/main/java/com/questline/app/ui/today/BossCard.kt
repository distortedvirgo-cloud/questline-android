package com.questline.app.ui.today

/* Карточка «Босс месяца» на вкладке «Сегодня».
 * Данные — живой поток закрытых квестов из AppRepo; урон считает BossEngine.
 * Победа (урон >= 100) даёт одноразовый +50 монет: перед начислением
 * проверяем CoinsLedger по reason=BOSS_WIN и refId месяца — рекомпозиции
 * и повторные входы на экран не задвоят бонус.
 */

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.questline.app.data.AppRepo
import com.questline.app.domain.BossEngine
import com.questline.app.ui.theme.Q
import java.time.YearMonth

@Composable
fun BossCard(repo: AppRepo, modifier: Modifier = Modifier) {
    val month = remember { YearMonth.now() }

    // Все DONE-квесты живым потоком; конкретный месяц отфильтрует BossEngine по closedAtMillis
    val doneQuests by remember { repo.quests.observeDone() }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val boss = BossEngine.compute(doneQuests, month)
    val barColor = if (boss.defeated) Q.success else Q.danger

    // Одноразовый бонус: сначала смотрим ledger, потом начисляем
    LaunchedEffect(boss.defeated, month) {
        if (!boss.defeated) return@LaunchedEffect
        val refId = BossEngine.ledgerRefId(month)
        val alreadyPaid = repo.coins.countByReasonRef(BossEngine.REASON_BOSS_WIN, refId)
        if (alreadyPaid == 0) {
            repo.addCoins(BossEngine.WIN_BONUS_COINS, BossEngine.REASON_BOSS_WIN, refId)
        }
    }

    val caption = if (boss.defeated) {
        "🏆 Босс побеждён! +${BossEngine.WIN_BONUS_COINS} монет"
    } else {
        "HP ${boss.hpLeft}/${BossEngine.BOSS_MAX_HP} · Закрывай квесты — каждый бьёт босса"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Q.surface,
        border = BorderStroke(1.dp, Q.border),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Босс месяца", style = MaterialTheme.typography.labelSmall, color = Q.inkMuted)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(boss.emoji, fontSize = 34.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        boss.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    HpBar(
                        fraction = if (boss.defeated) 1f else boss.hpLeft / BossEngine.BOSS_MAX_HP.toFloat(),
                        color = barColor,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(caption, style = MaterialTheme.typography.bodySmall, color = Q.inkMuted)
                }
            }
        }
    }
}

/** HP-бар как в ProfileScreen: подложка Q.surfaceAlt, заливка danger → success при победе. */
@Composable
private fun HpBar(fraction: Float, color: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Q.surfaceAlt),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(color),
        )
    }
}
