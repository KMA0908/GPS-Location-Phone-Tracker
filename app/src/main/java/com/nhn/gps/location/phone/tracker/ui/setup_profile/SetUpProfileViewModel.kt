package com.nhn.gps.location.phone.tracker.ui.setup_profile

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.nhn.gps.location.phone.tracker.base.BaseViewModel
import com.nhn.gps.location.phone.tracker.data.local.AppPreferences
import com.nhn.gps.location.phone.tracker.data.model.UserProfile
import com.nhn.gps.location.phone.tracker.data.repository.UserRepository
import com.nhn.gps.location.phone.tracker.navigation.AppDestination
import com.nhn.gps.location.phone.tracker.navigation.NavigationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface SetUpProfileUiState {
    object Idle : SetUpProfileUiState
    object Loading : SetUpProfileUiState
    object Success : SetUpProfileUiState
    object PhoneAlreadyExists : SetUpProfileUiState
    data class Error(val message: String) : SetUpProfileUiState
}

@HiltViewModel
class SetUpProfileViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val navigationManager: NavigationManager,
    private val userRepository: UserRepository
) : BaseViewModel() {

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _phone = MutableStateFlow("")
    val phone: StateFlow<String> = _phone.asStateFlow()

    private val _avatarUri = MutableStateFlow<Uri?>(null)
    val avatarUri: StateFlow<Uri?> = _avatarUri.asStateFlow()

    private val _uiState = MutableStateFlow<SetUpProfileUiState>(SetUpProfileUiState.Idle)
    val uiState: StateFlow<SetUpProfileUiState> = _uiState.asStateFlow()

    val isSaveEnabled: StateFlow<Boolean> = combine(_name, _phone, _uiState) { name, phone, state ->
        name.isNotBlank() && phone.isNotBlank() && isValidVietnamPhone(phone) && state !is SetUpProfileUiState.Loading
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun isValidVietnamPhone(phone: String): Boolean {
        return phone.matches(Regex("^(03|05|07|08|09)\\d{8}$"))
    }

    fun onNameChanged(name: String) {
        _name.value = name
    }

    fun onPhoneChanged(phone: String) {
        _phone.value = phone
    }

    fun onAvatarChanged(uri: Uri?) {
        _avatarUri.value = uri
    }

    fun onSaveClicked() {
        if (!isSaveEnabled.value) return

        launchCatching {
            _uiState.value = SetUpProfileUiState.Loading
            
            val phone = _phone.value
            val name = _name.value

            // 1. Kiểm tra số điện thoại đã tồn tại chưa
            val existingUser = userRepository.findUserByPhone(phone)
            
            if (existingUser != null) {
                // Nếu tồn tại: Tải thông tin về lưu local và đi tới Home (như đăng nhập)
                appPreferences.setUserId(existingUser.uid)
                appPreferences.setUserName(existingUser.name)
                appPreferences.setUserPhone(existingUser.phone)
                appPreferences.setUserAvatar(existingUser.avatarUrl)
                
                _uiState.value = SetUpProfileUiState.Success
                navigationManager.navigateTo(AppDestination.Home, clearStack = true)
                return@launchCatching
            }

            // 2. Nếu chưa tồn tại: Thực hiện tạo user mới (Sử dụng anonymous auth)
            val uid = userRepository.signInAnonymously()

            // 3. Upload avatar nếu có
            var avatarUrl = ""
            _avatarUri.value?.let { uri ->
                avatarUrl = userRepository.uploadAvatar(uid, uri)
            }

            val profile = UserProfile(
                uid = uid,
                name = name,
                phone = phone,
                avatarUrl = avatarUrl
            )

            // 4. Lưu profile lên Firebase
            userRepository.saveUserProfile(uid, profile)

            // 5. Lưu local preferences
            appPreferences.setUserId(uid)
            appPreferences.setUserName(name)
            appPreferences.setUserPhone(phone)
            appPreferences.setUserAvatar(avatarUrl)

            // 6. Thành công và Điều hướng
            _uiState.value = SetUpProfileUiState.Success
            navigationManager.navigateTo(AppDestination.Home, clearStack = true)
        }
    }
    
    fun resetState() {
        _uiState.value = SetUpProfileUiState.Idle
    }
}
