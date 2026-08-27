package com.questline.app.ui.theme

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/* Палитра STYLE.md — единственный источник цветов в проекте.
   Светлая и тёмная схемы используют один список имён; экраны обращаются к Q.*
   и всегда получают цвета активной схемы. Ничего вне этого списка. */

@Immutable
data class QColors(
    val bg: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val border: Color,
    val ink: Color,
    val inkMuted: Color,
    val accent: Color,
    val accentSoft: Color,
    val success: Color,
    val warn: Color,
    val danger: Color,
    val coin: Color,
)

val LightQ = QColors(
    bg = Color(0xFFFAFAF7),         // фон приложения, тёплый off-white
    surface = Color(0xFFFFFFFF),    // карточки
    surfaceAlt = Color(0xFFF1EFEA), // вторичные блоки, чипы
    border = Color(0xFFE5E2DA),     // границы вместо теней
    ink = Color(0xFF1E1E1C),
    inkMuted = Color(0xFF8A8780),
    accent = Color(0xFF4A5FD9),     // единственный акцент
    accentSoft = Color(0xFFEEF0FB),
    success = Color(0xFF3D8B5F),
    warn = Color(0xFFC99A3C),
    danger = Color(0xFFC4544A),
    coin = Color(0xFFD9A441),       // монеты; нигде кроме косметики
)

// Тёплый графит вместо чистого чёрного; акценты осветлены под контраст.
val DarkQ = QColors(
    bg = Color(0xFF161512),
    surface = Color(0xFF201E19),
    surfaceAlt = Color(0xFF2A2721),
    border = Color(0xFF3B3730),
    ink = Color(0xFFECE9E1),
    inkMuted = Color(0xFF9B968A),
    accent = Color(0xFF93A5EE),
    accentSoft = Color(0xFF262B47),
    success = Color(0xFF6FB78D),
    warn = Color(0xFFD6B069),
    danger = Color(0xFFDA8078),
    coin = Color(0xFFE2BB59),
)

private val LocalQ = staticCompositionLocalOf { LightQ }

/** Режим оформления: системная / светлая / тёмная («Настройки → Оформление»). */
object AppTheme {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"

    /** Глобальное состояние — читает QuestlineTheme, пишет секция «Оформление». */
    var mode by mutableStateOf(SYSTEM)
        private set

    private const val PREFS = "theme_prefs"
    private const val KEY_MODE = "mode"

    fun load(context: Context) {
        mode = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, SYSTEM) ?: SYSTEM
    }

    fun set(context: Context, value: String) {
        mode = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, value).apply()
    }
}

/** Цвета активной схемы; Q.bg и соседи доступны в любом композабле. */
object Q {
    val bg: Color @Composable get() = LocalQ.current.bg
    val surface: Color @Composable get() = LocalQ.current.surface
    val surfaceAlt: Color @Composable get() = LocalQ.current.surfaceAlt
    val border: Color @Composable get() = LocalQ.current.border
    val ink: Color @Composable get() = LocalQ.current.ink
    val inkMuted: Color @Composable get() = LocalQ.current.inkMuted
    val accent: Color @Composable get() = LocalQ.current.accent
    val accentSoft: Color @Composable get() = LocalQ.current.accentSoft
    val success: Color @Composable get() = LocalQ.current.success
    val warn: Color @Composable get() = LocalQ.current.warn
    val danger: Color @Composable get() = LocalQ.current.danger
    val coin: Color @Composable get() = LocalQ.current.coin
}

/** Вся палитра разом — для захвата перед Canvas/DrawScope (там @Composable нельзя). */
@Composable
fun questlineQ(): QColors = LocalQ.current

private fun lightScheme(q: QColors) = lightColorScheme(
    primary = q.accent,
    onPrimary = Color.White,
    primaryContainer = q.accentSoft,
    onPrimaryContainer = q.accent,
    secondary = q.ink,
    onSecondary = Color.White,
    secondaryContainer = q.surfaceAlt,
    onSecondaryContainer = q.ink,
    tertiary = q.success,
    onTertiary = Color.White,
    background = q.bg,
    onBackground = q.ink,
    surface = q.surface,
    onSurface = q.ink,
    surfaceVariant = q.surfaceAlt,
    onSurfaceVariant = q.inkMuted,
    outline = q.border,
    outlineVariant = q.border,
    error = q.danger,
    onError = Color.White,
)

private fun darkScheme(q: QColors) = darkColorScheme(
    primary = q.accent,
    onPrimary = Color(0xFF171613),
    primaryContainer = q.accentSoft,
    onPrimaryContainer = q.accent,
    secondary = q.ink,
    onSecondary = Color(0xFF171613),
    secondaryContainer = q.surfaceAlt,
    onSecondaryContainer = q.ink,
    tertiary = q.success,
    onTertiary = Color(0xFF171613),
    background = q.bg,
    onBackground = q.ink,
    surface = q.surface,
    onSurface = q.ink,
    surfaceVariant = q.surfaceAlt,
    onSurfaceVariant = q.inkMuted,
    outline = q.border,
    outlineVariant = q.border,
    error = q.danger,
    onError = Color(0xFF171613),
)

@Composable
fun QuestlineTheme(content: @Composable () -> Unit) {
    val dark = when (AppTheme.mode) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        else -> isSystemInDarkTheme()
    }
    val baseQ = if (dark) DarkQ else LightQ

    // Живая тема: акцент меняется из магазина косметики (ThemeState.selectedIndex);
    // 0 — индуга по умолчанию, у неё в каждой схеме свой оттенок.
    val selected = com.questline.app.ui.shop.ThemeState.selectedIndex
    val q = if (selected == 0) baseQ else {
        val custom = Color(com.questline.app.ui.shop.ThemeState.themes[selected.coerceIn(0, 3)].colorHex)
        baseQ.copy(accent = custom, accentSoft = custom.copy(alpha = 0.16f))
    }

    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
    }

    CompositionLocalProvider(LocalQ provides q) {
        MaterialTheme(
            colorScheme = if (dark) darkScheme(q) else lightScheme(q),
            content = content,
        )
    }
}
