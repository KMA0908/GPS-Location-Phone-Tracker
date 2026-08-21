package com.nhn.gps.location.phone.tracker.ui.main

import androidx.lifecycle.viewModelScope
import android.util.Log
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.nhn.gps.location.phone.tracker.base.BaseViewModel
import com.nhn.gps.location.phone.tracker.data.local.AppPreferences
import com.nhn.gps.location.phone.tracker.data.model.FamousPlaceModel
import com.nhn.gps.location.phone.tracker.data.repository.ExploreRepository
import com.nhn.gps.location.phone.tracker.data.repository.ExploreResult
import com.nhn.gps.location.phone.tracker.data.repository.GeocodedLocation
import com.nhn.gps.location.phone.tracker.navigation.AppDestination
import com.nhn.gps.location.phone.tracker.navigation.NavigationManager
import com.nhn.gps.location.phone.tracker.ui.location.MapRouteRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class ZonePlacePrediction(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String,
) {
    val displayText: String
        get() = listOf(primaryText, secondaryText).filter { it.isNotBlank() }.joinToString(", ")
}

sealed interface ZoneAddressSearchState {
    data object Idle : ZoneAddressSearchState
    data object Loading : ZoneAddressSearchState
    data class Predictions(val items: List<ZonePlacePrediction>) : ZoneAddressSearchState
    data class Success(val location: GeocodedLocation, val displayName: String?) : ZoneAddressSearchState
    data object NotFound : ZoneAddressSearchState
    data class Error(val messageRes: Int) : ZoneAddressSearchState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val navigationManager: NavigationManager,
    private val exploreRepository: ExploreRepository,
    private val placesClient: PlacesClient,
) : BaseViewModel() {

    private val _zoneAddressSearchState = MutableStateFlow<ZoneAddressSearchState>(ZoneAddressSearchState.Idle)
    val zoneAddressSearchState = _zoneAddressSearchState.asStateFlow()
    private var zoneAddressSearchJob: Job? = null

    fun searchZoneAddress(query: String, visibleBounds: LatLngBounds? = null, debounce: Boolean = true) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _zoneAddressSearchState.value = ZoneAddressSearchState.Error(com.nhn.gps.location.phone.tracker.R.string.search_address_empty)
            return
        }

        zoneAddressSearchJob?.cancel()
        _zoneAddressSearchState.value = ZoneAddressSearchState.Loading

        zoneAddressSearchJob = viewModelScope.launch {
            try {
                if (debounce) delay(ZONE_SEARCH_DEBOUNCE_MS)
                Log.d(TAG, "zone_places_query_started queryLength=${trimmed.length}")
                val builder = FindAutocompletePredictionsRequest.builder().setQuery(trimmed)
                visibleBounds?.let { bounds ->
                    runCatching {
                        builder.setLocationBias(
                            RectangularBounds.newInstance(bounds.southwest, bounds.northeast)
                        )
                    }
                }
                val response = placesClient.findAutocompletePredictions(builder.build()).await()
                val predictions = response.autocompletePredictions.map { prediction ->
                    ZonePlacePrediction(
                        placeId = prediction.placeId,
                        primaryText = prediction.getPrimaryText(null).toString(),
                        secondaryText = prediction.getSecondaryText(null).toString(),
                    )
                }
                Log.d(TAG, "zone_places_predictions_received count=${predictions.size}")
                _zoneAddressSearchState.value = if (predictions.isEmpty()) {
                    ZoneAddressSearchState.NotFound
                } else {
                    ZoneAddressSearchState.Predictions(predictions)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "zone_places_query_failed type=${error.javaClass.simpleName}", error)
                _zoneAddressSearchState.value = ZoneAddressSearchState.Error(com.nhn.gps.location.phone.tracker.R.string.search_location_error)
            }
        }
    }

    fun selectZoneAddressPrediction(prediction: ZonePlacePrediction) {
        zoneAddressSearchJob?.cancel()
        _zoneAddressSearchState.value = ZoneAddressSearchState.Loading
        zoneAddressSearchJob = viewModelScope.launch {
            try {
                Log.d(TAG, "zone_place_fetch_started placeId=${maskPlaceId(prediction.placeId)}")
                val fields = listOf(
                    Place.Field.ID,
                    Place.Field.DISPLAY_NAME,
                    Place.Field.FORMATTED_ADDRESS,
                    Place.Field.LOCATION,
                )
                val place = placesClient.fetchPlace(
                    FetchPlaceRequest.newInstance(prediction.placeId, fields)
                ).await().place
                val location = place.location
                if (location == null) {
                    _zoneAddressSearchState.value = ZoneAddressSearchState.NotFound
                    return@launch
                }
                val formattedAddress = place.formattedAddress
                    ?.takeIf { it.isNotBlank() }
                    ?: prediction.displayText
                Log.d(TAG, "zone_place_selected placeId=${maskPlaceId(place.id)}")
                _zoneAddressSearchState.value = ZoneAddressSearchState.Success(
                    location = GeocodedLocation(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        formattedAddress = formattedAddress,
                        placeId = place.id ?: prediction.placeId,
                    ),
                    displayName = place.displayName?.takeIf { it.isNotBlank() }
                        ?: prediction.primaryText,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "zone_place_fetch_failed type=${error.javaClass.simpleName}", error)
                _zoneAddressSearchState.value = ZoneAddressSearchState.Error(
                    com.nhn.gps.location.phone.tracker.R.string.search_location_error
                )
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

    private fun maskPlaceId(placeId: String?): String = placeId
        ?.takeLast(4)
        ?.padStart(7, '*')
        ?: "missing"

    private companion object {
        const val TAG = "MainViewModel"
        const val ZONE_SEARCH_DEBOUNCE_MS = 300L
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
        initialValue = true
    )

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
        _mapRouteRequest.value = MapRouteRequest(
            destinationName = destinationName,
            latitude = latitude,
            longitude = longitude,
            friendId = friendId,
        )
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
        viewModelScope.launch {
            preferences.setLocationSharingEnabled(enabled)
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
