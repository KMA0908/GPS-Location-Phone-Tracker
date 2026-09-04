package com.nhn.gps.location.phone.tracker.ui.settings

import androidx.lifecycle.viewModelScope
import com.nhn.gps.location.phone.tracker.base.BaseViewModel
import com.nhn.gps.location.phone.tracker.data.local.AppPreferences
import com.nhn.gps.location.phone.tracker.data.model.Country
import com.nhn.gps.location.phone.tracker.data.model.UserProfile
import com.nhn.gps.location.phone.tracker.data.repository.CountryRepository
import com.nhn.gps.location.phone.tracker.data.repository.UserRepository
import com.nhn.gps.location.phone.tracker.util.AvatarHelper
import com.nhn.gps.location.phone.tracker.util.PhoneNumberFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

sealed interface EditProfileUiState {
    object Idle : EditProfileUiState
    object Loading : EditProfileUiState
    object Success : EditProfileUiState
    data class Error(val message: String) : EditProfileUiState
}

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val userRepository: UserRepository,
    countryRepository: CountryRepository,
    private val phoneNumberFormatter: PhoneNumberFormatter,
) : BaseViewModel() {

    private val countries = countryRepository.getCountries()
    private val defaultCountry = countries.firstOrNull {
        it.iso.equals(Locale.getDefault().country, ignoreCase = true)
    } ?: DEFAULT_COUNTRY

    private val _uiState = MutableStateFlow<EditProfileUiState>(EditProfileUiState.Idle)
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    private val _initialName = MutableStateFlow("")
    private val _currentName = MutableStateFlow("")
    val currentName: StateFlow<String> = _currentName.asStateFlow()

    private val _initialAvatarKey = MutableStateFlow("")
    private val _currentAvatarKey = MutableStateFlow("")
    val currentAvatarKey: StateFlow<String> = _currentAvatarKey.asStateFlow()

    private val _initialPhone = MutableStateFlow("")
    private val _currentPhone = MutableStateFlow("")
    val currentPhone: StateFlow<String> = _currentPhone.asStateFlow()

    private val _selectedCountry = MutableStateFlow(defaultCountry)
    val selectedCountry: StateFlow<Country> = _selectedCountry.asStateFlow()

    private val _canEditProfile = MutableStateFlow(false)
    val canEditProfile: StateFlow<Boolean> = _canEditProfile.asStateFlow()

    val isChanged: MutableStateFlow<Boolean> = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            val name = appPreferences.userName.first()
            val avatarKey = AvatarHelper.normalizeKey(appPreferences.userAvatarKey.first())
            val storedPhone = appPreferences.userPhone.first().trim()
            val country = countryFor(storedPhone)
            val canonicalPhone = storedPhone.takeIf(String::isNotEmpty)?.let {
                runCatching { phoneNumberFormatter.normalize(country.dialCode, it) }.getOrDefault(it)
            }.orEmpty()
            
            _initialName.value = name
            _currentName.value = name
            
            _initialAvatarKey.value = avatarKey
            _currentAvatarKey.value = avatarKey

            _selectedCountry.value = country
            _initialPhone.value = canonicalPhone
            _currentPhone.value = phoneNumberFormatter.toNationalNumber(country.dialCode, canonicalPhone)

            updateIdentityState(appPreferences.userId.first())
        }
    }

    fun onNameChanged(newName: String) {
        _currentName.value = newName
        checkChanges()
    }

    fun onAvatarChanged(newKey: String) {
        _currentAvatarKey.value = AvatarHelper.normalizeKey(newKey)
        checkChanges()
    }

    fun onPhoneChanged(newPhone: String) {
        _currentPhone.value = newPhone
        checkChanges()
    }

    fun onCountryChanged(country: Country) {
        _selectedCountry.value = country
        checkChanges()
    }

    fun isValidPhone(phone: String = _currentPhone.value): Boolean =
        phoneNumberFormatter.isValid(_selectedCountry.value.dialCode, phone)

    fun selectPresetAvatar(avatarKey: String) {
        // Keep the selection as a draft. Firebase and the local cache are committed
        // together only after the user taps Save and the remote write succeeds.
        _currentAvatarKey.value = AvatarHelper.normalizeKey(avatarKey)
        checkChanges()
    }

    private fun checkChanges() {
        val nameChanged = _currentName.value.trim() != _initialName.value.trim() && _currentName.value.isNotBlank()
        val avatarChanged = _currentAvatarKey.value != _initialAvatarKey.value
        val phone = canonicalPhoneOrNull()
        val phoneChanged = phone != null && phone != _initialPhone.value
        isChanged.value = (nameChanged || avatarChanged || phoneChanged) && phone != null
    }

    fun saveChanges() {
        val newName = _currentName.value.trim()
        val newAvatarKey = AvatarHelper.normalizeKey(_currentAvatarKey.value)
        val newPhone = canonicalPhoneOrNull() ?: return
        if (newName.isBlank() || !isChanged.value) return

        viewModelScope.launch {
            val uid = appPreferences.userId.first()
            if (!updateIdentityState(uid)) return@launch

            _uiState.value = EditProfileUiState.Loading
            try {
                val verifiedUid = checkNotNull(uid)

                val updatedProfile = UserProfile(
                    uid = verifiedUid,
                    name = newName,
                    phone = newPhone,
                    avatarUrl = appPreferences.userAvatar.first(),
                    avatarKey = newAvatarKey
                )

                // Update Firebase before committing the local cache.
                userRepository.saveUserProfile(verifiedUid, updatedProfile)

                appPreferences.setUserName(newName)
                appPreferences.setUserPhone(newPhone)
                appPreferences.setUserAvatarKey(newAvatarKey)
                appPreferences.setLocalAvatarPath(null)

                _initialName.value = newName
                _initialAvatarKey.value = newAvatarKey
                _initialPhone.value = newPhone
                isChanged.value = false
                _uiState.value = EditProfileUiState.Success
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.value = EditProfileUiState.Error(
                    error.localizedMessage ?: error.javaClass.simpleName
                )
            }
        }
    }

    private fun updateIdentityState(localUid: String?): Boolean {
        val hasInstallationProfile = !localUid.isNullOrBlank()
        _canEditProfile.value = hasInstallationProfile
        if (!hasInstallationProfile) {
            isChanged.value = false
            _uiState.value = EditProfileUiState.Error("Installation profile is unavailable")
        }
        return hasInstallationProfile
    }

    fun resetState() {
        _uiState.value = EditProfileUiState.Idle
    }

    private fun canonicalPhoneOrNull(): String? {
        val phone = _currentPhone.value.trim()
        if (phone.isEmpty()) return ""
        return runCatching {
            phoneNumberFormatter.normalize(_selectedCountry.value.dialCode, phone)
        }.getOrNull()
    }

    private fun countryFor(phone: String): Country {
        if (!phone.startsWith("+")) {
            // Builds before E.164 support accepted Vietnamese national numbers only.
            return countries.firstOrNull { it.iso.equals("VN", ignoreCase = true) }
                ?: DEFAULT_COUNTRY
        }
        return countries
            .filter { phone.startsWith(it.dialCode) }
            .maxByOrNull { it.dialCode.length }
            ?: defaultCountry
    }

    private companion object {
        val DEFAULT_COUNTRY = Country("Vietnam", "+84", "VN", "🇻🇳")
    }
}
