package com.questline.app.notify

/** Результат разбора банковского пуша. */
data class ParsedBankEvent(
    val amountMinor: Long,
    /** EXPENSE | INCOME */
    val type: String,
    /** Остаток из пуша («Баланс: 548,04 ₽»); null, если сумма остатка не указана. */
    val balanceMinor: Long? = null,
)

/**
 * Чистый парсер текстов пушей российских банков.
 *
 * Никаких зависимостей от android.* — строка на входе, результат на выходе,
 * поэтому легко покрывается unit-тестами. Устойчив к пробелам и NBSP (\u00A0),
 * тысячным разделителям («1 234», «1'234»), запятой или точке как десятичному
 * разделителю и знаку валюты до или после числа.
 *
 * Правила определения типа операции:
 *  - РАСХОД: покупка / оплата / списание / потрачено / снятие (+ «перевод … себе»).
 *  - ДОХОД: зачислен / поступлен / входящ(ий) / перевод от / выплата /
 *    зарплат(а) / начислен / cashback / кэшбэк.
 *  - Если есть только слова баланса («баланс», «осталось», «лимит», «доступно»,
 *    «остаток») без признака операции — возвращаем null (фантомная трата).
 *  - Если есть и слово операции, и слово баланса — операция приоритетнее.
 *  - Если есть слова и расхода, и дохода — побеждает то, что стоит в тексте раньше
 *    (например «Кэшбэк за покупки» — доход).
 *
 * Выбор суммы: берём денежное значение, БЛИЖАЙШЕЕ к ключевому слову операции;
 * суммы, стоящие сразу после слов баланса, при выборе отбрасываются.
 *
 * Отдельно возвращается [ParsedBankEvent.balanceMinor] — первая денежная сумма,
 * стоящая сразу после слов баланса («баланс», «остаток», «осталось», «доступно»);
 * если таких сумм нет — null.
 */
object BankParser {

    // ---------------- Маркеры ----------------

    private val EXPENSE_WORDS = listOf(
        "покупк",   // покупка, покупки
        "оплат",    // оплата, оплачено
        "списан",   // списание, списано
        "потрачен", // потрачено, потрачена
        "сняти",    // снятие
        "выдач",    // выдача наличных
    )

    private val INCOME_WORDS = listOf(
        "зачислен",   // зачисление, зачислено
        "поступлен",  // поступление
        "входящ",     // входящий перевод/платёж
        "перевод от",
        "выплат",     // выплата
        "зарплат",    // зарплата
        "начислен",   // начислены проценты
        "cashback",
        "cash back",
        "кэшбэк",
        "кешбэк",
    )

    /** Слова, после которых сумма относится к балансу, а не к операции */
    private val AMOUNT_BEFORE_BALANCE_WORDS = listOf(
        "баланс", "остаток", "осталось", "доступн", "лимит",
    )

    private const val TRANSFER_WORD = "перевод"

    /** Куда может быть адресован расходный перевод самому себе */
    private val SELF_MARKERS = listOf(
        "себе", "на свою", "на карту", "на счёт", "на счет",
    )

    private const val TRANSFER_WINDOW = 30 // символов между "перевод" и маркером "себе"
    private const val BALANCE_CONTEXT_CHARS = 16 // хвост перед суммой для проверки контекста
    private const val BARE_NUMBER_WINDOW = 60 // окно поиска числа без валюты после ключевого слова
    private const val MAX_INT_DIGITS = 12 // защита Long от мусорных длинных чисел

    // ---------------- Суммы ----------------

    /** Число + валюта ПОСЛЕ него: «3 500,00 ₽», «500 руб», «1234 RUB» */
    private val MONEY_CURRENCY_AFTER = Regex(
        "(\\d[\\d\\s\\u00A0']{0,14}?)(?:[.,](\\d{1,2}))?\\s*(?:₽|руб\\.?(?![а-яёa-z])|rub(?![а-яёa-z]))",
        RegexOption.IGNORE_CASE,
    )

    /** Валюта ПЕРЕД числом: «₽ 350», «₽350,50», «RUB 1 000» */
    private val MONEY_CURRENCY_BEFORE = Regex(
        "(?:₽|руб\\.?(?![а-яёa-z])|rub(?![а-яёa-z]))\\s*(\\d[\\d\\s\\u00A0']{0,14}?(?:[.,]\\d{1,2})?)",
        RegexOption.IGNORE_CASE,
    )

    /** Страховка: число без символа валюты после ключевого слова операции */
    private val BARE_NUMBER = Regex("\\d+(?:[.,]\\d{1,2})?")

    private val DECIMAL_TAIL = Regex("[.,](\\d{1,2})$")

    // ---------------- Разбор ----------------

    /**
     * Разобрать текст пуша.
     * @return null, если операцию надёжно распознать нельзя.
     */
    fun parse(raw: String): ParsedBankEvent? {
        if (raw.isBlank()) return null

        // Нормализация: NBSP и узкие пробелы -> обычный пробел.
        val text = raw.replace('\u00A0', ' ').replace('\u202F', ' ').replace('\u2007', ' ')
        if (text.isBlank()) return null
        val lower = text.lowercase()

        val incomeIdx = firstIndexOf(lower, INCOME_WORDS)
        val explicitExpenseIdx = firstIndexOf(lower, EXPENSE_WORDS)
        val transferSelfIdx = transferToSelfIndex(lower)

        if (incomeIdx < 0 && explicitExpenseIdx < 0 && transferSelfIdx < 0) {
            // Нет признаков операции: либо текст про баланс, либо нераспознаваемое.
            return null
        }

        // Тип и якорь — позиция ключевого слова, к которой ищем ближайшую сумму.
        val (type, anchor) = anchors(incomeIdx, explicitExpenseIdx, transferSelfIdx)

        val amountMinor = findAmountNear(lower, anchor) ?: return null
        if (amountMinor <= 0L) return null

        // Остаток: первая money-сумма, стоящая сразу после слова баланса
        // («Баланс: 548,04 ₽»). Независимо от того, какая сумма стала операцией.
        val balanceMinor = collectCurrencyAmounts(lower)
            .filter { (position, _) -> belongsToBalance(lower, position) }
            .minByOrNull { it.first }
            ?.second

        return ParsedBankEvent(amountMinor = amountMinor, type = type, balanceMinor = balanceMinor)
    }

    /** Раньше стоящее ключевое слово определяет тип операции. */
    private fun anchors(incomeIdx: Int, expenseIdx: Int, transferSelfIdx: Int): Pair<String, Int> {
        val candidates = buildList {
            if (expenseIdx >= 0) add(expenseIdx to "EXPENSE")
            if (transferSelfIdx >= 0) add(transferSelfIdx to "EXPENSE")
            if (incomeIdx >= 0) add(incomeIdx to "INCOME")
        }
        // При равенстве позиций явное слово расхода важнее эвристики перевода себе
        return candidates.minWithOrNull(compareBy({ it.first }, { it.second == "EXPENSE" }))
            ?.let { it.second to it.first }
            ?: ("EXPENSE" to 0) // недостижимо: выше гарантирован хотя бы один кандидат
    }

    /**
     * Индекс слова «перевод», которое адресовано себе («…на карту», «…себе»),
     * при условии что между ними нет «от» (это уже входящий перевод).
     * Если таких мест несколько — берётся первое.
     */
    private fun transferToSelfIndex(lower: String): Int {
        var from = 0
        while (true) {
            val i = lower.indexOf(TRANSFER_WORD, from)
            if (i < 0) return -1
            val windowEnd = minOf(lower.length, i + TRANSFER_WORD.length + TRANSFER_WINDOW)
            val gap = lower.substring(i + TRANSFER_WORD.length, windowEnd)
            val markerOffset = SELF_MARKERS
                .mapNotNull { gap.indexOf(it).takeIf { idx -> idx >= 0 } }
                .minOrNull()
            if (markerOffset != null) {
                val beforeMarker = gap.substring(0, markerOffset)
                // "перевод от Иван … на карту" — это доходный шаблон, не наш случай
                if (!containsStandaloneWord(beforeMarker, "от")) return i
            }
            from = i + TRANSFER_WORD.length
        }
    }

    // ---------------- Поиск суммы ----------------

    /**
     * Денежное значение, ближайшее к якорю. Кандидаты, стоящие сразу после слов
     * баланса, отбрасываются; если других нет — используется любой найденный.
     * Совсем без кандидатов с валютой — резервный поиск голого числа.
     */
    private fun findAmountNear(lower: String, anchor: Int): Long? {
        val candidates = collectCurrencyAmounts(lower)
        if (candidates.isEmpty()) return findBareNumber(lower, anchor)

        val clean = candidates.filterNot { belongsToBalance(lower, it.first) }
        val pool = clean.ifEmpty { candidates }

        // Предпочитаем суммы, стоящие ПОСЛЕ ключевого слова операции
        // («Оплата. Сумма 350 ₽», а не «Комиссия 10 ₽» перед главной суммой).
        // Совпадение по дистанции; если «после якоря» ничего нет — ближайшее любое.
        return pool.asSequence()
            .filter { it.first >= anchor }
            .minByOrNull { it.first - anchor }?.second
            ?: pool.minByOrNull { kotlin.math.abs(it.first - anchor) }?.second
    }

    /** Есть ли в тексте отдельное слово (работает и для кириллицы). */
    private fun containsStandaloneWord(text: String, word: String): Boolean {
        var idx = text.indexOf(word)
        while (idx >= 0) {
            val beforeOk = idx == 0 || !text[idx - 1].isLetter()
            val afterIdx = idx + word.length
            val afterOk = afterIdx >= text.length || !text[afterIdx].isLetter()
            if (beforeOk && afterOk) return true
            idx = text.indexOf(word, idx + 1)
        }
        return false
    }

    /** Все кандидаты вида «число+валюта» или «валюта+число» как (позиция, копейки). */
    private fun collectCurrencyAmounts(text: String): List<Pair<Int, Long>> {
        val result = mutableListOf<Pair<Int, Long>>()

        MONEY_CURRENCY_AFTER.findAll(text).forEach { m ->
            val minor = toMinor(intPartOf(m.groupValues[1]), m.groupValues[2].takeIf { it.isNotEmpty() })
            if (minor != null) result += m.range.first to minor
        }
        MONEY_CURRENCY_BEFORE.findAll(text).forEach { m ->
            val token = m.groupValues[1]
            val minor = toMinor(intPartOf(token), DECIMAL_TAIL.find(token)?.groupValues?.get(1))
            if (minor != null) result += m.range.first to minor
        }
        return result
    }

    /** Сумма следует за словом баланса? */
    private fun belongsToBalance(lower: String, amountStart: Int): Boolean {
        if (amountStart <= 0) return false
        val tail = lower.substring(0, amountStart).takeLast(BALANCE_CONTEXT_CHARS)
        return AMOUNT_BEFORE_BALANCE_WORDS.any { tail.contains(it) }
    }

    /** Резервный поиск числа без символа валюты в окне после ключевого слова. */
    private fun findBareNumber(lower: String, anchor: Int): Long? {
        if (anchor < 0 || anchor >= lower.length) return null
        val window = lower.substring(anchor, minOf(lower.length, anchor + BARE_NUMBER_WINDOW))
        val match = BARE_NUMBER.find(window) ?: return null
        val token = match.value
        val decimalTail = DECIMAL_TAIL.find(token)?.groupValues?.get(1)
        val intPart = token.dropLastWhile { it.isDigit() && decimalTail != null }
            .let { if (decimalTail != null) it.dropLast(1) else it } // убрать [.,]
        return toMinor(intPart, decimalTail)
    }

    /** Целая часть сырого токена до возможной десятичной запятой/точки. */
    private fun intPartOf(token: String): String {
        val cutAt = token.indexOfFirst { it == ',' || it == '.' }
        return if (cutAt >= 0) token.substring(0, cutAt) else token
    }

    // ---------------- Числа ----------------

    /**
     * Преобразовать сырое значение в копейки. Пробелы, NBSP и апострофы
     * удаляются; запятая учитывается отдельно как десятичный хвост.
     * @return null для пустых, слишком длинных и нулевых значений.
     */
    private fun toMinor(intRaw: String, fracRaw: String?): Long? {
        val intDigits = intRaw.filter { it.isDigit() }
        if (intDigits.isEmpty() || intDigits.length > MAX_INT_DIGITS) return null
        val rubles = intDigits.toLongOrNull() ?: return null

        val fracDigits = fracRaw.orEmpty().filter { it.isDigit() }
        val kopecks = when (fracDigits.length) {
            0 -> 0L
            1 -> fracDigits.toLong() * 10
            else -> fracDigits.take(2).toLong()
        }
        val total = rubles * 100 + kopecks
        return if (total > 0) total else null
    }

    // ---------------- Служебное ----------------

    private fun firstIndexOf(lowerText: String, words: List<String>): Int {
        var best = -1
        for (w in words) {
            val idx = lowerText.indexOf(w)
            if (idx >= 0 && (best < 0 || idx < best)) best = idx
        }
        return best
    }
}
