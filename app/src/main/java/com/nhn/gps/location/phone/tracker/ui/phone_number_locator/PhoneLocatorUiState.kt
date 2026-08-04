package com.nhn.gps.location.phone.tracker.ui.phone_number_locator

import com.nhn.gps.location.phone.tracker.data.model.UserLocation
import com.nhn.gps.location.phone.tracker.data.model.UserProfile

sealed interface PhoneLocatorUiState {
    data object Idle : PhoneLocatorUiState
    data object Loading : PhoneLocatorUiState
    data class Success(val profile: UserProfile, val location: UserLocation?, val address: String? = null) : PhoneLocatorUiState
    data object NoResult : PhoneLocatorUiState
    data class Error(val message: String) : PhoneLocatorUiState
}
