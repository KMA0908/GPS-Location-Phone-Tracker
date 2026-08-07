package com.nhn.gps.location.phone.tracker.ui.explore

import androidx.lifecycle.viewModelScope
import com.nhn.gps.location.phone.tracker.base.BaseViewModel
import com.nhn.gps.location.phone.tracker.data.model.FamousPlaceModel
import com.nhn.gps.location.phone.tracker.data.repository.ExploreRepository
import com.nhn.gps.location.phone.tracker.data.repository.ExploreResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExploreUiState(
    val places: List<FamousPlaceModel> = emptyList(),
    val selectedPlaceId: String? = null,
    val searchedPlace: FamousPlaceModel? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

private data class RegionKey(
    val latBucket: Int,
    val lngBucket: Int,
    val distanceBucket: Int
)

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val repository: ExploreRepository
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    private val cameraStateFlow = MutableSharedFlow<GlobeCameraState>(replay = 0)

    private val cache = mutableMapOf<RegionKey, List<FamousPlaceModel>>()
    private val cacheOrder = mutableListOf<RegionKey>()
    private val MAX_CACHE_SIZE = 15

    private var currentCameraState: GlobeCameraState? = null

    init {
        observeCamera()
    }

    @OptIn(FlowPreview::class)
    private fun observeCamera() {
        viewModelScope.launch {
            cameraStateFlow
                .debounce(650)
                .map { state ->
                    currentCameraState = state
                    toRegionKey(state)
                }
                .distinctUntilChanged()
                .collectLatest { key ->
                    fetchPlacesForRegion(key)
                }
        }
    }

    fun onCameraChanged(state: GlobeCameraState) {
        viewModelScope.launch {
            cameraStateFlow.emit(state)
        }
    }

    private suspend fun fetchPlacesForRegion(key: RegionKey) {
        val cached = cache[key]
        if (cached != null) {
            _uiState.update { it.copy(places = cached, selectedPlaceId = cached.firstOrNull()?.id) }
            return
        }

        val state = currentCameraState ?: return
        
        _uiState.update { it.copy(isLoading = true, error = null) }
        
        // Map camera distance (3.5 to 15.0) to radius (5km to 50km)
        val radius = ((state.cameraDistance - 3.5) / (15.0 - 3.5) * 45000.0 + 5000.0)
            .coerceIn(5000.0, 50000.0)

        when (val result = repository.getNearbyFamousPlaces(state.centerLatitude, state.centerLongitude, radius, 4)) {
            is ExploreResult.Success -> {
                val places = result.data
                addToCache(key, places)
                _uiState.update { it.copy(
                    places = places,
                    selectedPlaceId = places.firstOrNull()?.id,
                    isLoading = false
                ) }
            }
            is ExploreResult.Empty -> {
                _uiState.update { it.copy(places = emptyList(), isLoading = false) }
            }
            is ExploreResult.ApiError -> {
                _uiState.update { it.copy(isLoading = false, error = result.message) }
            }
            else -> {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun addToCache(key: RegionKey, data: List<FamousPlaceModel>) {
        if (cache.containsKey(key)) {
            cacheOrder.remove(key)
        } else if (cache.size >= MAX_CACHE_SIZE) {
            val oldest = cacheOrder.removeAt(0)
            cache.remove(oldest)
        }
        cache[key] = data
        cacheOrder.add(key)
    }

    private fun toRegionKey(state: GlobeCameraState): RegionKey {
        // Quantumize coordinates and distance to avoid frequent API calls
        return RegionKey(
            latBucket = (state.centerLatitude / 2.0).toInt(),
            lngBucket = (state.centerLongitude / 2.0).toInt(),
            distanceBucket = (state.cameraDistance / 1.0).toInt()
        )
    }

    fun selectPlace(placeId: String) {
        _uiState.update { it.copy(selectedPlaceId = placeId) }
    }

    fun selectPlaceAt(position: Int) {
        val places = _uiState.value.places
        if (position in places.indices) {
            _uiState.update { it.copy(selectedPlaceId = places[position].id) }
        }
    }

    fun selectRandomPlace() {
        val places = _uiState.value.places
        if (places.isNotEmpty()) {
            val randomPlace = places.random()
            _uiState.update { it.copy(selectedPlaceId = randomPlace.id) }
        }
    }

    fun retryCurrentRegion() {
        viewModelScope.launch {
            val state = currentCameraState ?: return@launch
            fetchPlacesForRegion(toRegionKey(state))
        }
    }
}
