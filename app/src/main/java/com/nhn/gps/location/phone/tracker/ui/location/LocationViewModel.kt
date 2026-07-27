package com.nhn.gps.location.phone.tracker.ui.location

import android.annotation.SuppressLint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.nhn.gps.location.phone.tracker.base.BaseViewModel
import com.nhn.gps.location.phone.tracker.data.model.FriendLocation
import com.nhn.gps.location.phone.tracker.data.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

@HiltViewModel
class LocationViewModel @Inject constructor(
    private val repository: LocationRepository,
    private val fusedLocationClient: FusedLocationProviderClient
) : BaseViewModel() {

    private val _selfLocation = MutableStateFlow<LatLng?>(null)
    val selfLocation: StateFlow<LatLng?> = _selfLocation.asStateFlow()

    private val _friendsLocations = MutableStateFlow<List<FriendLocation>>(emptyList())
    val friendsLocations: StateFlow<List<FriendLocation>> = _friendsLocations.asStateFlow()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let {
                _selfLocation.value = LatLng(it.latitude, it.longitude)
            }
        }
    }

    init {
        getCurrentLocation()
        observeFriends()
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(2000)
            .build()

        fusedLocationClient.requestLocationUpdates(request, locationCallback, null)
    }

    private fun observeFriends() {
        launchCatching {
            repository.getFriendsLocations().collectLatest { locations ->
                _friendsLocations.value = locations
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
