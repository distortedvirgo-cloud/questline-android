package com.questline.app.ui.tour

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/** Реестр живых целей тура: id → прямоугольник в координатах корня схемы. */
object TourRegistry {
    val bounds = mutableStateMapOf<String, Rect>()

    fun put(id: String, rect: Rect) {
        bounds[id] = rect
    }

    fun remove(id: String) {
        bounds.remove(id)
    }

    fun get(id: String): Rect? = bounds[id]
}

/** Регистрация цели: позиция обновляется при каждом лейауте, снимается при уходе с экрана. */
fun Modifier.tourTarget(id: String): Modifier = composed {
    val current = rememberUpdatedState(id)
    DisposableEffect(Unit) {
        onDispose { TourRegistry.remove(current.value) }
    }
    onGloballyPositioned { TourRegistry.put(current.value, it.boundsInRoot()) }
}
