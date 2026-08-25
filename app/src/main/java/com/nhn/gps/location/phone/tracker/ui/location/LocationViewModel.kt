package com.nhn.gps.location.phone.tracker.ui.location

import android.annotation.SuppressLint
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import com.nhn.gps.location.phone.tracker.base.BaseViewModel
import com.nhn.gps.location.phone.tracker.data.local.AppPreferences
import com.nhn.gps.location.phone.tracker.data.model.FriendLocation
import com.nhn.gps.location.phone.tracker.data.model.UserLocation
import com.nhn.gps.location.phone.tracker.data.repository.FriendRepository
import com.nhn.gps.location.phone.tracker.data.repository.GoogleRouteTravelMode
import com.nhn.gps.location.phone.tracker.data.repository.LocationRepository
import com.nhn.gps.location.phone.tracker.data.repository.ZoneRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DirectionTravelMode(
    val routeApiMode: GoogleRouteTravelMode,
) {
    CAR(GoogleRouteTravelMode.DRIVE),
    MOTORCYCLE(GoogleRouteTravelMode.TWO_WHEELER),
    WALKING(GoogleRouteTravelMode.WALK),
}

@HiltViewModel
class LocationViewModel @Inject constructor(
    private val repository: LocationRepository,
    private val friendRepository: FriendRepository,
    private val fusedLocationClient: FusedLocationProviderClient,
    private val appPreferences: AppPreferences,
    private val zoneRepository: ZoneRepository,
) : BaseViewModel() {

    private val _selfLocation = MutableStateFlow<LatLng?>(null)
    val selfLocation: StateFlow<LatLng?> = _selfLocation.asStateFlow()

    private val _friendsLocations = MutableStateFlow<List<FriendLocation>>(emptyList())
    val friendsLocations: StateFlow<List<FriendLocation>> = _friendsLocations.asStateFlow()

    private var lastSyncedLocation: LatLng? = null
    private val syncMutex = Mutex()

    private val _isFriendsDataLoaded = MutableStateFlow(false)
    val isFriendsDataLoaded: StateFlow<Boolean> = _isFriendsDataLoaded.asStateFlow()

    private val _selectedTravelMode = MutableStateFlow(DirectionTravelMode.CAR)
    val selectedTravelMode: StateFlow<DirectionTravelMode> = _selectedTravelMode.asStateFlow()

    fun selectTravelMode(mode: DirectionTravelMode) {
        _selectedTravelMode.value = mode
    }

    private val _isFriendSearchActive = MutableStateFlow(false)
    val isFriendSearchActive: StateFlow<Boolean> = _isFriendSearchActive.asStateFlow()
    private val _friendSearchInput = MutableStateFlow("")
    val friendSearchInput: StateFlow<String> = _friendSearchInput.asStateFlow()
    private val _appliedFriendSearchQuery = MutableStateFlow("")
    val appliedFriendSearchQuery: StateFlow<String> = _appliedFriendSearchQuery.asStateFlow()
    private val _displayedFriends = MutableStateFlow<List<FriendLocation>>(emptyList())
    val displayedFriends: StateFlow<List<FriendLocation>> = _displayedFriends.asStateFlow()
    private val _recentSearchedFriends = MutableStateFlow<List<FriendLocation>>(emptyList())
    val recentSearchedFriends: StateFlow<List<FriendLocation>> = _recentSearchedFriends.asStateFlow()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { loc ->
                val newLatLng = LatLng(loc.latitude, loc.longitude)
                _selfLocation.value = newLatLng
                syncLocationWithFirebase(newLatLng)
                viewModelScope.launch { zoneRepository.processLocation(newLatLng) }
            }
        }
    }

    init {
        getCurrentLocation()
        observeAllLocations()
        viewModelScope.launch {
            combine(friendsLocations, appliedFriendSearchQuery) { friends, query ->
                if (query.isBlank()) friends else friends.filter {
                    it.name.contains(query, true) || it.id.contains(query, true)
                }
            }.collectLatest { _displayedFriends.value = it }
        }
        viewModelScope.launch {
            appPreferences.friendSearchHistoryFlow.combine(friendsLocations) { entries, friends ->
                entries.mapNotNull { entry -> friends.find { it.id == entry.friendId } }
            }.collectLatest { _recentSearchedFriends.value = it }
        }
    }

    fun openFriendSearch() { _isFriendSearchActive.value = true; _friendSearchInput.value = ""; _appliedFriendSearchQuery.value = "" }
    fun closeFriendSearch() { _isFriendSearchActive.value = false; _friendSearchInput.value = ""; _appliedFriendSearchQuery.value = "" }
    fun updateFriendSearchInput(value: String) {
        _friendSearchInput.value = value
        if (value.isBlank()) _appliedFriendSearchQuery.value = ""
    }
    fun submitFriendSearch() { _appliedFriendSearchQuery.value = _friendSearchInput.value.trim() }
    fun clearFriendSearch() { updateFriendSearchInput("") }
    fun recordFriendSearch(friendId: String) { viewModelScope.launch { appPreferences.recordFriendSearch(friendId) } }
    fun removeFriendSearchHistory(friendId: String) { viewModelScope.launch { appPreferences.removeFriendSearchHistory(friendId) } }
    fun clearFriendSearchHistory() { viewModelScope.launch { appPreferences.clearFriendSearchHistory() } }

    @SuppressLint("MissingPermission")
    fun getCurrentLocation() {
        // Get last location immediately for faster UI update
        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            loc?.let {
                val newLatLng = LatLng(it.latitude, it.longitude)
                _selfLocation.value = newLatLng
                syncLocationWithFirebase(newLatLng)
                viewModelScope.launch { zoneRepository.processLocation(newLatLng) }
            }
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(2000)
            .build()

        fusedLocationClient.requestLocationUpdates(request, locationCallback, null)
    }

    private fun observeAllLocations() {
        launchCatching {
            appPreferences.userId
                .distinctUntilChanged()
                .collectLatest { myUid ->
                    _friendsLocations.value = emptyList()
                    _isFriendsDataLoaded.value = false

                    if (myUid.isNullOrBlank()) return@collectLatest

                    Log.d("LocationVM", "Observing friends after active user changed")
                    friendRepository.getFriends(myUid).collectLatest { locations ->
                        Log.d("LocationVM", "Received ${locations.size} friends locations")
                        _friendsLocations.value = locations
                        _isFriendsDataLoaded.value = true
                        locations.forEach { friend ->
                            zoneRepository.processLocation(
                                LatLng(friend.latitude, friend.longitude),
                                subjectId = "friend:${friend.id}",
                                subjectName = friend.name.ifBlank { "Friend" },
                            )
                        }
                    }
                }
        }
    }

    fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    fun clearLocationData() {
        _selfLocation.value = null
    }

    private fun syncLocationWithFirebase(latLng: LatLng) {
        viewModelScope.launch {
            if (!appPreferences.isLocationSharingEnabled.first()) return@launch
            val uid = appPreferences.userId.first() ?: return@launch
            
            syncMutex.withLock {
                val previous = lastSyncedLocation
                val shouldSync = if (previous == null) {
                    true
                } else {
                    SphericalUtil.computeDistanceBetween(previous, latLng) >= 100.0
                }

                if (shouldSync) {
                    val userLocation = UserLocation(
                        latitude = latLng.latitude,
                        longitude = latLng.longitude,
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateSelfLocation(uid, userLocation)
                    lastSyncedLocation = latLng
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
