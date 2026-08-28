package com.questline.app.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.questline.app.data.AppRepo
import com.questline.app.data.Category
import com.questline.app.data.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Экран «Задачи»: инбокс / сегодня, создание и редактирование, закрытие.
 * Все списки — hot-флоу над DAO с WhileSubscribed(5000).
 */
class TasksViewModel(private val repo: AppRepo) : ViewModel() {

    enum class Tab { INBOX, TODAY }

    private val _tab = MutableStateFlow(Tab.INBOX)
    val tab: StateFlow<Tab> = _tab.asStateFlow()

    fun selectTab(tab: Tab) {
        _tab.value = tab
    }

    /** «Сегодня» фиксируем на момент создания VM — у экрана нет вечного воркера */
    val todayEpochDay: Long = AppRepo.todayEpochDay

    val inboxTasks: StateFlow<List<Task>> = repo.tasks.observeOpen()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Повторяющиеся в «отдыхе» между повторами не показываем */
    val todayTasks: StateFlow<List<Task>> = repo.tasks.observeForToday(todayEpochDay)
        .map { list ->
            list.filterNot { task ->
                val last = task.lastDoneEpochDay
                task.repeatIntervalDays > 0 && last != null &&
                    todayEpochDay < last + task.repeatIntervalDays
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val questCategories: StateFlow<List<Category>> = repo.categories.observeQuest()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** taskId == null → создание; иначе правка существующей записи */
    fun save(
        taskId: Long?,
        title: String,
        complexity: String,
        categoryId: Long?,
        dueEpochDay: Long?,
        repeatIntervalDays: Int,
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            if (taskId == null) {
                repo.tasks.insert(
                    Task(
                        title = title.trim(),
                        categoryId = categoryId,
                        repeatDaily = repeatIntervalDays > 0,
                        repeatIntervalDays = repeatIntervalDays,
                        dueEpochDay = dueEpochDay,
                        complexity = complexity,
                        createdAtMillis = System.currentTimeMillis(),
                    ),
                )
            } else {
                val existing = repo.tasks.byId(taskId) ?: return@launch
                repo.tasks.update(
                    existing.copy(
                        title = title.trim(),
                        categoryId = categoryId,
                        repeatDaily = repeatIntervalDays > 0,
                        repeatIntervalDays = repeatIntervalDays,
                        dueEpochDay = dueEpochDay,
                        complexity = complexity,
                    ),
                )
            }
        }
    }

    /**
     * Закрытие задачи: создаёт USER-квест с XP/монетами (кор-луп).
     * Повторяющаяся не закрывается навсегда, а отмечается последним днём выполнения.
     */
    fun complete(task: Task) {
        if (task.repeatIntervalDays > 0 && task.lastDoneEpochDay == todayEpochDay) return // уже отмечена сегодня
        viewModelScope.launch { repo.completeTaskAsQuest(task) }
    }

    fun delete(taskId: Long) {
        viewModelScope.launch { repo.tasks.delete(taskId) }
    }
}
