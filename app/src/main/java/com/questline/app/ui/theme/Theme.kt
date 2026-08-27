package com.questline.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/* Палитра STYLE.md — единственный источник цветов в проекте.
   Ничего вне этого списка (правило конституции). */
object Q {
    val bg = Color(0xFFFAFAF7)          // фон приложения, тёплый off-white
    val surface = Color(0xFFFFFFFF)     // карточки
    val surfaceAlt = Color(0xFFF1EFEA)  // вторичные блоки, чипы
    val border = Color(0xFFE5E2DA)      // границы вместо теней
    val ink = Color(0xFF1E1E1C)
    val inkMuted = Color(0xFF8A8780)
    val accent = Color(0xFF4A5FD9)      // единственный акцент
    val accentSoft = Color(0xFFEEF0FB)
    val success = Color(0xFF3D8B5F)
    val warn = Color(0xFFC99A3C)
    val danger = Color(0xFFC4544A)
    val coin = Color(0xFFD9A441)        // монеты; нигде кроме косметики
}

private val LightScheme = lightColorScheme(
    primary = Q.accent,
    onPrimary = Color.White,
    primaryContainer = Q.accentSoft,
    onPrimaryContainer = Q.accent,
    secondary = Q.ink,
    onSecondary = Color.White,
    secondaryContainer = Q.surfaceAlt,
    onSecondaryContainer = Q.ink,
    tertiary = Q.success,
    onTertiary = Color.White,
    background = Q.bg,
    onBackground = Q.ink,
    surface = Q.surface,
    onSurface = Q.ink,
    surfaceVariant = Q.surfaceAlt,
    onSurfaceVariant = Q.inkMuted,
    outline = Q.border,
    outlineVariant = Q.border,
    error = Q.danger,
    onError = Color.White,
)

@Composable
fun QuestlineTheme(content: @Composable () -> Unit) {
    // Живая тема: accent меняется из магазина косметики (ThemeState.selectedIndex)
    val accent = com.questline.app.ui.shop.ThemeState.accent
    val scheme = if (accent == Q.accent) LightScheme else LightScheme.copy(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = accent.copy(alpha = 0.14f),
        onPrimaryContainer = accent,
    )
    MaterialTheme(
        colorScheme = scheme,
        content = content,
    )
}
