package com.questline.app.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Разбор банковских пушей: тип операции и сумма.
 * Суммы проверяются в копейках (minor units).
 */
class BankParserTest {

    // ---------------- TRANSFER: перевод самому себе ----------------

    @Test
    fun `перевод на свою карту — TRANSFER`() {
        val parsed = BankParser.parse("Перевод 1 000 ₽ на свою карту ••5129")
        assertEquals("TRANSFER", parsed?.type)
        assertEquals(100_000L, parsed?.amountMinor)
    }

    @Test
    fun `перевод между своими счетами — TRANSFER`() {
        val parsed = BankParser.parse(
            "Перевод между своими счетами: 1 000 ₽. Платёжный счёт ••1205 → Платёжный счёт ••5129",
        )
        assertEquals("TRANSFER", parsed?.type)
        assertEquals(100_000L, parsed?.amountMinor)
    }

    @Test
    fun `перевод себе раньше доходного слова — TRANSFER`() {
        val parsed = BankParser.parse("Перевод себе 1 000 ₽. Кэшбэк 50 ₽")
        assertEquals("TRANSFER", parsed?.type)
        assertEquals(100_000L, parsed?.amountMinor)
    }

    // ---------------- INCOME ----------------

    @Test
    fun `перевод от человека — INCOME`() {
        val parsed = BankParser.parse("Перевод от Ивана 500 ₽")
        assertEquals("INCOME", parsed?.type)
        assertEquals(50_000L, parsed?.amountMinor)
    }

    @Test
    fun `кэшбэк раньше слова покупки — INCOME`() {
        val parsed = BankParser.parse("Кэшбэк за покупки 50 ₽")
        assertEquals("INCOME", parsed?.type)
        assertEquals(5_000L, parsed?.amountMinor)
    }

    @Test
    fun `зачисление — INCOME`() {
        val parsed = BankParser.parse("Зачисление 10 000 ₽")
        assertEquals("INCOME", parsed?.type)
        assertEquals(1_000_000L, parsed?.amountMinor)
    }

    // ---------------- EXPENSE ----------------

    @Test
    fun `явный расход раньше перевода себе — EXPENSE и сумма расхода`() {
        val parsed = BankParser.parse("Оплата 100 ₽ в кофейне. Перевод себе 200 ₽")
        assertEquals("EXPENSE", parsed?.type)
        assertEquals(10_000L, parsed?.amountMinor)
    }

    @Test
    fun `оплата с остатком — EXPENSE и баланс отдельно`() {
        val parsed = BankParser.parse("Оплата 350 ₽. Баланс: 548,04 ₽")
        assertEquals("EXPENSE", parsed?.type)
        assertEquals(35_000L, parsed?.amountMinor)
        assertEquals(54_804L, parsed?.balanceMinor)
    }

    // ---------------- Фантомные операции ----------------

    @Test
    fun `только баланс без признака операции — null`() {
        assertNull(BankParser.parse("Баланс: 5 000 ₽"))
        assertNull(BankParser.parse("Доступно 1 234,56 ₽"))
    }

    @Test
    fun `пустой текст — null`() {
        assertNull(BankParser.parse("   "))
    }
}
