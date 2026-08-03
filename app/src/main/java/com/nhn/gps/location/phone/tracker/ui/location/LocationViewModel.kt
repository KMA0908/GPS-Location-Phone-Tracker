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
import com.nhn.gps.location.phone.tracker.base.BaseViewModel
import com.nhn.gps.location.phone.tracker.data.local.AppPreferences
import com.nhn.gps.location.phone.tracker.data.model.FriendLocation
import com.nhn.gps.location.phone.tracker.data.model.UserLocation
import com.nhn.gps.location.phone.tracker.data.repository.FriendRepository
import com.nhn.gps.location.phone.tracker.data.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocationViewModel @Inject constructor(
    private val repository: LocationRepository,
    private val friendRepository: FriendRepository,
    private val fusedLocationClient: FusedLocationProviderClient,
    private val appPreferences: AppPreferences
) : BaseViewModel() {

    private val _selfLocation = MutableStateFlow<LatLng?>(null)
    val selfLocation: StateFlow<LatLng?> = _selfLocation.asStateFlow()

    private val _friendsLocations = MutableStateFlow<List<FriendLocation>>(emptyList())
    val friendsLocations: StateFlow<List<FriendLocation>> = _friendsLocations.asStateFlow()

    private val _isFriendsDataLoaded = MutableStateFlow(false)
    val isFriendsDataLoaded: StateFlow<Boolean> = _isFriendsDataLoaded.asStateFlow()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { loc ->
                val newLatLng = LatLng(loc.latitude, loc.longitude)
                _selfLocation.value = newLatLng
                syncLocationWithFirebase(newLatLng)
            }
        }
    }

    init {
        getCurrentLocation()
        observeAllLocations()
    }

    @SuppressLint("MissingPermission")
    fun getCurrentLocation() {
        // Get last location immediately for faster UI update
        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            loc?.let {
                val newLatLng = LatLng(it.latitude, it.longitude)
                _selfLocation.value = newLatLng
                syncLocationWithFirebase(newLatLng)
            }
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(2000)
            .build()

        fusedLocationClient.requestLocationUpdates(request, locationCallback, null)
    }

    private fun observeAllLocations() {
        launchCatching {
            val myUid = appPreferences.userId.first() ?: return@launchCatching
            Log.d("LocationVM", "Observing friends locations for $myUid")
            friendRepository.getFriends(myUid).collectLatest { locations ->
                Log.d("LocationVM", "Received ${locations.size} friends locations")
                _friendsLocations.value = locations
                _isFriendsDataLoaded.value = true
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
            val uid = appPreferences.userId.first() ?: return@launch
            val userLocation = UserLocation(
                latitude = latLng.latitude,
                longitude = latLng.longitude,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateSelfLocation(uid, userLocation)
        }
    }

    override fun onCleared() {
        super.onCleared()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
