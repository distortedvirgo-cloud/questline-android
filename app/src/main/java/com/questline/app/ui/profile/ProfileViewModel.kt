package com.questline.app.ui.profile

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.questline.app.data.AppRepo
import com.questline.app.data.CoinsLedger
import com.questline.app.domain.ProgressionEngine
import kotlinx.coroutines.launch

class ProfileViewModel(private val repo: AppRepo) : ViewModel() {

    data class State(
        val level: Int = 1,
        val xpIntoLevel: Int = 0,
        val xpNeeded: ProgressionEngine.LevelState? = null,
        val coins: Int = 0,
        val keyXp: Map<String, Int> = emptyMap(),
        val recentCoins: List<CoinsLedger> = emptyList(),
    )

    val state = mutableStateOf(State())

    init {
        viewModelScope.launch {
            // История квестов загружается один раз (стрики/уровень статичны на сессию)
            val done = repo.quests.allDone()
            val levelState = ProgressionEngine.levelFromTotal(ProgressionEngine.totalXp(done))
            state.value = state.value.copy(
                level = levelState.level,
                xpIntoLevel = levelState.xpIntoLevel,
                xpNeeded = levelState,
                keyXp = ProgressionEngine.keyXp(done),
            )
        }
        viewModelScope.launch {
            repo.coins.observeTotalCoins().collect { total ->
                state.value = state.value.copy(coins = total)
            }
        }
        viewModelScope.launch {
            repo.coins.observeRecent(20).collect { list ->
                state.value = state.value.copy(recentCoins = list)
            }
        }
    }
}

fun profileVmFactory(context: Context): ViewModelProvider.Factory = viewModelFactory {
    initializer { ProfileViewModel(AppRepo.get(context.applicationContext)) }
}
