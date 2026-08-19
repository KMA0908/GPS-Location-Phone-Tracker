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

    val isChanged: MutableStateFlow<Boolean> = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            val name = appPreferences.userName.first()
            _initialName.value = name
            _currentName.value = name
        }
    }

    fun onNameChanged(newName: String) {
        _currentName.value = newName
        isChanged.value = newName.trim() != _initialName.value.trim() && newName.isNotBlank()
    }

    fun saveChanges() {
        val newName = _currentName.value.trim()
        if (newName.isBlank() || !isChanged.value) return

        launchCatching {
            _uiState.value = EditProfileUiState.Loading
            val uid = appPreferences.userId.first() ?: return@launchCatching
            
            // Get current full profile to avoid losing other fields
            val existingUser = userRepository.findUserByPhone(appPreferences.userPhone.first())
            
            val updatedProfile = UserProfile(
                uid = uid,
                name = newName,
                phone = appPreferences.userPhone.first(),
                avatarUrl = appPreferences.userAvatar.first(),
                avatarKey = appPreferences.userAvatarKey.first()
            )

            userRepository.saveUserProfile(uid, updatedProfile)
            appPreferences.setUserName(newName)
            
            _uiState.value = EditProfileUiState.Success
            _initialName.value = newName
            isChanged.value = false
        }
    }

    fun resetState() {
        _uiState.value = EditProfileUiState.Idle
    }
}
