package com.questline.app.ui.habits

/**
 * Событие вехи стрика (T-07) для полноэкранного празднования: конфетти
 * QuestEffects + строка «🔥 Серия 7 дней! +10 монет». seq перезапускает
 * анимацию; emit'ится из HabitsViewModel и TodayViewModel.
 */
data class HabitMilestonePulse(
    val habitId: Long,
    val streak: Int,
    val coins: Int,
    val seq: Long,
) {
    val headline: String get() = "🔥 Серия $streak дней! +$coins монет"
}
