package com.nhn.gps.location.phone.tracker.ui.main

import androidx.lifecycle.viewModelScope
import com.nhn.gps.location.phone.tracker.base.BaseViewModel
import com.nhn.gps.location.phone.tracker.data.local.AppPreferences
import com.nhn.gps.location.phone.tracker.navigation.AppDestination
import com.nhn.gps.location.phone.tracker.navigation.NavigationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val navigationManager: NavigationManager,
) : BaseViewModel() {

    private val _isLocationPermanentlyEnabled = MutableStateFlow(false)
    val isLocationPermanentlyEnabled: StateFlow<Boolean> =
        _isLocationPermanentlyEnabled.asStateFlow()

    private val _isSessionLocationGranted = MutableStateFlow(false)
    val isSessionLocationGranted: StateFlow<Boolean> = _isSessionLocationGranted.asStateFlow()

    val userAvatar: StateFlow<String> = preferences.userAvatar.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ""
    )

    val uiState = combine(
        preferences.appOpenCount,
        navigationManager.currentDestination,
    ) { appOpenCount, destination ->
        MainUiState(
            appOpenCount = appOpenCount,
            currentRoute = destination?.route,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(),
    )

    init {
        launchCatching {
            preferences.incrementAppOpenCount()
        }
        viewModelScope.launch {
            preferences.isLocationEnabled.collect {
                _isLocationPermanentlyEnabled.value = it
            }
        }
    }

    fun setSessionLocationGranted(granted: Boolean) {
        _isSessionLocationGranted.value = granted
    }

    fun handleIntent(destinationRoute: String?) {
        viewModelScope.launch {
            if (destinationRoute != null) {
                val destination = when (destinationRoute) {
                    "permission" -> AppDestination.Permission
                    "home" -> {
                        val userName = preferences.userName.first()
                        if (userName.isBlank()) AppDestination.SetUpProfile else AppDestination.Home
                    }

                    else -> AppDestination.Home
                }
                navigationManager.navigateTo(destination, clearStack = true)
            } else {
                checkInitialDestination()
            }
        }
    }

    private suspend fun checkInitialDestination() {
        val isPermissionShown = preferences.isPermissionShown.first()
        val userName = preferences.userName.first()

        val (destination, clearStack) = when {
            !isPermissionShown -> AppDestination.Permission to false
            userName.isBlank() -> AppDestination.SetUpProfile to true
            else -> AppDestination.Home to true
        }
        navigationManager.navigateTo(destination, clearStack = clearStack)
    }

    fun openMap() {
        navigationManager.navigateTo(AppDestination.Map)
    }

    fun goHome() {
        navigationManager.navigateTo(AppDestination.Home)
    }

    fun updateLocationPermissionStatus(isGranted: Boolean) {
        viewModelScope.launch {
            preferences.setLocationEnabled(isGranted)
        }
    }

    fun navigateBack(): Boolean {
        return navigationManager.navigateBack()
    }
}
