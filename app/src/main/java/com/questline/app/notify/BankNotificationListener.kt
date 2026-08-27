package com.questline.app.notify

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.questline.app.data.AppRepo
import com.questline.app.data.PendingTxn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Ловец пушей банков. Система связывает сервис через BIND_NOTIFICATION_LISTENER_SERVICE,
 * пользователь включает доступ в настройках Android + флаг в [BankPrefs].
 *
 * Инвариант: сервис НИКОГДА не падает. Весь колбэк и корутина обёрнуты в try/catch,
 * parse() работает на строке без зависимостей и не может выбросить ничего тяжелее,
 * но защита стоит на каждом слое.
 */
class BankNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Пакеты-кандидаты банков: проверяем на lower-case подстроку имени пакета.
    private val bankPackageHints = listOf(
        "bank", "sber", "alfa", "alfabank", "tinkoff", "tbank", "vtb",
        "gazprom", "raif", "otp", "psb", "sovcom", "mts",
    )

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try {
            if (sbn == null) return
            val bankPackage = sbn.packageName ?: return
            if (!looksLikeBank(bankPackage)) return

            // Быстрая часть: извлечь строки. Тяжёлое (Room, парсинг) — в IO-корутине.
            val extras = sbn.notification?.extras ?: return
            var text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            if (text.isBlank()) {
                text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
            }
            if (text.isBlank()) return
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()

            scope.launch {
                try {
                    handlePush(bankPackage = bankPackage, title = title, text = text)
                } catch (_: Exception) {
                    // Пуш не сохранён — молча игнорируем, уведомление придёт повторно или потеряется.
                }
            }
        } catch (_: Throwable) {
            // Ни при каких условиях не роняем сервис.
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** IO-часть: парсинг, дедупликация и запись PendingTxn. Никогда не бросает наружу. */
    private suspend fun handlePush(bankPackage: String, title: String, text: String) {
        if (!BankPrefs.isEnabled(this)) return

        // Тип операции у Сбера часто в ЗАГОЛОВКЕ («Перевод от Ивана»), а в тексте
        // только «+ 100 ₽ — Баланс: …». Парсим связку, дедуп и хранение — по тексту.
        val parsed = BankParser.parse(if (title.isBlank()) text else title + "\n" + text) ?: return
        val now = System.currentTimeMillis()

        val dao = AppRepo.get(applicationContext).pending
        if (dao.isDuplicate(text, now) != 0) return

        dao.insert(
            PendingTxn(
                bankPackage = bankPackage,
                title = title,
                text = text,
                amountMinor = parsed.amountMinor,
                type = parsed.type,
                epochDay = LocalDate.now().toEpochDay(),
                receivedMillis = now,
            )
        )
    }

    private fun looksLikeBank(packageName: String): Boolean {
        val lowered = packageName.lowercase()
        return bankPackageHints.any { lowered.contains(it) }
    }
}
