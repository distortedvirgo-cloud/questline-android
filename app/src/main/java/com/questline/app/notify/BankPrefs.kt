package com.questline.app.notify

import android.content.Context

/**
 * Флаг включённости автоучёта по банковским пушам.
 * Обычные SharedPreferences — шифрование не требуется (не добавляли зависимость).
 */
object BankPrefs {

    private const val FILE_NAME = "bank_prefs"
    private const val KEY_ENABLED = "enabled"

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
}
