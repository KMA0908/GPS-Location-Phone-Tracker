package com.nhn.gps.location.phone.tracker.ui.explore

import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.google.maps.android.SphericalUtil
import com.nhn.gps.location.phone.tracker.base.BaseViewModel
import com.nhn.gps.location.phone.tracker.data.model.FamousPlaceModel
import com.nhn.gps.location.phone.tracker.data.repository.ExploreRepository
import com.nhn.gps.location.phone.tracker.data.repository.ExploreResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaceDetailUiState(
    val place: FamousPlaceModel? = null,
    val userLocation: LatLng? = null,
    val distanceMeters: Double? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isFavorite: Boolean = false
)

@HiltViewModel
class PlaceDetailViewModel @Inject constructor(
    private val repository: ExploreRepository
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(PlaceDetailUiState())
    val uiState: StateFlow<PlaceDetailUiState> = _uiState.asStateFlow()

    init {
        observeFavorites()
        observeDistance()
    }

    private fun observeDistance() {
        viewModelScope.launch {
            _uiState.collectLatest { state ->
                val place = state.place
                val userLoc = state.userLocation
                if (place != null && userLoc != null) {
                    val distance = SphericalUtil.computeDistanceBetween(
                        userLoc,
                        LatLng(place.latitude, place.longitude)
                    )
                    _uiState.update { it.copy(distanceMeters = distance) }
                } else if (state.distanceMeters != null) {
                    _uiState.update { it.copy(distanceMeters = null) }
                }
            }
        }
    }

    fun updateUserLocation(location: LatLng?) {
        _uiState.update { it.copy(userLocation = location) }
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            combine(
                _uiState.map { it.place?.id }.distinctUntilChanged(),
                repository.observeFavoritePlaceIds()
            ) { placeId, favoriteIds ->
                placeId != null && favoriteIds.contains(placeId)
            }.collectLatest { isFav ->
                _uiState.update { it.copy(isFavorite = isFav) }
            }
        }
    }

    fun loadPlaceDetail(placeId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.getPlaceDetail(placeId)) {
                is ExploreResult.Success -> {
                    _uiState.update { it.copy(place = result.data, isLoading = false) }
                }
                is ExploreResult.ApiError -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
                is ExploreResult.InvalidPlace -> {
                    _uiState.update { it.copy(isLoading = false, error = "Invalid Place") }
                }
                else -> {
                    _uiState.update { it.copy(isLoading = false, error = "Unknown Error") }
                }
            }
        }
    }

    fun toggleFavorite() {
        val placeId = _uiState.value.place?.id ?: return
        viewModelScope.launch {
            repository.toggleFavorite(placeId)
        }
    }

    suspend fun getPhotoUri(metadata: PhotoMetadata): String? {
        return repository.getResolvedPhotoUri(metadata)
    }
}
