package com.questline.app.ui.habits

import com.questline.app.ui.util.ruPlural

/**
 * Событие вехи стрика (T-07) для полноэкранного празднования: конфетти
 * QuestEffects + строка «🔥 Серия 7 дней! +10 монет» (формы слов — по
 * правилам русского множественного числа). seq перезапускает анимацию;
 * emit'ится из HabitsViewModel и TodayViewModel.
 */
data class HabitMilestonePulse(
    val habitId: Long,
    val streak: Int,
    val coins: Int,
    val seq: Long,
) {
    val headline: String get() = "🔥 Серия $streak ${ruPlural(streak, "день", "дня", "дней")}! +$coins ${ruPlural(coins, "монета", "монеты", "монет")}"
}
