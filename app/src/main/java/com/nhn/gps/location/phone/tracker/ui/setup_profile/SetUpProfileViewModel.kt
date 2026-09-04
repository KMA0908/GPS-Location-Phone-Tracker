package com.nhn.gps.location.phone.tracker.ui.setup_profile

import androidx.lifecycle.viewModelScope
import com.google.firebase.functions.FirebaseFunctionsException
import com.nhn.gps.location.phone.tracker.base.BaseViewModel
import com.nhn.gps.location.phone.tracker.data.local.AppPreferences
import com.nhn.gps.location.phone.tracker.data.model.Country
import com.nhn.gps.location.phone.tracker.data.model.UserProfile
import com.nhn.gps.location.phone.tracker.data.repository.CountryRepository
import com.nhn.gps.location.phone.tracker.data.repository.UserRepository
import com.nhn.gps.location.phone.tracker.navigation.AppDestination
import com.nhn.gps.location.phone.tracker.navigation.NavigationManager
import com.nhn.gps.location.phone.tracker.util.AvatarHelper
import com.nhn.gps.location.phone.tracker.util.PhoneNumberFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Locale
import javax.inject.Inject

sealed interface SetUpProfileUiState {
    object Idle : SetUpProfileUiState
    object Loading : SetUpProfileUiState
    object Success : SetUpProfileUiState
    data class Error(val message: String) : SetUpProfileUiState
}

@HiltViewModel
class SetUpProfileViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val navigationManager: NavigationManager,
    private val userRepository: UserRepository,
    countryRepository: CountryRepository,
    private val phoneNumberFormatter: PhoneNumberFormatter,
) : BaseViewModel() {

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _phone = MutableStateFlow("")
    val phone: StateFlow<String> = _phone.asStateFlow()

    private val _selectedCountry = MutableStateFlow(
        countryRepository.getCountries().firstOrNull {
            it.iso.equals(Locale.getDefault().country, ignoreCase = true)
        } ?: DEFAULT_COUNTRY,
    )
    val selectedCountry: StateFlow<Country> = _selectedCountry.asStateFlow()

    private val _avatarKey = MutableStateFlow<String>(AvatarHelper.DEFAULT_AVATAR_KEY)
    val avatarKey: StateFlow<String> = _avatarKey.asStateFlow()

    private val _uiState = MutableStateFlow<SetUpProfileUiState>(SetUpProfileUiState.Idle)
    val uiState: StateFlow<SetUpProfileUiState> = _uiState.asStateFlow()

    val isSaveEnabled: StateFlow<Boolean> = combine(
        _name,
        _phone,
        _selectedCountry,
        _uiState,
    ) { name, phone, country, state ->
        name.isNotBlank() &&
            phoneNumberFormatter.isValid(country.dialCode, phone) &&
            state !is SetUpProfileUiState.Loading
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun isValidPhone(phone: String): Boolean =
        phoneNumberFormatter.isValid(_selectedCountry.value.dialCode, phone)

    fun onNameChanged(name: String) {
        _name.value = name
    }

    fun onPhoneChanged(phone: String) {
        _phone.value = phone
    }

    fun onCountryChanged(country: Country) {
        _selectedCountry.value = country
    }

    fun onAvatarChanged(avatarKey: String) {
        _avatarKey.value = AvatarHelper.normalizeKey(avatarKey)
    }

    fun onSaveClicked() {
        if (!isSaveEnabled.value) return

        launchCatching {
            _uiState.value = SetUpProfileUiState.Loading
            
            val phone = _phone.value.trim().takeIf(String::isNotEmpty)?.let {
                phoneNumberFormatter.normalize(_selectedCountry.value.dialCode, it)
            }.orEmpty()
            val name = _name.value.trim()

            val uid = try {
                userRepository.getOrCreateInstallationId()
            } catch (e: Exception) {
                _uiState.value = SetUpProfileUiState.Error(e.message ?: "Could not create installation profile")
                return@launchCatching
            }

            var profile = UserProfile(
                uid = uid,
                name = name,
                phone = phone,
                avatarUrl = "", // We no longer upload avatar from this screen
                avatarKey = AvatarHelper.normalizeKey(_avatarKey.value)
            )

            // Phone is a locator index. It is never used to adopt another
            // installation's profile.
            var savedUid = uid
            try {
                userRepository.saveUserProfile(savedUid, profile)
            } catch (error: FirebaseFunctionsException) {
                if (!error.isOwnershipCollision()) throw error
                savedUid = userRepository.createReplacementInstallationId()
                profile = profile.copy(uid = savedUid)
                userRepository.saveUserProfile(savedUid, profile)
            }

            // Save local preferences.
            appPreferences.setUserId(savedUid)
            appPreferences.setUserName(name)
            appPreferences.setUserPhone(phone)
            appPreferences.setUserAvatar("")
            appPreferences.setUserAvatarKey(AvatarHelper.normalizeKey(_avatarKey.value))

            // Finish and navigate.
            _uiState.value = SetUpProfileUiState.Success
            navigateAfterProfileCreated()
        }
    }

    private suspend fun navigateAfterProfileCreated() {
        navigationManager.navigateTo(AppDestination.Home, clearStack = true)
    }
    
    fun resetState() {
        _uiState.value = SetUpProfileUiState.Idle
    }

    private companion object {
        val DEFAULT_COUNTRY = Country("Vietnam", "+84", "VN", "🇻🇳")
    }

    private fun FirebaseFunctionsException.isOwnershipCollision(): Boolean =
        code == FirebaseFunctionsException.Code.PERMISSION_DENIED ||
            code == FirebaseFunctionsException.Code.FAILED_PRECONDITION
}
