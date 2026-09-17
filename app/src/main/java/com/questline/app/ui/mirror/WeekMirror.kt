package com.questline.app.ui.mirror

/* «Зеркало недели» v3 (T-14): недельный обзор из 4 секций — итог недели,
 * консистентность по характеристикам, советы на следующую неделю и
 * карточка-акцент «Фокус недели». Данные собирает WeekMirrorViewModel одним
 * расчётом (buildWeekMirrorUi); советы детерминированы датой (seed = epochDay),
 * поэтому между пересозданиями экрана фокус недели стабилен.
 * Секции — в WeekMirrorParts.kt (лимит ≤300 строк на файл).
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.questline.app.data.AppRepo
import com.questline.app.ui.theme.Q
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Composable
fun WeekMirrorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { AppRepo.get(context) }
    val vm: WeekMirrorViewModel = viewModel(initializer = { WeekMirrorViewModel(repo) })
    val ui by vm.ui.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Q.bg,
        topBar = { MirrorTopBar(onBack) },
    ) { innerPadding ->
        val s = ui
        // До загрузки данных карточки не рисуем вовсе
        if (s != null) Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            WeekSummarySection(s)
            ConsistencySection(s)
            WeekTipsSection(s)
            WeekFocusCard(s)
        }
    }
}

@Composable
private fun MirrorTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Q.bg)
            .statusBarsPadding()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Назад",
                tint = Q.ink,
            )
        }
        Text(
            "Зеркало недели",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/**
 * Данные зеркала собираются одним расчётом на входе на экран. «Сегодня»
 * фиксируется на момент создания VM, как в остальных экранах, — советы
 * и фокус недели в рамках дня стабильны.
 */
class WeekMirrorViewModel(private val repo: AppRepo) : ViewModel() {

    private val _ui = MutableStateFlow<WeekMirrorUi?>(null)
    val ui: StateFlow<WeekMirrorUi?> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            _ui.value = buildWeekMirrorUi(repo, AppRepo.todayEpochDay)
        }
    }
}
