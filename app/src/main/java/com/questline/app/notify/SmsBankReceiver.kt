package com.questline.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.questline.app.data.PendingTxn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Приёмник SMS. Читает ТОЛЬКО сообщения от банковских отправителей из
 * белого списка: номер 900 и alphanumeric-имена Сбера. Всё остальное
 * игнорируется до разбора текста.
 */
class SmsBankReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context?, intent: Intent?) {
        try {
            if (context == null) return
            if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
            if (!BankPrefs.isEnabled(context)) return

            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            val sender = messages?.firstOrNull()?.originatingAddress?.trim().orEmpty()
            if (!isAllowedSender(sender)) {
                Log.d(TAG, "SMS от «$sender» вне белого списка — пропущено")
                return
            }

            val text = messages.orEmpty()
                .joinToString("") { it.displayMessageBody.orEmpty() }
                .trim()
            if (text.isBlank()) return

            val parsed = BankParser.parse(text) ?: run {
                Log.d(TAG, "SMS от «$sender» не распознано как операция")
                return
            }

            val appContext = context.applicationContext
            scope.launch {
                try {
                    val dao = com.questline.app.data.AppRepo.get(appContext).pending
                    val now = System.currentTimeMillis()
                    if (dao.isDuplicate(text, now) > 0) return@launch
                    dao.insert(
                        PendingTxn(
                            bankPackage = "sms:${sender.lowercase()}",
                            title = "SMS $sender",
                            text = text,
                            amountMinor = parsed.amountMinor,
                            type = parsed.type,
                            epochDay = LocalDate.now().toEpochDay(),
                            receivedMillis = now,
                        ),
                    )
                    Log.d(TAG, "SMS-операция добавлена: ${parsed.type} ${parsed.amountMinor}")
                } catch (e: Exception) {
                    Log.d(TAG, "Ошибка записи SMS-операции: ${e.message}")
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "Сбой приёма SMS: ${t.message}")
        }
    }

    /** Белый список отправителей: номер 900 и имена Сбера. */
    private fun isAllowedSender(sender: String): Boolean {
        val s = sender.lowercase().replace(" ", "")
        if (s.isEmpty()) return false
        return s == "900" || s.contains("сбер") || s.contains("sber")
    }

    private companion object {
        const val TAG = "SmsBankReceiver"
    }
}
