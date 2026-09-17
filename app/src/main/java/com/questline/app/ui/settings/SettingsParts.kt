package com.questline.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.questline.app.ui.theme.AppTheme
import com.questline.app.ui.theme.Q

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppearanceSection() {
    val context = LocalContext.current
    SectionCard(title = "Оформление") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                AppTheme.SYSTEM to "Системная",
                AppTheme.LIGHT to "Светлая",
                AppTheme.DARK to "Тёмная",
            ).forEach { (mode, label) ->
                FilterChip(
                    selected = AppTheme.mode == mode,
                    onClick = { AppTheme.set(context, mode) },
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
internal fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Q.surface, RoundedCornerShape(16.dp))
            .border(1.dp, Q.border, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        content()
    }
}
