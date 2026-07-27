package com.nhn.gps.location.phone.tracker.ui.permission

import androidx.lifecycle.viewModelScope
import com.nhn.gps.location.phone.tracker.base.BaseViewModel
import com.nhn.gps.location.phone.tracker.data.local.AppPreferences
import com.nhn.gps.location.phone.tracker.navigation.AppDestination
import com.nhn.gps.location.phone.tracker.navigation.NavigationManager
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
    private val appPreferences: AppPreferences, private val navigationManager: NavigationManager
) : BaseViewModel() {

    private val _permissionState = MutableStateFlow(PermissionState())
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    private val _requestPermission = MutableSharedFlow<PermissionType>(extraBufferCapacity = 1)
    val requestPermission: SharedFlow<PermissionType> = _requestPermission.asSharedFlow()

    init {
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
            _requestPermission.tryEmit(type)
        } else {
            updatePermission(type, false)
        }
    }

    fun onContinueClicked() {
        viewModelScope.launch {
            appPreferences.setPermissionShown(true)
            navigationManager.navigateTo(AppDestination.SetUpProfile)
        }
    }

    fun onLaterClicked() {
        viewModelScope.launch {
            appPreferences.setPermissionShown(true)
            navigationManager.navigateTo(AppDestination.SetUpProfile)
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
