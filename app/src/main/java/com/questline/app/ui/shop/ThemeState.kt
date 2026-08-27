package com.questline.app.ui.shop

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.questline.app.data.AppRepo
import kotlinx.coroutines.flow.first

/**
 * Магазин косметики: выбираемая тема акцента.
 * Состояние переживает процесс в SharedPreferences; владение темами хранится
 * строковым множеством там же. Индига бесплатна и считается купленной всегда.
 */
object ThemeState {

    data class AccentTheme(val id: String, val name: String, val colorHex: Long, val price: Int)

    val themes = listOf(
        AccentTheme("indigo", "Индиго", 0xFF4A5FD9, 0),
        AccentTheme("emerald", "Изумруд", 0xFF3D8B5F, 150),
        AccentTheme("amber", "Янтарь", 0xFFC99A3C, 150),
        AccentTheme("crimson", "Багряный", 0xFFC4544A, 150),
    )

    /** Публичное: QuestlineTheme читает его при сборке ColorScheme. */
    var selectedIndex by mutableIntStateOf(0)

    private const val PREFS = "theme_prefs"
    private const val KEY_INDEX = "idx"
    private const val KEY_OWNED = "owned"
    private const val ALWAYS_OWNED = "indigo"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        selectedIndex = prefs(ctx).getInt(KEY_INDEX, 0).coerceIn(0, 3)
    }

    fun persist(ctx: Context) {
        prefs(ctx).edit().putInt(KEY_INDEX, selectedIndex).apply()
    }

    /** Купленные темы; индига входит в выдачу по умолчанию. */
    fun ownedSet(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(KEY_OWNED, emptySet()).orEmpty() + ALWAYS_OWNED

    fun markOwned(ctx: Context, id: String) {
        prefs(ctx).edit().putStringSet(KEY_OWNED, ownedSet(ctx) + id).apply()
    }

    /**
     * Покупка темы за монеты. Уже купленная — просто true. При нехватке монет
     * ничего не списывает и возвращает false. Списание — одна запись гроссбуха.
     */
    suspend fun buy(ctx: Context, theme: AccentTheme): Boolean {
        val repo = AppRepo.get(ctx)
        if (theme.id in ownedSet(ctx)) return true
        val coins = repo.coins.observeTotalCoins().first()
        if (coins < theme.price) return false
        repo.addCoins(-theme.price, "SHOP_PURCHASE")
        markOwned(ctx, theme.id)
        return true
    }

    /** Текущий акцентный цвет для ColorScheme в Theme.kt. */
    val accent: Color
        get() = Color(themes[selectedIndex.coerceIn(0, 3)].colorHex)
}
