package com.nhn.gps.location.phone.tracker.ui.settings

import androidx.lifecycle.viewModelScope
import com.nhn.gps.location.phone.tracker.base.BaseViewModel
import com.nhn.gps.location.phone.tracker.data.local.AppPreferences
import com.nhn.gps.location.phone.tracker.data.model.UserProfile
import com.nhn.gps.location.phone.tracker.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

    val isChanged: MutableStateFlow<Boolean> = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            val name = appPreferences.userName.first()
            val avatarKey = appPreferences.userAvatarKey.first()
            
            _initialName.value = name
            _currentName.value = name
            
            _initialAvatarKey.value = avatarKey
            _currentAvatarKey.value = avatarKey
        }
    }

    fun onNameChanged(newName: String) {
        _currentName.value = newName
        checkChanges()
    }

    fun onAvatarChanged(newKey: String) {
        _currentAvatarKey.value = newKey
        checkChanges()
    }

    fun selectPresetAvatar(avatarKey: String) {
        viewModelScope.launch {
            appPreferences.setUserAvatarKey(avatarKey)
            appPreferences.setLocalAvatarPath(null)
            _initialAvatarKey.value = avatarKey
            _currentAvatarKey.value = avatarKey
            checkChanges()
        }
    }

    private fun checkChanges() {
        val nameChanged = _currentName.value.trim() != _initialName.value.trim() && _currentName.value.isNotBlank()
        val avatarChanged = _currentAvatarKey.value != _initialAvatarKey.value
        isChanged.value = nameChanged || avatarChanged
    }

    fun saveChanges() {
        val newName = _currentName.value.trim()
        val newAvatarKey = _currentAvatarKey.value
        if (newName.isBlank() || !isChanged.value) return

        launchCatching {
            _uiState.value = EditProfileUiState.Loading
            val uid = appPreferences.userId.first() ?: return@launchCatching

            val updatedProfile = UserProfile(
                uid = uid,
                name = newName,
                phone = appPreferences.userPhone.first(),
                avatarUrl = appPreferences.userAvatar.first(),
                avatarKey = newAvatarKey
            )

            // Update Firebase
            userRepository.saveUserProfile(uid, updatedProfile)
            
            // Update Local Preferences
            appPreferences.setUserName(newName)
            appPreferences.setUserAvatarKey(newAvatarKey)
            
            // Update Initial State
            _initialName.value = newName
            _initialAvatarKey.value = newAvatarKey
            
            isChanged.value = false
            _uiState.value = EditProfileUiState.Success
        }
    }

    fun resetState() {
        _uiState.value = EditProfileUiState.Idle
    }
}
