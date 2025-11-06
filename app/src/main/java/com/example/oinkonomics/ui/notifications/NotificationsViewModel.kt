package com.example.oinkonomics.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.oinkonomics.data.MissingUserException
import com.example.oinkonomics.data.OinkonomicsRepository
import com.example.oinkonomics.data.Subscription
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// HOLDS UI STATE FOR THE SUBSCRIPTIONS SCREEN.
data class SubscriptionsUiState(
	val isLoading: Boolean = true,
	val subscriptions: List<Subscription> = emptyList(),
	val monthlyTotal: Double = 0.0,
	val errorMessage: String? = null,
	val sessionExpired: Boolean = false
)

class NotificationsViewModel(
	private val repository: OinkonomicsRepository,
	private val userId: Long
) : ViewModel() {

	private val _uiState = MutableStateFlow(SubscriptionsUiState())
	val uiState: StateFlow<SubscriptionsUiState> = _uiState

	init {
		refresh()
	}

	fun refresh() {
		if (userId < 0) return
		viewModelScope.launch {
			_uiState.update { it.copy(isLoading = true, errorMessage = null, sessionExpired = false) }
			try {
				val list = repository.getSubscriptions(userId)
				_uiState.update {
					it.copy(
						isLoading = false,
						subscriptions = list,
						monthlyTotal = list.sumOf { sub -> sub.amount },
						errorMessage = null
					)
				}
			} catch (ex: MissingUserException) {
				_uiState.update { it.copy(isLoading = false, sessionExpired = true, errorMessage = ex.message) }
			} catch (ex: Exception) {
				_uiState.update { it.copy(isLoading = false, errorMessage = ex.message ?: "Unable to load subscriptions") }
			}
		}
	}

	fun addSubscription(name: String, amount: Double, date: LocalDate, iconUri: String?) {
		if (userId < 0) return
		viewModelScope.launch {
			try {
				repository.createSubscription(userId, name, amount, date, iconUri)
				refresh()
			} catch (ex: MissingUserException) {
				_uiState.update { it.copy(errorMessage = ex.message, sessionExpired = true) }
			} catch (ex: Exception) {
				_uiState.update { it.copy(errorMessage = ex.message ?: "Unable to add subscription") }
			}
		}
	}

	fun updateSubscription(subscription: Subscription) {
		if (userId < 0) return
		viewModelScope.launch {
			try {
				val ok = repository.updateSubscription(subscription)
				if (ok) refresh() else _uiState.update { it.copy(errorMessage = "Unable to update subscription") }
			} catch (ex: MissingUserException) {
				_uiState.update { it.copy(errorMessage = ex.message, sessionExpired = true) }
			} catch (ex: Exception) {
				_uiState.update { it.copy(errorMessage = ex.message ?: "Unable to update subscription") }
			}
		}
	}

	fun removeSubscription(subscriptionId: Long) {
		if (userId < 0) return
		viewModelScope.launch {
			try {
				val ok = repository.deleteSubscription(subscriptionId, userId)
				if (ok) refresh() else _uiState.update { it.copy(errorMessage = "Unable to remove subscription") }
			} catch (ex: MissingUserException) {
				_uiState.update { it.copy(errorMessage = ex.message, sessionExpired = true) }
			} catch (ex: Exception) {
				_uiState.update { it.copy(errorMessage = ex.message ?: "Unable to remove subscription") }
			}
		}
	}

	class Factory(
		private val repository: OinkonomicsRepository,
		private val userId: Long
	) : ViewModelProvider.Factory {
		override fun <T : ViewModel> create(modelClass: Class<T>): T {
			if (modelClass.isAssignableFrom(NotificationsViewModel::class.java)) {
				@Suppress("UNCHECKED_CAST")
				return NotificationsViewModel(repository, userId) as T
			}
			throw IllegalArgumentException("Unknown ViewModel class")
		}
	}
}