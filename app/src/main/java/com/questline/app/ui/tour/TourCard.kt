package com.questline.app.ui.tour

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.questline.app.ui.theme.Q

/** Карточка шага: заголовок, текст, «N из 13», точки, «Далее», «Пропустить». */
@Composable
internal fun TourCard(
    step: TourStep,
    index: Int,
    total: Int,
    isLast: Boolean,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Q.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = modifier,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = step.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Q.ink,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = step.body,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                color = Q.inkMuted,
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${index + 1} из $total",
                    style = MaterialTheme.typography.labelSmall,
                    color = Q.inkMuted,
                )
                Spacer(Modifier.weight(1f))
                TourDots(current = index, total = total)
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onNext,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Q.accent,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
            ) {
                Text(text = if (isLast) "Поехали!" else "Далее")
            }
            TextButton(
                onClick = onSkip,
                colors = ButtonDefaults.textButtonColors(contentColor = Q.inkMuted),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(text = "Пропустить экскурсию")
            }
        }
    }
}

/** Точки прогресса: текущая — шире и в акценте, остальные — приглушённые. */
@Composable
private fun TourDots(current: Int, total: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { i ->
            val selected = i == current
            Box(
                modifier = Modifier
                    .size(width = if (selected) 16.dp else 6.dp, height = 6.dp)
                    .background(
                        color = if (selected) Q.accent else Q.border,
                        shape = RoundedCornerShape(3.dp),
                    ),
            )
        }
    }
}
