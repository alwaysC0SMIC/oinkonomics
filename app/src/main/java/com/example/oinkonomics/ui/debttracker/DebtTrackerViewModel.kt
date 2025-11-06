package com.example.oinkonomics.ui.debttracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.oinkonomics.data.Debt
import com.example.oinkonomics.data.MissingUserException
import com.example.oinkonomics.data.OinkonomicsRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DebtUiState(
	val isLoading: Boolean = true,
	val debts: List<Debt> = emptyList(),
	val totalPaid: Double = 0.0,
	val totalLeft: Double = 0.0,
	val totalDebt: Double = 0.0,
	val errorMessage: String? = null,
	val sessionExpired: Boolean = false
)

class DebtTrackerViewModel(
	private val repository: OinkonomicsRepository,
	private val userId: Long
) : ViewModel() {

	private val _uiState = MutableStateFlow(DebtUiState())
	val uiState: StateFlow<DebtUiState> = _uiState

	init { refresh() }

	fun refresh() {
		if (userId < 0) return
		viewModelScope.launch {
			_uiState.update { it.copy(isLoading = true, errorMessage = null, sessionExpired = false) }
			try {
				val list = repository.getDebts(userId)
				val total = list.sumOf { it.totalAmount }
				val paid = list.sumOf { it.paidAmount }
				val left = (total - paid).coerceAtLeast(0.0)
				_uiState.update { it.copy(isLoading = false, debts = list, totalDebt = total, totalPaid = paid, totalLeft = left) }
			} catch (ex: MissingUserException) {
				_uiState.update { it.copy(isLoading = false, sessionExpired = true, errorMessage = ex.message) }
			} catch (ex: Exception) {
				_uiState.update { it.copy(isLoading = false, errorMessage = ex.message ?: "Unable to load debts") }
			}
		}
	}

	fun addDebt(name: String, total: Double, paid: Double, due: LocalDate) {
		if (userId < 0) return
		viewModelScope.launch {
			try {
				repository.createDebt(userId, name, total, paid, due)
				refresh()
			} catch (ex: MissingUserException) {
				_uiState.update { it.copy(errorMessage = ex.message, sessionExpired = true) }
			} catch (ex: Exception) {
				_uiState.update { it.copy(errorMessage = ex.message ?: "Unable to add debt") }
			}
		}
	}

	fun updateDebt(debt: Debt) {
		if (userId < 0) return
		viewModelScope.launch {
			try {
				val ok = repository.updateDebt(debt)
				if (ok) refresh() else _uiState.update { it.copy(errorMessage = "Unable to update debt") }
			} catch (ex: MissingUserException) {
				_uiState.update { it.copy(errorMessage = ex.message, sessionExpired = true) }
			} catch (ex: Exception) {
				_uiState.update { it.copy(errorMessage = ex.message ?: "Unable to update debt") }
			}
		}
	}

	fun removeDebt(id: Long) {
		if (userId < 0) return
		viewModelScope.launch {
			try {
				val ok = repository.deleteDebt(id, userId)
				if (ok) refresh() else _uiState.update { it.copy(errorMessage = "Unable to remove debt") }
			} catch (ex: MissingUserException) {
				_uiState.update { it.copy(errorMessage = ex.message, sessionExpired = true) }
			} catch (ex: Exception) {
				_uiState.update { it.copy(errorMessage = ex.message ?: "Unable to remove debt") }
			}
		}
	}

	class Factory(
		private val repository: OinkonomicsRepository,
		private val userId: Long
	) : ViewModelProvider.Factory {
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			if (modelClass.isAssignableFrom(DebtTrackerViewModel::class.java)) {
				@Suppress("UNCHECKED_CAST")
				return DebtTrackerViewModel(repository, userId) as T
			}
			throw IllegalArgumentException("Unknown ViewModel class")
		}
	}
}