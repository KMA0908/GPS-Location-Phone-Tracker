package com.nhn.gps.location.phone.tracker.ui.phone_number_locator

import androidx.lifecycle.viewModelScope
import com.nhn.gps.location.phone.tracker.base.BaseViewModel
import com.nhn.gps.location.phone.tracker.data.repository.PhoneLocatorRepository
import com.nhn.gps.location.phone.tracker.util.PhoneNumberFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PhoneLocatorViewModel @Inject constructor(
    private val repository: PhoneLocatorRepository,
    private val phoneFormatter: PhoneNumberFormatter
) : BaseViewModel() {

    private val _uiState = MutableStateFlow<PhoneLocatorUiState>(PhoneLocatorUiState.Idle)
    val uiState: StateFlow<PhoneLocatorUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun findUser(dialCode: String, phone: String) {
        if (phone.isBlank()) return

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.value = PhoneLocatorUiState.Loading
            
            // Thực hiện Normalize số điện thoại trước khi truyền vào Repository
            val normalizedPhone = phoneFormatter.normalize(dialCode, phone)
            
            repository.findUserByPhone(normalizedPhone).fold(
                onSuccess = { (profile, location) ->
                    val address = location?.let { 
                        repository.getAddressFromLocation(it.latitude, it.longitude) 
                    }
                    _uiState.value = PhoneLocatorUiState.Success(profile, location, address)
                },
                onFailure = { error ->
                    val errorMessage = error.message ?: ""
                    val state = when {
                        errorMessage.contains(NO_USER_FOUND, ignoreCase = true) -> PhoneLocatorUiState.NoResult
                        else -> PhoneLocatorUiState.Error(errorMessage)
                    }
                    _uiState.value = state
                }
            )
        }
    }

    fun resetState() {
        _uiState.value = PhoneLocatorUiState.Idle
    }

    companion object {
        private const val NO_USER_FOUND = "No user found"
    }
}
