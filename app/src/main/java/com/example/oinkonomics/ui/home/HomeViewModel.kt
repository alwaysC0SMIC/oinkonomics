package com.example.oinkonomics.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.oinkonomics.data.BudgetCategory
import com.example.oinkonomics.data.Expense
import com.example.oinkonomics.data.OinkonomicsRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val expenses: List<Expense> = emptyList(),
    val categories: List<BudgetCategory> = emptyList(),
    val totalBudget: Double = 0.0,
    val totalSpent: Double = 0.0,
    val errorMessage: String? = null
)

class HomeViewModel(
    private val repository: OinkonomicsRepository,
    private val userId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        refreshData()
    }

    fun refreshData() {
        if (userId < 0) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val categories = repository.getBudgetCategories(userId)
                val expenses = repository.getExpenses(userId)
                val totalBudget = categories.sumOf { it.maxAmount }
                val totalSpent = categories.sumOf { it.spentAmount }
                _uiState.value = HomeUiState(
                    isLoading = false,
                    expenses = expenses,
                    categories = categories,
                    totalBudget = totalBudget,
                    totalSpent = totalSpent,
                    errorMessage = null
                )
            } catch (ex: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = ex.message ?: "Unable to load data")
                }
            }
        }
    }

    fun addExpense(
        name: String,
        amount: Double,
        date: LocalDate,
        categoryId: Long,
        receiptUri: String?
    ) {
        if (userId < 0) return
        viewModelScope.launch {
            try {
                repository.createExpense(userId, categoryId, name, amount, date, receiptUri)
                refreshData()
            } catch (ex: Exception) {
                _uiState.update { it.copy(errorMessage = ex.message ?: "Unable to add expense") }
            }
        }
    }

    fun updateExpense(
        expenseId: Long,
        name: String,
        amount: Double,
        date: LocalDate,
        categoryId: Long,
        receiptUri: String?
    ) {
        if (userId < 0) return
        viewModelScope.launch {
            try {
                val current = _uiState.value.expenses.firstOrNull { it.id == expenseId }
                if (current == null) {
                    _uiState.update { it.copy(errorMessage = "Expense not found") }
                    return@launch
                }
                val updated = current.copy(
                    name = name,
                    amount = amount,
                    categoryId = categoryId,
                    dateIso = date.toString(),
                    receiptUri = receiptUri
                )
                val success = repository.updateExpense(updated)
                if (success) {
                    refreshData()
                } else {
                    _uiState.update { it.copy(errorMessage = "Unable to update expense") }
                }
            } catch (ex: Exception) {
                _uiState.update { it.copy(errorMessage = ex.message ?: "Unable to update expense") }
            }
        }
    }

    fun removeExpense(expenseId: Long) {
        if (userId < 0) return
        viewModelScope.launch {
            try {
                val deleted = repository.deleteExpense(expenseId, userId)
                if (deleted) {
                    refreshData()
                } else {
                    _uiState.update { it.copy(errorMessage = "Unable to remove expense") }
                }
            } catch (ex: Exception) {
                _uiState.update { it.copy(errorMessage = ex.message ?: "Unable to remove expense") }
            }
        }
    }

    class Factory(
        private val repository: OinkonomicsRepository,
        private val userId: Long
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return HomeViewModel(repository, userId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
