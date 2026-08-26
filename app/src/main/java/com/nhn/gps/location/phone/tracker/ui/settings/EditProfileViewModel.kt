package com.nhn.gps.location.phone.tracker.ui.settings

import androidx.lifecycle.viewModelScope
import com.nhn.gps.location.phone.tracker.base.BaseViewModel
import com.nhn.gps.location.phone.tracker.data.local.AppPreferences
import com.nhn.gps.location.phone.tracker.data.model.UserProfile
import com.nhn.gps.location.phone.tracker.data.repository.UserRepository
import com.nhn.gps.location.phone.tracker.util.AvatarHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface EditProfileUiState {
    object Idle : EditProfileUiState
    object Loading : EditProfileUiState
    object Success : EditProfileUiState
    object IdentityMismatch : EditProfileUiState
    data class Error(val message: String) : EditProfileUiState
}

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val userRepository: UserRepository
) : BaseViewModel() {

    private val _uiState = MutableStateFlow<EditProfileUiState>(EditProfileUiState.Idle)
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    private val _initialName = MutableStateFlow("")
    private val _currentName = MutableStateFlow("")
    val currentName: StateFlow<String> = _currentName.asStateFlow()

    private val _initialAvatarKey = MutableStateFlow("")
    private val _currentAvatarKey = MutableStateFlow("")
    val currentAvatarKey: StateFlow<String> = _currentAvatarKey.asStateFlow()

    private val _canEditProfile = MutableStateFlow(false)
    val canEditProfile: StateFlow<Boolean> = _canEditProfile.asStateFlow()

    val isChanged: MutableStateFlow<Boolean> = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            val name = appPreferences.userName.first()
            val avatarKey = AvatarHelper.normalizeKey(appPreferences.userAvatarKey.first())
            
            _initialName.value = name
            _currentName.value = name
            
            _initialAvatarKey.value = avatarKey
            _currentAvatarKey.value = avatarKey

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

    fun selectPresetAvatar(avatarKey: String) {
        // Keep the selection as a draft. Firebase and the local cache are committed
        // together only after the user taps Save and the remote write succeeds.
        _currentAvatarKey.value = AvatarHelper.normalizeKey(avatarKey)
        checkChanges()
    }

    private fun checkChanges() {
        val nameChanged = _currentName.value.trim() != _initialName.value.trim() && _currentName.value.isNotBlank()
        val avatarChanged = _currentAvatarKey.value != _initialAvatarKey.value
        isChanged.value = nameChanged || avatarChanged
    }

    fun saveChanges() {
        val newName = _currentName.value.trim()
        val newAvatarKey = AvatarHelper.normalizeKey(_currentAvatarKey.value)
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
                    phone = appPreferences.userPhone.first(),
                    avatarUrl = appPreferences.userAvatar.first(),
                    avatarKey = newAvatarKey
                )

                // Update Firebase before committing the local cache.
                userRepository.saveUserProfile(verifiedUid, updatedProfile)

                appPreferences.setUserName(newName)
                appPreferences.setUserAvatarKey(newAvatarKey)
                appPreferences.setLocalAvatarPath(null)

                _initialName.value = newName
                _initialAvatarKey.value = newAvatarKey
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
        val authUid = userRepository.getCurrentUserId()
        val matches = !localUid.isNullOrBlank() && !authUid.isNullOrBlank() && localUid == authUid
        _canEditProfile.value = matches
        if (!matches) {
            isChanged.value = false
            _uiState.value = EditProfileUiState.IdentityMismatch
        }
        return matches
    }

    fun resetState() {
        _uiState.value = EditProfileUiState.Idle
    }
}
