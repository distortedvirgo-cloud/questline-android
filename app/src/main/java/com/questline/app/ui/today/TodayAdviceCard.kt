package com.questline.app.ui.today

/* Совет дня — карточка-плейсхолдер (T-06): тихая поверхность surfaceAlt, без
 * акцента. Реальный rule-движок советов приедет в T-12 (NudgeEngine).
 */

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.questline.app.ui.theme.Q

@Composable
internal fun TodayAdviceCard() {
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = CARD_SHAPE, colors = cardColors()) {
        Column(Modifier.padding(16.dp)) {
            Text("Совет дня", style = MaterialTheme.typography.titleMedium, color = Q.inkMuted)
            Spacer(Modifier.height(6.dp))
            Text(
                "Подберём совет по твоим данным в ближайших обновлениях",
                style = MaterialTheme.typography.bodyMedium,
                color = Q.inkMuted,
            )
        }
    }
}
