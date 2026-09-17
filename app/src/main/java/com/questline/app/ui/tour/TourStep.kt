package com.questline.app.ui.tour

/** Один шаг экскурсии: текст и цель подсветки. */
data class TourStep(
    val id: String,
    val title: String,
    val body: String,
    /** Id зарегистрированной цели; null — центрированный слайд без дырки. */
    val targetId: String? = null,
    /** Маршрут NavHost («today»/«habits»/«money»/«profile»); null — вкладку не переключать. */
    val tab: String? = null,
)
