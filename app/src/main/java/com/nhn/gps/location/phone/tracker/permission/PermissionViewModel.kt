package com.nhn.gps.location.phone.tracker.permission

import androidx.lifecycle.viewModelScope
import com.nhn.gps.location.phone.tracker.base.BaseViewModel
import com.nhn.gps.location.phone.tracker.data.local.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PermissionViewModel @Inject constructor(
    private val appPreferences: AppPreferences
) : BaseViewModel() {

    private val _permissionState = MutableStateFlow(PermissionState())
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    private val _navigateToMain = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToMain: SharedFlow<Unit> = _navigateToMain.asSharedFlow()

    private val _requestPermission = MutableSharedFlow<PermissionType>(extraBufferCapacity = 1)
    val requestPermission: SharedFlow<PermissionType> = _requestPermission.asSharedFlow()

    init {
        // Thứ 3: Quan sát trạng thái quyền từ DataStore để cập nhật UI
        viewModelScope.launch {
            appPreferences.isLocationEnabled.collectLatest { enabled ->
                _permissionState.update { it.copy(isLocationGranted = enabled) }
            }
        }
        viewModelScope.launch {
            appPreferences.isCameraEnabled.collectLatest { enabled ->
                _permissionState.update { it.copy(isCameraGranted = enabled) }
            }
        }
        viewModelScope.launch {
            appPreferences.isNotificationEnabled.collectLatest { enabled ->
                _permissionState.update { it.copy(isNotificationGranted = enabled) }
            }
        }
    }

    fun updatePermission(type: PermissionType, isGranted: Boolean) {
        viewModelScope.launch {
            when (type) {
                PermissionType.LOCATION -> appPreferences.setLocationEnabled(isGranted)
                PermissionType.CAMERA -> appPreferences.setCameraEnabled(isGranted)
                PermissionType.NOTIFICATION -> appPreferences.setNotificationEnabled(isGranted)
            }
        }
    }

    fun onPermissionSwitchClicked(type: PermissionType, isChecked: Boolean) {
        if (isChecked) {
            // Thứ 1: Click cho quyền thì request luôn, không hiện dialog
            _requestPermission.tryEmit(type)
        } else {
            // Thứ 3: Click tắt thì chỉ cập nhật trạng thái trong DataStore thành false
            updatePermission(type, false)
        }
    }

    fun onContinueClicked() {
        viewModelScope.launch {
            appPreferences.setPermissionShown(true)
            _navigateToMain.tryEmit(Unit)
        }
    }

    fun onLaterClicked() {
        viewModelScope.launch {
            appPreferences.setPermissionShown(true)
            _navigateToMain.tryEmit(Unit)
        }
    }
}

data class PermissionState(
    val isLocationGranted: Boolean = false,
    val isCameraGranted: Boolean = false,
    val isNotificationGranted: Boolean = false
)

enum class PermissionType {
    LOCATION, CAMERA, NOTIFICATION
}
