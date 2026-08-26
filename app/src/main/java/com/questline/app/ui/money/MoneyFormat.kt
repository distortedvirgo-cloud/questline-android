package com.questline.app.ui.money

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

/** Деньги и даты финансового модуля. Все суммы — minor units (копейки). */
object MoneyFormat {

    private val numberFormat = NumberFormat.getIntegerInstance(Locale("ru", "RU"))

    /** «20 000 ₽»; отрицательный баланс — «−3 500 ₽» стиль знака из формата */
    fun text(minor: Long): String = numberFormat.format(minor / 100) + " \u20BD"

    /**
     * Парсинг ввода пользователя в копейки: допускаем запятую/точку,
     * пробелы как разделители групп. Некорректное или отрицательное — null.
     */
    fun parseRubles(input: String): Long? {
        val normalized = input.trim()
            .replace("\u00A0", "")
            .replace(" ", "")
            .replace(',', '.')
        val value = normalized.toDoubleOrNull() ?: return null
        if (!value.isFinite() || value < 0) return null
        return (value * 100).roundToLong()
    }

    /** «Февраль 2026» с заглавной буквы */
    fun monthTitle(date: LocalDate): String {
        val raw = DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru")).format(date)
        return raw.replaceFirstChar { it.uppercase(Locale("ru")) }
    }

    /** Границы месяца в epochDay: [первый день, последний день] */
    fun monthBounds(firstDayOfMonth: LocalDate): Pair<Long, Long> {
        val first = firstDayOfMonth.withDayOfMonth(1)
        val last = first.withDayOfMonth(first.lengthOfMonth())
        return first.toEpochDay() to last.toEpochDay()
    }

    /** «14 августа» — день транзакции словами */
    fun dayWords(epochDay: Long): String {
        val date = LocalDate.ofEpochDay(epochDay)
        return DateTimeFormatter.ofPattern("d MMMM", Locale("ru")).format(date)
    }
}
