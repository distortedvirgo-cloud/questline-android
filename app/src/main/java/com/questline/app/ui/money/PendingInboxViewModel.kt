package com.questline.app.ui.money

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.questline.app.data.AppRepo
import com.questline.app.data.Category
import com.questline.app.data.PendingTxn
import com.questline.app.data.Txn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PendingInboxViewModel(private val repo: AppRepo) : ViewModel() {

    val pending: StateFlow<List<PendingTxn>> = repo.pending.observePending()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val financeCategories: StateFlow<List<Category>> = repo.categories.observeFinance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Подтвердить: создаём Txn на сегодня в выбранную категорию. */
    fun confirm(item: PendingTxn, categoryId: Long) {
        viewModelScope.launch {
            repo.txns.insert(
                Txn(
                    amountMinor = item.amountMinor,
                    type = item.type,
                    categoryId = categoryId,
                    epochDay = item.epochDay,
                    note = item.title.ifBlank { "Из уведомления" },
                    source = "BANK_PUSH",
                    pendingId = item.id,
                    createdAtMillis = System.currentTimeMillis(),
                ),
            )
            repo.pending.setStatus(item.id, "CONFIRMED")
        }
    }

    fun discard(item: PendingTxn) {
        viewModelScope.launch { repo.pending.setStatus(item.id, "DISCARDED") }
    }
}

fun pendingInboxFactory(repo: AppRepo): ViewModelProvider.Factory = viewModelFactory {
    initializer { PendingInboxViewModel(repo) }
}
