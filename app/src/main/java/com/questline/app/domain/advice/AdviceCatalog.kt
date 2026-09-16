package com.questline.app.domain.advice

/**
 * T-12: шаблон стартовой привычки — данные, из которых кнопка «Взять»
 * создаёт привычку в один тап.
 */
data class StarterHabit(
    val title: String,
    val emoji: String,
    /** PHYSICS | MIND | MONEY | SOCIAL | DISCIPLINE */
    val characteristic: String,
    /** S | M | L — влияет на XP за отметку (5/8/10) */
    val complexity: String,
    /** Количественная цель (например 10 страниц); null = простая отметка */
    val targetValue: Double? = null,
    /** Единица количественной цели: «мин», «стр» */
    val unit: String? = null,
)

/**
 * T-12: каталог стартовых привычек — 30 проверенных малых действий, по 6 на
 * каждую характеристику. Только тексты и шаблоны, без логики: правила выбора
 * живут в NudgeEngine. Действия ≤10 минут или конкретная ежедневная мелочь.
 */
object AdviceCatalog {

    val ALL: List<StarterHabit> = listOf(
        // PHYSICS — физика
        StarterHabit("10 приседаний после подъёма", "💪", "PHYSICS", "S"),
        StarterHabit("Прогулка 15 минут после обеда", "🚶", "PHYSICS", "S", 15.0, "мин"),
        StarterHabit("20 отжиманий, разбитых на день", "🏋️", "PHYSICS", "M", 20.0, "раз"),
        StarterHabit("Растяжка 5 минут перед сном", "🧘", "PHYSICS", "S", 5.0, "мин"),
        StarterHabit("Пешком или на велосипеде вместо транспорта", "🚲", "PHYSICS", "M"),
        StarterHabit("Овощи в каждый приём пищи", "🥗", "PHYSICS", "M"),

        // MIND — разум
        StarterHabit("10 страниц книги перед сном", "📖", "MIND", "M", 10.0, "стр"),
        StarterHabit("5 новых английских слов в день", "🔤", "MIND", "S", 5.0, "слов"),
        StarterHabit("Дневник: три предложения о дне", "✍️", "MIND", "S"),
        StarterHabit("Головоломка или судоку 10 минут", "🧩", "MIND", "S", 10.0, "мин"),
        StarterHabit("Подкаст или аудиокнига по дороге", "🎧", "MIND", "M", 20.0, "мин"),
        StarterHabit("Один короткий онлайн-урок в день", "📚", "MIND", "M", 15.0, "мин"),

        // MONEY — деньги
        StarterHabit("Записать траты сразу же", "💸", "MONEY", "S"),
        StarterHabit("Разобрать чеки и переводы за день", "🧾", "MONEY", "S"),
        StarterHabit("Кофе с собой вместо кофейни", "☕️", "MONEY", "M"),
        StarterHabit("Перевести 100 ₽ в копилку", "💰", "MONEY", "S", 100.0, "₽"),
        StarterHabit("Один день без покупок", "🛍", "MONEY", "M"),
        StarterHabit("Проверить одну подписку и решить её судьбу", "🔍", "MONEY", "S"),

        // SOCIAL — харизма
        StarterHabit("Написать одному человеку доброе слово", "💬", "SOCIAL", "S"),
        StarterHabit("Позвонить родным, а не отписаться в чате", "📞", "SOCIAL", "M"),
        StarterHabit("Сказать «да» одному приглашению", "🙋", "SOCIAL", "M"),
        StarterHabit("Задать один вопрос на встрече вместо молчания", "🤝", "SOCIAL", "S"),
        StarterHabit("Поблагодарить человека за что-то конкретное", "🙏", "SOCIAL", "S"),
        StarterHabit("Встретиться с другом лицом к лицу", "🎲", "SOCIAL", "L"),

        // DISCIPLINE — дисциплина
        StarterHabit("Убрать телефон на 1 час", "📵", "DISCIPLINE", "M", 60.0, "мин"),
        StarterHabit("Встать по будильнику без повтора", "⏰", "DISCIPLINE", "S"),
        StarterHabit("Утром записать 3 главные задачи дня", "📝", "DISCIPLINE", "S"),
        StarterHabit("5 минут разбора стола перед работой", "🧹", "DISCIPLINE", "S", 5.0, "мин"),
        StarterHabit("Лечь спать до 23:30", "🌙", "DISCIPLINE", "M"),
        StarterHabit("Первые 30 минут утра без соцсетей", "🌅", "DISCIPLINE", "M", 30.0, "мин"),
    )

    fun forCharacteristic(key: String): List<StarterHabit> = ALL.filter { it.characteristic == key }

    /** Пакет для payload совета: "habit|emoji|title|char|complexity|target|unit", пустое поле = null. */
    fun serialize(starter: StarterHabit): String = listOf(
        "habit",
        starter.emoji,
        starter.title,
        starter.characteristic,
        starter.complexity,
        starter.targetValue?.toString().orEmpty(),
        starter.unit.orEmpty(),
    ).joinToString("|")

    fun parseStarterHabit(payload: String?): StarterHabit? {
        if (payload == null) return null
        val parts = payload.split("|")
        if (parts.size != 7 || parts[0] != "habit") return null
        return StarterHabit(
            title = parts[2],
            emoji = parts[1],
            characteristic = parts[3],
            complexity = parts[4],
            targetValue = parts[5].toDoubleOrNull(),
            unit = parts[6].ifEmpty { null },
        )
    }
}
