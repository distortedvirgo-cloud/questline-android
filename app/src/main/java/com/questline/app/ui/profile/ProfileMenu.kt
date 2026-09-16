package com.questline.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.questline.app.ui.theme.Q

/** Меню «Я»: вертикальные пункты-карточки со стрелкой (хаб всех подсистем v3). */
@Composable
fun ProfileMenu(
    onMirror: () -> Unit,
    onShop: () -> Unit,
    onAssistant: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MenuRow("🪞 Зеркало недели", onMirror)
        MenuRow("🎨 Магазин тем", onShop)
        MenuRow("🤖 AI-коуч", onAssistant)
        MenuRow("⚙ Настройки", onSettings)
    }
}

@Composable
private fun MenuRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Q.surface)
            .border(1.dp, Q.border, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text("›", style = MaterialTheme.typography.titleLarge, color = Q.inkMuted)
    }
}
