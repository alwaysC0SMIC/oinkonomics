package com.example.oinkonomics.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.oinkonomics.data.BudgetCategory
import com.example.oinkonomics.data.Expense
import com.example.oinkonomics.data.MissingUserException
import com.example.oinkonomics.data.MonthlySpendingGoal
import com.example.oinkonomics.data.OinkonomicsRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// CAPTURES ALL UI STATE NEEDED BY THE HOME SCREEN.
data class HomeUiState(
    val isLoading: Boolean = true,
    val expenses: List<Expense> = emptyList(),
    val categories: List<BudgetCategory> = emptyList(),
    val totalBudget: Double = 0.0,
    val totalSpent: Double = 0.0,
    val monthlyGoal: MonthlySpendingGoal? = null,
    val currentMonthSpent: Double = 0.0,
    val dateRangeStart: LocalDate? = null,
    val dateRangeEnd: LocalDate? = null,
    val errorMessage: String? = null,
    val sessionExpired: Boolean = false
)

// COORDINATES HOME SCREEN DATA LOADING AND MUTATIONS.
class HomeViewModel(
    private val repository: OinkonomicsRepository,
    private val userId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        // LOADS INITIAL DATA WHEN THE VIEWMODEL IS CREATED.
        refreshData()
    }

    fun refreshData() {
        // PULLS LATEST CATEGORIES AND EXPENSES FROM THE REPOSITORY.
        if (userId < 0) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, sessionExpired = false) }
            try {
                val categories = repository.getBudgetCategories(userId)
                val expenses = repository.getExpenses(userId)
                val monthlyGoal = repository.getMonthlySpendingGoal(userId)
                val totalBudget = categories.sumOf { it.maxAmount }
                val totalSpent = expenses.sumOf { it.amount }
                
                // CALCULATE CURRENT MONTH SPENDING
                val currentMonth = YearMonth.now()
                val currentMonthSpent = expenses
                    .filter { YearMonth.from(it.localDate) == currentMonth }
                    .sumOf { it.amount }
                
                _uiState.value = HomeUiState(
                    isLoading = false,
                    expenses = expenses,
                    categories = categories,
                    totalBudget = totalBudget,
                    totalSpent = totalSpent,
                    monthlyGoal = monthlyGoal,
                    currentMonthSpent = currentMonthSpent,
                    errorMessage = null,
                    sessionExpired = false
                )
            } catch (ex: MissingUserException) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = ex.message, sessionExpired = true)
                }
            } catch (ex: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = ex.message ?: "Unable to load data",
                        sessionExpired = false
                    )
                }
            }
        }
    }

    fun addExpense(
        name: String,
        amount: Double,
        date: LocalDate,
        categoryId: Long?,
        receiptUri: String?
    ) {
        // CREATES A NEW EXPENSE AND REFRESHES ON SUCCESS.
        if (userId < 0) return
        viewModelScope.launch {
            try {
                repository.createExpense(userId, categoryId, name, amount, date, receiptUri)
                refreshData()
            } catch (ex: MissingUserException) {
                _uiState.update {
                    it.copy(errorMessage = ex.message, sessionExpired = true)
                }
            } catch (ex: Exception) {
                _uiState.update {
                    it.copy(errorMessage = ex.message ?: "Unable to add expense", sessionExpired = false)
                }
            }
        }
    }

    fun updateExpense(
        expenseId: Long,
        name: String,
        amount: Double,
        date: LocalDate,
        categoryId: Long?,
        receiptUri: String?
    ) {
        // UPDATES AN EXISTING EXPENSE AND REFRESHES ON SUCCESS.
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
            } catch (ex: MissingUserException) {
                _uiState.update {
                    it.copy(errorMessage = ex.message, sessionExpired = true)
                }
            } catch (ex: Exception) {
                _uiState.update {
                    it.copy(errorMessage = ex.message ?: "Unable to update expense", sessionExpired = false)
                }
            }
        }
    }

    fun removeExpense(expenseId: Long) {
        // DELETES AN EXPENSE AND REFRESHES IF THE OPERATION SUCCEEDS.
        if (userId < 0) return
        viewModelScope.launch {
            try {
                val deleted = repository.deleteExpense(expenseId, userId)
                if (deleted) {
                    refreshData()
                } else {
                    _uiState.update { it.copy(errorMessage = "Unable to remove expense") }
                }
            } catch (ex: MissingUserException) {
                _uiState.update {
                    it.copy(errorMessage = ex.message, sessionExpired = true)
                }
            } catch (ex: Exception) {
                _uiState.update {
                    it.copy(errorMessage = ex.message ?: "Unable to remove expense", sessionExpired = false)
                }
            }
        }
    }

    fun setMonthlyGoal(minGoal: Double?, maxGoal: Double?) {
        // UPDATES THE USER'S MONTHLY SPENDING GOALS.
        if (userId < 0) return
        viewModelScope.launch {
            try {
                val success = repository.setMonthlySpendingGoal(userId, minGoal, maxGoal)
                if (success) {
                    refreshData()
                } else {
                    _uiState.update {
                        it.copy(errorMessage = "Unable to set monthly goal", sessionExpired = false)
                    }
                }
            } catch (ex: MissingUserException) {
                _uiState.update {
                    it.copy(errorMessage = ex.message, sessionExpired = true)
                }
            } catch (ex: Exception) {
                _uiState.update {
                    it.copy(errorMessage = ex.message ?: "Unable to set monthly goal", sessionExpired = false)
                }
            }
        }
    }

    fun setDateRange(startDate: LocalDate?, endDate: LocalDate?) {
        // SETS THE DATE RANGE FOR FILTERING EXPENSES.
        _uiState.update { it.copy(dateRangeStart = startDate, dateRangeEnd = endDate) }
    }

    fun onSessionInvalidHandled() {
        // CLEARS SESSION FLAGS AFTER NAVIGATING AWAY.
        _uiState.update { it.copy(sessionExpired = false, errorMessage = null) }
    }

    class Factory(
        private val repository: OinkonomicsRepository,
        private val userId: Long
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                // PROVIDES A HOMEVIEWMODEL INSTANCE WITH PROVIDED DEPENDENCIES.
                return HomeViewModel(repository, userId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
