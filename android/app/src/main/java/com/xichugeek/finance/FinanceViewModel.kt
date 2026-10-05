package com.xichugeek.finance

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xichugeek.finance.data.AccountEntity
import com.xichugeek.finance.data.CategoryEntity
import com.xichugeek.finance.data.FinanceDatabase
import com.xichugeek.finance.data.FinanceRepository
import com.xichugeek.finance.data.TransactionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FinanceUiState(
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
)

class FinanceViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FinanceRepository(FinanceDatabase.get(application))

    val state: StateFlow<FinanceUiState> = combine(
        repository.accounts,
        repository.categories,
        repository.transactions,
    ) { accounts, categories, transactions ->
        FinanceUiState(accounts, categories, transactions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FinanceUiState())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        viewModelScope.launch {
            try {
                repository.seedIfEmpty()
            } catch (e: Exception) {
                _error.value = e.message ?: "初始化本地数据失败"
            }
        }
    }

    fun clearError() { _error.value = null }
    fun showError(message: String) { _error.value = message }

    private fun runAction(onSuccess: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                onSuccess()
            } catch (e: Exception) {
                _error.value = e.message ?: "操作失败，请重试"
            }
        }
    }

    fun addAccount(name: String, kind: String, onSuccess: () -> Unit = {}) =
        runAction(onSuccess) { repository.addAccount(name, kind) }

    fun addCategory(name: String, type: String, onSuccess: () -> Unit = {}) =
        runAction(onSuccess) { repository.addCategory(name, type) }

    fun saveTransaction(transaction: TransactionEntity, onSuccess: () -> Unit) =
        runAction(onSuccess) {
            val snapshot = state.value
            require(snapshot.accounts.any { it.id == transaction.accountId }) { "请选择账户" }
            require(snapshot.categories.any { it.id == transaction.categoryId && it.type == transaction.type }) { "请选择对应的分类" }
            repository.saveTransaction(transaction)
        }

    fun deleteTransaction(id: Long, onSuccess: () -> Unit) =
        runAction(onSuccess) { repository.deleteTransaction(id) }
}
