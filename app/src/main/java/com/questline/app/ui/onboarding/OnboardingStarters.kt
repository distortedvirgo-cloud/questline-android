package com.questline.app.ui.onboarding

/* Шаг 2 онбординга (N-03): каталог стартеров AdviceCatalog, сгруппированный
 * по характеристикам, у каждого — крошечная тёплая подпись. Выбор тапом,
 * не больше трёх. Из стартера собирается ежедневная привычка без напоминания.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.questline.app.data.habits.Habit
import com.questline.app.domain.advice.AdviceCatalog
import com.questline.app.domain.advice.StarterHabit
import com.questline.app.domain.habits.HabitEngine
import com.questline.app.ui.theme.Q

/** Порядок групп каталога и русские имена характеристик. */
private val groupOrder = listOf(
    "PHYSICS" to "Физика",
    "MIND" to "Разум",
    "MONEY" to "Деньги",
    "SOCIAL" to "Харизма",
    "DISCIPLINE" to "Дисциплина",
)

/** Крошечные подписи: каталог даёт только название и количественную цель. */
private val starterNotes = mapOf(
    "10 приседаний после подъёма" to "Меньше минуты, прямо у кровати",
    "Прогулка 15 минут после обеда" to "Тихий круг вокруг дома",
    "20 отжиманий, разбитых на день" to "Можно по два за раз — день длинный",
    "Растяжка 5 минут перед сном" to "Спина скажет спасибо",
    "Пешком или на велосипеде вместо транспорта" to "Одна поездка в день — уже счёт",
    "Овощи в каждый приём пищи" to "Хоть один огурец — засчитано",
    "10 страниц книги перед сном" to "Пара глав вместо ленты",
    "5 новых английских слов в день" to "Карточки на телефоне — и хватит",
    "Дневник: три предложения о дне" to "Больше трёх и не нужно",
    "Головоломка или судоку 10 минут" to "Разминка для головы",
    "Подкаст или аудиокнига по дороге" to "Дорога уже есть — включи и слушай",
    "Один короткий онлайн-урок в день" to "Пятнадцать минут — это урок",
    "Записать траты сразу же" to "Прямо у кассы, за десять секунд",
    "Разобрать чеки и переводы за день" to "Вечерний минутный разбор",
    "Кофе с собой вместо кофейни" to "Карман целее, бодрость та же",
    "Перевести 100 ₽ в копилку" to "Сто рублей — не страшно",
    "Один день без покупок" to "Список желаний подождёт до завтра",
    "Проверить одну подписку и решить её судьбу" to "Одна подписка в день",
    "Написать одному человеку доброе слово" to "Одно сообщение — и день теплее",
    "Позвонить родным, а не отписаться в чате" to "Голос слышнее галочки",
    "Сказать «да» одному приглашению" to "Люди обычно рады",
    "Задать один вопрос на встрече вместо молчания" to "Один вопрос — уже участие",
    "Поблагодарить человека за что-то конкретное" to "Конкретика ценнее общего",
    "Встретиться с другом лицом к лицу" to "Кофе раз в неделю можно",
    "Убрать телефон на 1 час" to "Час без ленты — свободно",
    "Встать по будильнику без повтора" to "Сначала ноги, потом серия",
    "Утром записать 3 главные задачи дня" to "Три строчки — и день с планом",
    "5 минут разбора стола перед работой" to "Чистый стол — ясная голова",
    "Лечь спать до 23:30" to "Завтра скажет спасибо",
    "Первые 30 минут утра без соцсетей" to "Утро — твоё, не ленты",
)

private fun StarterHabit.note(): String = starterNotes[title]
    ?: (targetValue?.let { "Цель: ${it.toInt()} ${unit.orEmpty().trim()} в день" } ?: "Крошечный ежедневный шаг")

/** Привычка из стартера: ежедневная, без напоминания, создана сегодня. */
internal fun StarterHabit.toHabit(today: Long): Habit = Habit(
    title = title,
    emoji = emoji,
    characteristic = characteristic,
    scheduleType = HabitEngine.SCHEDULE_DAILY,
    targetValue = targetValue,
    unit = unit?.takeIf { targetValue != null },
    complexity = complexity,
    createdAt = today,
)

/** Шаг 2: сетка стартеров по характеристикам с выбором тапом. */
@Composable
internal fun StarterPickSection(
    selected: List<StarterHabit>,
    limitHint: Boolean,
    onToggle: (StarterHabit) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        Text("Выбери 1–3 привычки", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Хоть одна — уже старт. Позже можно добавить в «Привычках».",
            style = MaterialTheme.typography.bodyMedium,
            color = Q.inkMuted,
        )
        Spacer(Modifier.height(14.dp))
        groupOrder.forEach { (key, label) ->
            Text(label, style = MaterialTheme.typography.labelMedium, color = Q.inkMuted)
            Spacer(Modifier.height(8.dp))
            AdviceCatalog.forCharacteristic(key).forEach { starter ->
                StarterCard(
                    starter = starter,
                    isSelected = selected.contains(starter),
                    onToggle = onToggle,
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun StarterCard(
    starter: StarterHabit,
    isSelected: Boolean,
    onToggle: (StarterHabit) -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isSelected) Q.accentSoft else Q.surface)
            .border(1.dp, if (isSelected) Q.accent else Q.border, shape)
            .clickable { onToggle(starter) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(starter.emoji, fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(starter.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(starter.note(), style = MaterialTheme.typography.bodySmall, color = Q.inkMuted)
        }
        if (isSelected) {
            Text("✓", color = Q.accent, fontWeight = FontWeight.SemiBold)
        }
    }
}
