package com.questline.app.ui.tour

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

private const val TOUR_PREFS = "tour_prefs"
private const val KEY_DONE = "tour_v3_done"

/** Все 13 шагов экскурсии; тексты — по спецификации. */
val TOUR_STEPS: List<TourStep> = listOf(
    TourStep(
        id = "welcome",
        title = "Добро пожаловать в Questline!",
        body = "Это твоя RPG за реальные дела: XP, монеты и уровни за привычки, задачи и квесты. " +
            "Пройдёмся по четырём комнатам — потерпи две минуты.",
    ),
    TourStep(
        id = "today_stats",
        title = "Прогресс перед глазами",
        body = "Серия дней, монеты и уровень — главный экран отвечает на вопрос „как я сегодня?“. " +
            "Отмечай дела — числа растут.",
        targetId = "today_stats",
    ),
    TourStep(
        id = "bottom_nav",
        title = "Четыре комнаты",
        body = "Сегодня — что сделать сейчас. Привычки — ежедневная рутина. Деньги — бюджет и копилки. " +
            "Я — прогресс, темы и настройки.",
        targetId = "bottom_nav",
    ),
    TourStep(
        id = "habits_list",
        title = "Привычки",
        body = "Отмечай выполненное тапом по карточке. Стрики растут, вехи 7/30/100 дней дарят монеты и XP. " +
            "Тап по названию — детали, тепловая карта и заморозка.",
        targetId = "habits_list",
        tab = "habits",
    ),
    TourStep(
        id = "habits_add",
        title = "Создай первую",
        body = "„+“ внизу: эмодзи, характеристика, расписание (ежедневно, дни недели, N× в неделю, " +
            "раз в N дней), сложность и напоминание.",
        targetId = "habits_add",
    ),
    TourStep(
        id = "money_balance",
        title = "Сколько у тебя денег",
        body = "Укажи баланс один раз — дальше он пересчитывается сам по операциям. " +
            "Можно завести карты: каждая со своим остатком.",
        targetId = "money_balance",
        tab = "money",
    ),
    TourStep(
        id = "money_tabs",
        title = "Разделы денег",
        body = "План — бюджеты месяца и burn rate. Операции — все записи. Статистика — тренды. " +
            "Копилки — цели. ✨ AI — советы коуча.",
        targetId = "money_tabs",
    ),
    TourStep(
        id = "money_quickadd",
        title = "Быстрый ввод",
        body = "„+“ справа — расход или доход за 5 секунд: сумма → категория → Сохранить. " +
            "Баланс и бюджеты пересчитаются сами.",
        targetId = "money_quickadd",
    ),
    TourStep(
        id = "money_goals",
        title = "Копилки",
        body = "Накопи на что-то важное: цель, сумма, „Внести“ — и приложение покажет, " +
            "когда копилка закроется при текущем темпе.",
        targetId = "money_goals",
    ),
    TourStep(
        id = "profile_level",
        title = "Твой герой",
        body = "Уровень и XP растут от всего, что ты отмечаешь. Монеты — валюта: темы и рамки в Магазине.",
        targetId = "profile_level",
        tab = "profile",
    ),
    TourStep(
        id = "profile_chars",
        title = "Характеристики",
        body = "Физика, Разум, Деньги, Харизма, Дисциплина — прокачиваются отмеченными привычками и квестами. " +
            "Слабая сфера = кандидат на внимание.",
        targetId = "profile_chars",
    ),
    TourStep(
        id = "profile_menu",
        title = "Меню",
        body = "Зеркало недели — итоги и советы. Магазин тем. AI-коуч — идеи под твои данные. " +
            "Настройки и бэкап — там же.",
        targetId = "profile_menu",
    ),
    TourStep(
        id = "rule2min",
        title = "Правило 2 минут",
        body = "Любое полезное дело, которое занимает меньше двух минут, делай сразу. " +
            "Начни с крошечной привычки — серия важнее объёма. " +
            "И главное: пропуск — не провал; не пропускай дважды подряд.",
    ),
)

/** Состояние тура: активность, текущий шаг, флаг пройденности в «tour_prefs». */
class TourController(private val context: Context) {
    var active by mutableStateOf(false)
        private set
    var index by mutableIntStateOf(0)
        private set

    val step: TourStep
        get() = TOUR_STEPS[index.coerceIn(0, TOUR_STEPS.lastIndex)]

    val isLast: Boolean
        get() = index >= TOUR_STEPS.lastIndex

    fun start() {
        index = 0
        active = true
    }

    fun next() {
        if (isLast) finish() else index++
    }

    /** Завершение и «Пропустить»: любой выход закрывает тур и пишет флаг. */
    fun finish() {
        active = false
        markDone()
    }

    fun isDone(): Boolean = prefs().getBoolean(KEY_DONE, false)

    /** Сброс флага для повторного показа из Настроек. */
    fun reset() {
        prefs().edit().putBoolean(KEY_DONE, false).apply()
    }

    private fun markDone() {
        prefs().edit().putBoolean(KEY_DONE, true).apply()
    }

    private fun prefs() =
        context.getSharedPreferences(TOUR_PREFS, Context.MODE_PRIVATE)
}

@Composable
fun rememberTourController(context: Context): TourController =
    remember { TourController(context) }
