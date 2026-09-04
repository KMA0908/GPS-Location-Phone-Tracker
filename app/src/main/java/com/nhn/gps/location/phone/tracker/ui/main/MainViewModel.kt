package com.nhn.gps.location.phone.tracker.ui.main

import androidx.lifecycle.viewModelScope
import android.util.Log
import com.nhn.gps.location.phone.tracker.base.BaseViewModel
import com.nhn.gps.location.phone.tracker.data.local.AppPreferences
import com.nhn.gps.location.phone.tracker.data.model.FamousPlaceModel
import com.nhn.gps.location.phone.tracker.data.repository.ExploreRepository
import com.nhn.gps.location.phone.tracker.data.repository.ExploreResult
import com.nhn.gps.location.phone.tracker.data.repository.GeocodedLocation
import com.nhn.gps.location.phone.tracker.data.repository.GeocoderUnavailableException
import com.nhn.gps.location.phone.tracker.data.repository.PhoneLocatorRepository
import com.nhn.gps.location.phone.tracker.data.repository.UserRepository
import com.nhn.gps.location.phone.tracker.navigation.AppDestination
import com.nhn.gps.location.phone.tracker.navigation.NavigationManager
import com.nhn.gps.location.phone.tracker.ui.location.MapRouteRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ZoneAddressSearchState {
    data object Idle : ZoneAddressSearchState
    data object Loading : ZoneAddressSearchState
    data class Success(val location: GeocodedLocation) : ZoneAddressSearchState
    data object NotFound : ZoneAddressSearchState
    data class Error(val messageRes: Int) : ZoneAddressSearchState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val navigationManager: NavigationManager,
    private val exploreRepository: ExploreRepository,
    private val phoneLocatorRepository: PhoneLocatorRepository,
    private val userRepository: UserRepository,
) : BaseViewModel() {

    private val _zoneAddressSearchState = MutableStateFlow<ZoneAddressSearchState>(ZoneAddressSearchState.Idle)
    val zoneAddressSearchState = _zoneAddressSearchState.asStateFlow()
    private var zoneAddressSearchJob: Job? = null

    fun searchZoneAddress(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _zoneAddressSearchState.value = ZoneAddressSearchState.Error(com.nhn.gps.location.phone.tracker.R.string.search_address_empty)
            return
        }

        zoneAddressSearchJob?.cancel()
        _zoneAddressSearchState.value = ZoneAddressSearchState.Loading

        zoneAddressSearchJob = viewModelScope.launch {
            try {
                Log.d(TAG, "zone_geocoder_search_started queryLength=${trimmed.length}")
                phoneLocatorRepository.getLocationFromAddress(trimmed)
                    .onSuccess { location ->
                        if (location == null) {
                            Log.d(TAG, "zone_geocoder_search_not_found")
                            _zoneAddressSearchState.value = ZoneAddressSearchState.NotFound
                        } else {
                            Log.d(TAG, "zone_geocoder_search_success")
                            _zoneAddressSearchState.value = ZoneAddressSearchState.Success(location)
                        }
                    }
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                        Log.e(TAG, "zone_geocoder_search_failed type=${error.javaClass.simpleName}", error)
                        _zoneAddressSearchState.value = ZoneAddressSearchState.Error(
                            if (error is GeocoderUnavailableException) {
                                com.nhn.gps.location.phone.tracker.R.string.search_location_unavailable
                            } else {
                                com.nhn.gps.location.phone.tracker.R.string.search_location_error
                            }
                        )
                    }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "zone_geocoder_search_failed type=${error.javaClass.simpleName}", error)
                _zoneAddressSearchState.value = ZoneAddressSearchState.Error(com.nhn.gps.location.phone.tracker.R.string.search_location_error)
            }
        }
    }

    fun consumeZoneAddressSearchResult() {
        _zoneAddressSearchState.value = ZoneAddressSearchState.Idle
    }

    fun cancelZoneAddressSearch() {
        zoneAddressSearchJob?.cancel()
        zoneAddressSearchJob = null
        _zoneAddressSearchState.value = ZoneAddressSearchState.Idle
    }

    private companion object {
        const val TAG = "MainViewModel"
    }

    private val _isLocationPermanentlyEnabled = MutableStateFlow(false)
    val isLocationPermanentlyEnabled: StateFlow<Boolean> =
        _isLocationPermanentlyEnabled.asStateFlow()

    private val _isSessionLocationGranted = MutableStateFlow(false)
    val isSessionLocationGranted: StateFlow<Boolean> = _isSessionLocationGranted.asStateFlow()

    private val _selectedPlaceId = MutableStateFlow<String?>(null)
    val selectedPlaceId: StateFlow<String?> = _selectedPlaceId.asStateFlow()

    private val _selectedFamousCategoryId = MutableStateFlow(1)
    val selectedFamousCategoryId: StateFlow<Int> = _selectedFamousCategoryId.asStateFlow()

    private val _mapRouteRequest = MutableStateFlow<MapRouteRequest?>(null)
    val mapRouteRequest: StateFlow<MapRouteRequest?> = _mapRouteRequest.asStateFlow()

    private val _famousPlaces = MutableStateFlow<List<FamousPlaceModel>>(emptyList())

    val userAvatar: StateFlow<String> = preferences.userAvatar.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ""
    )

    val userAvatarKey: StateFlow<String> = preferences.userAvatarKey.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ""
    )

    val localAvatarPath: StateFlow<String?> = preferences.localAvatarPath.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    val userName: StateFlow<String> = preferences.userName.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ""
    )

    val userPhone: StateFlow<String> = preferences.userPhone.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ""
    )

    val userId: StateFlow<String?> = preferences.userId.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    val isLocationSharingEnabled: StateFlow<Boolean> = preferences.isLocationSharingEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false
    )

    private val _isLocationSharingUpdating = MutableStateFlow(false)
    val isLocationSharingUpdating: StateFlow<Boolean> =
        _isLocationSharingUpdating.asStateFlow()

    val isNotificationEnabled: StateFlow<Boolean> = preferences.isNotificationEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false
    )

    val selectedLanguage: StateFlow<String> = preferences.selectedLanguage.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = "en"
    )

    val uiState = combine(
        preferences.appOpenCount,
        navigationManager.currentDestination,
        _famousPlaces
    ) { appOpenCount, destination, famousPlaces ->
        MainUiState(
            appOpenCount = appOpenCount,
            currentDestination = destination,
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
                    "setup_profile" -> AppDestination.SetUpProfile
                    "home" -> {
                        val userId = preferences.userId.first()
                        if (userId.isNullOrBlank()) AppDestination.SetUpProfile else AppDestination.Home
                    }
                    AppDestination.AlertDetail.route -> AppDestination.AlertDetail
                    AppDestination.ZoneAlerts.route -> AppDestination.ZoneAlerts

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

        val (destination, clearStack) = when {
            !userId.isNullOrBlank() -> AppDestination.Home to true
            else -> AppDestination.SetUpProfile to true
        }
        navigationManager.navigateTo(destination, clearStack = clearStack)
    }

    fun openMap() {
        navigationManager.navigateTo(AppDestination.Map)
    }

    fun openRouteOnMap(
        destinationName: String,
        latitude: Double,
        longitude: Double,
        friendId: String? = null,
    ) {
        val request = MapRouteRequest(
            destinationName = destinationName,
            latitude = latitude,
            longitude = longitude,
            friendId = friendId,
        )
        Log.d(TAG, "map_route_request_created source=${if (friendId != null) "friend" else "famous_place"} requestId=${request.requestId}")
        _mapRouteRequest.value = request
        navigationManager.navigateTo(AppDestination.Map)
    }

    fun consumeMapRouteRequest(requestId: String) {
        if (_mapRouteRequest.value?.requestId == requestId) {
            _mapRouteRequest.value = null
        }
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

    fun setLocationSharingEnabled(enabled: Boolean) {
        if (_isLocationSharingUpdating.value) return
        _isLocationSharingUpdating.value = true
        launchCatching {
            try {
                val uid = preferences.userId.first()?.takeIf(String::isNotBlank)
                    ?: error("Installation profile is unavailable")
                // The backend disables availability and removes the stored location in
                // one atomic update. Commit the local switch only after it succeeds.
                userRepository.setLocationSharing(uid, enabled)
                preferences.setLocationSharingEnabled(enabled)
            } finally {
                _isLocationSharingUpdating.value = false
            }
        }
    }

    fun setNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setNotificationEnabled(enabled)
        }
    }

    fun setSelectedPlaceId(placeId: String?) {
        _selectedPlaceId.value = placeId
    }

    fun setSelectedFamousCategoryId(categoryId: Int) {
        _selectedFamousCategoryId.value = categoryId.coerceIn(1, 14)
    }

    fun navigateBack(): Boolean {
        return navigationManager.navigateBack()
    }
}
