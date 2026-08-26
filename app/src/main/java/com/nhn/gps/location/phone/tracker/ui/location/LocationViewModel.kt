package com.nhn.gps.location.phone.tracker.ui.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.nhn.gps.location.phone.tracker.BuildConfig
import com.nhn.gps.location.phone.tracker.base.BaseViewModel
import com.nhn.gps.location.phone.tracker.data.local.AppPreferences
import com.nhn.gps.location.phone.tracker.data.model.FriendLocation
import com.nhn.gps.location.phone.tracker.data.model.UserLocation
import com.nhn.gps.location.phone.tracker.data.repository.FriendRepository
import com.nhn.gps.location.phone.tracker.data.repository.GoogleRouteTravelMode
import com.nhn.gps.location.phone.tracker.data.repository.LocationRepository
import com.nhn.gps.location.phone.tracker.data.repository.ZoneRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val appContext: Context,
    private val repository: LocationRepository,
    private val friendRepository: FriendRepository,
    private val fusedLocationClient: FusedLocationProviderClient,
    private val appPreferences: AppPreferences,
    private val zoneRepository: ZoneRepository,
    private val firebaseAuth: FirebaseAuth,
) : BaseViewModel() {

    private val _rawSelfLocation = MutableStateFlow<LatLng?>(null)
    val rawSelfLocation: StateFlow<LatLng?> = _rawSelfLocation.asStateFlow()

    private val _selfLocation = MutableStateFlow<LatLng?>(null)
    val selfLocation: StateFlow<LatLng?> = _selfLocation.asStateFlow()

    private val _friendsLocations = MutableStateFlow<List<FriendLocation>>(emptyList())
    val friendsLocations: StateFlow<List<FriendLocation>> = _friendsLocations.asStateFlow()

    private var lastSyncedLocation: LatLng? = null
    private var lastAcceptedDisplayedLocation: LatLng? = null
    private var newestLocationTimeMillis: Long = 0L
    private val syncMutex = Mutex()
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
    private var isLocationUpdatesStarted = false
    private var isNetworkCallbackRegistered = false
    @Volatile private var isNetworkValidated = false

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
            result.lastLocation?.let(::handleRawLocation)
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshNetworkState()
        override fun onLost(network: Network) = refreshNetworkState()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
            refreshNetworkState()
    }

    init {
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
    fun startForegroundLocationUpdates() {
        registerNetworkCallback()
        if (isLocationUpdatesStarted || !hasLocationPermission()) return
        isLocationUpdatesStarted = true

        // Get last location immediately for faster UI update
        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            loc?.takeIf { System.currentTimeMillis() - it.time <= MAX_LAST_LOCATION_AGE_MS }
                ?.let(::handleRawLocation)
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_LOCATION_INTERVAL_MS)
            // Keep raw samples responsive near a zone boundary. The separate policy below
            // applies the 100 m threshold to public UI state and Firebase writes.
            .setMinUpdateDistanceMeters(RAW_LOCATION_MIN_DISTANCE_METERS)
            .build()

        fusedLocationClient.requestLocationUpdates(request, locationCallback, null)
            .addOnSuccessListener { debugLog("foreground_location_started") }
            .addOnFailureListener { error ->
                isLocationUpdatesStarted = false
                Log.e(TAG, "foreground_location_start_failed type=${error.javaClass.simpleName}", error)
            }
    }

    /** Kept for existing Fragment callers; registration itself is idempotent. */
    fun getCurrentLocation() = startForegroundLocationUpdates()

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

    fun stopForegroundLocationUpdates() {
        if (isLocationUpdatesStarted) {
            isLocationUpdatesStarted = false
            fusedLocationClient.removeLocationUpdates(locationCallback)
            debugLog("foreground_location_stopped")
        }
        unregisterNetworkCallback()
    }

    fun stopLocationUpdates() = stopForegroundLocationUpdates()

    fun clearLocationData() {
        _rawSelfLocation.value = null
        _selfLocation.value = null
        lastAcceptedDisplayedLocation = null
    }

    private fun handleRawLocation(location: Location) {
        val latLng = LatLng(location.latitude, location.longitude)
        if (!LocationMovementPolicy.isValid(latLng) ||
            !location.hasAccuracy() || !location.accuracy.isFinite() || location.accuracy <= 0f ||
            (location.time > 0L && location.time < newestLocationTimeMillis)
        ) {
            debugLog("location_ignored_invalid")
            return
        }

        newestLocationTimeMillis = maxOf(newestLocationTimeMillis, location.time)
        _rawSelfLocation.value = latLng
        debugLog("raw_location_received accuracy=${location.accuracy.toInt()}")

        viewModelScope.launch {
            zoneRepository.processLocation(latLng, accuracyMeters = location.accuracy)
        }

        val previousDisplayed = lastAcceptedDisplayedLocation
        if (LocationMovementPolicy.shouldAccept(previousDisplayed, latLng)) {
            lastAcceptedDisplayedLocation = latLng
            _selfLocation.value = latLng
        } else {
            val moved = LocationMovementPolicy.distanceMeters(previousDisplayed, latLng)
            debugLog("location_ignored_below_sync_threshold movedMeters=${moved.toInt()}")
        }
        syncLocationWithFirebase(latLng)
    }

    private fun syncLocationWithFirebase(latLng: LatLng) {
        viewModelScope.launch {
            if (!appPreferences.isLocationSharingEnabled.first()) return@launch
            if (!isNetworkValidated) {
                debugLog("network_unavailable")
                return@launch
            }

            val authUid = firebaseAuth.currentUser?.uid ?: return@launch
            val activeUid = appPreferences.userId.first()
            if (activeUid.isNullOrBlank() || activeUid != authUid) {
                Log.e(TAG, "location_sync_failed type=UidMismatch")
                return@launch
            }

            syncMutex.withLock {
                val previous = lastSyncedLocation
                if (LocationSyncPolicy.shouldSync(isNetworkValidated, previous, latLng)) {
                    val movedMeters = LocationMovementPolicy.distanceMeters(previous, latLng)
                    debugLog("location_sync_started movedMeters=${movedMeters.toInt()}")
                    val userLocation = UserLocation(
                        latitude = latLng.latitude,
                        longitude = latLng.longitude,
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateSelfLocation(authUid, userLocation)
                        .onSuccess {
                            lastSyncedLocation = latLng
                            debugLog("location_sync_success")
                        }
                        .onFailure { error ->
                            Log.e(TAG, "location_sync_failed type=${error.javaClass.simpleName}", error)
                        }
                }
            }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun registerNetworkCallback() {
        if (isNetworkCallbackRegistered) return
        isNetworkCallbackRegistered = true
        isNetworkValidated = hasValidatedNetwork()
        runCatching { connectivityManager.registerDefaultNetworkCallback(networkCallback) }
            .onFailure {
                isNetworkCallbackRegistered = false
                Log.e(TAG, "network_callback_register_failed", it)
            }
        debugLog(if (isNetworkValidated) "network_available" else "network_unavailable")
    }

    private fun unregisterNetworkCallback() {
        if (!isNetworkCallbackRegistered) return
        isNetworkCallbackRegistered = false
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        isNetworkValidated = false
    }

    private fun refreshNetworkState() {
        val wasValidated = isNetworkValidated
        isNetworkValidated = hasValidatedNetwork()
        if (wasValidated == isNetworkValidated) return
        debugLog(if (isNetworkValidated) "network_available" else "network_unavailable")
        if (isNetworkValidated) {
            _rawSelfLocation.value?.let(::syncLocationWithFirebase)
        }
    }

    private fun hasValidatedNetwork(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    override fun onCleared() {
        stopForegroundLocationUpdates()
        super.onCleared()
    }

    private companion object {
        const val TAG = "LocationVM"
        const val LOCATION_INTERVAL_MS = 10_000L
        const val MIN_LOCATION_INTERVAL_MS = 5_000L
        const val RAW_LOCATION_MIN_DISTANCE_METERS = 10f
        const val MAX_LAST_LOCATION_AGE_MS = 2 * 60_000L
    }
}
