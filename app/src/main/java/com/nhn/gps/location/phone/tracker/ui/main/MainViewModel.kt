package com.nhn.gps.location.phone.tracker.ui.main

import androidx.lifecycle.viewModelScope
import com.nhn.gps.location.phone.tracker.base.BaseViewModel
import com.nhn.gps.location.phone.tracker.data.local.AppPreferences
import com.nhn.gps.location.phone.tracker.data.model.FamousPlaceModel
import com.nhn.gps.location.phone.tracker.data.repository.ExploreRepository
import com.nhn.gps.location.phone.tracker.data.repository.ExploreResult
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
    private val exploreRepository: ExploreRepository
) : BaseViewModel() {

    private val _isLocationPermanentlyEnabled = MutableStateFlow(false)
    val isLocationPermanentlyEnabled: StateFlow<Boolean> =
        _isLocationPermanentlyEnabled.asStateFlow()

    private val _isSessionLocationGranted = MutableStateFlow(false)
    val isSessionLocationGranted: StateFlow<Boolean> = _isSessionLocationGranted.asStateFlow()

    private val _selectedPlaceId = MutableStateFlow<String?>(null)
    val selectedPlaceId: StateFlow<String?> = _selectedPlaceId.asStateFlow()

    private val _famousPlaces = MutableStateFlow<List<FamousPlaceModel>>(emptyList())

    val userAvatar: StateFlow<String> = preferences.userAvatar.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ""
    )

    val uiState = combine(
        preferences.appOpenCount,
        navigationManager.currentDestination,
        _famousPlaces
    ) { appOpenCount, destination, famousPlaces ->
        MainUiState(
            appOpenCount = appOpenCount,
            currentRoute = destination?.route,
            famousPlaces = famousPlaces
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
        fetchFamousPlaces()
    }

    private fun fetchFamousPlaces() {
        viewModelScope.launch {
            when (val result = exploreRepository.getAllFamousPlaces()) {
                is ExploreResult.Success -> {
                    _famousPlaces.value = result.data
                }
                else -> {
                    _famousPlaces.value = emptyList()
                }
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
                        val userId = preferences.userId.first()
                        if (userId.isNullOrBlank()) AppDestination.SetUpProfile else AppDestination.Home
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
        val userId = preferences.userId.first()
        val isPermissionShown = preferences.isPermissionShown.first()

        val (destination, clearStack) = when {
            !userId.isNullOrBlank() -> AppDestination.Home to true
            !isPermissionShown -> AppDestination.Permission to false
            else -> AppDestination.SetUpProfile to true
        }
        navigationManager.navigateTo(destination, clearStack = clearStack)
    }

    fun openMap() {
        navigationManager.navigateTo(AppDestination.Map)
    }

    fun openPhoneLocator() {
        navigationManager.navigateTo(AppDestination.PhoneLocator)
    }

    fun goHome() {
        navigationManager.navigateTo(AppDestination.Home)
    }

    fun openViewFriends() {
        navigationManager.navigateTo(AppDestination.MyFriend)
    }

    fun openSettings() {
        navigationManager.navigateTo(AppDestination.Settings)
    }

    fun openSettingsLanguage() {
        navigationManager.navigateTo(AppDestination.SettingsLanguage)
    }

    fun updateLocationPermissionStatus(isGranted: Boolean) {
        viewModelScope.launch {
            preferences.setLocationEnabled(isGranted)
        }
    }

    fun setSelectedPlaceId(placeId: String?) {
        _selectedPlaceId.value = placeId
    }

    fun navigateBack(): Boolean {
        return navigationManager.navigateBack()
    }
}
