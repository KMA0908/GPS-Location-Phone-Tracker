package com.nhn.gps.location.phone.tracker.ui.explore

import androidx.lifecycle.viewModelScope
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.nhn.gps.location.phone.tracker.base.BaseViewModel
import com.nhn.gps.location.phone.tracker.data.model.FamousPlaceModel
import com.nhn.gps.location.phone.tracker.data.repository.ExploreRepository
import com.nhn.gps.location.phone.tracker.data.repository.ExploreResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

sealed interface ExploreEffect {
    data class AnimateGlobe(val latitude: Double, val longitude: Double) : ExploreEffect
    data class ScrollCarousel(val position: Int) : ExploreEffect
    data class OpenPlaceDetail(val placeId: String) : ExploreEffect
}

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val repository: ExploreRepository
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<ExploreEffect>()
    val effect: SharedFlow<ExploreEffect> = _effect.asSharedFlow()

    private val searchQueryFlow = MutableStateFlow("")
    private val cameraStateFlow = MutableSharedFlow<GlobeCameraState>(replay = 0)

    private val cache = mutableMapOf<RegionKey, List<FamousPlaceModel>>()
    private val cacheOrder = mutableListOf<RegionKey>()
    private val maxCacheSize = 15

    private var currentCameraState: GlobeCameraState? = null

    init {
        observeCamera()
        observeSearch()
    }

    @OptIn(FlowPreview::class)
    private fun observeSearch() {
        viewModelScope.launch {
            searchQueryFlow
                .debounce(350)
                .map { it.trim() }
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.isEmpty()) {
                        _uiState.update { it.copy(searchedPlace = null) }
                    } else {
                        performSearch(query)
                    }
                }
        }
    }

    private suspend fun performSearch(query: String) {
        _uiState.update { it.copy(isLoading = true) }
        when (val result = repository.searchPlaces(query)) {
            is ExploreResult.Success -> {
                val place = result.data.firstOrNull()
                _uiState.update { it.copy(
                    searchedPlace = place,
                    isLoading = false
                ) }
                place?.let {
                    _effect.emit(ExploreEffect.AnimateGlobe(it.latitude, it.longitude))
                }
            }
            else -> {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        searchQueryFlow.value = query
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
            _uiState.update { it.copy(
                places = cached,
                selectedPlaceId = cached.firstOrNull()?.id,
                isLoading = false,
                isRefreshing = false,
                error = null
            ) }
            return
        }

        val state = currentCameraState ?: return
        
        val hasData = _uiState.value.places.isNotEmpty()
        if (hasData) {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
        } else {
            _uiState.update { it.copy(isLoading = true, error = null) }
        }
        
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
                    isLoading = false,
                    isRefreshing = false
                ) }
            }
            is ExploreResult.Empty -> {
                _uiState.update { it.copy(places = emptyList(), isLoading = false, isRefreshing = false) }
            }
            is ExploreResult.ApiError -> {
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = result.message) }
            }
            else -> {
                _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
            }
        }
    }

    private fun addToCache(key: RegionKey, data: List<FamousPlaceModel>) {
        if (cache.containsKey(key)) {
            cacheOrder.remove(key)
        } else if (cache.size >= maxCacheSize) {
            val oldest = cacheOrder.removeAt(0)
            cache.remove(oldest)
        }
        cache[key] = data
        cacheOrder.add(key)
    }

    private fun toRegionKey(state: GlobeCameraState): RegionKey {
        return RegionKey(
            latBucket = (state.centerLatitude / 2.0).toInt(),
            lngBucket = (state.centerLongitude / 2.0).toInt(),
            distanceBucket = (state.cameraDistance / 1.0).toInt()
        )
    }

    fun selectPlace(placeId: String) {
        val state = _uiState.value
        if (state.searchedPlace?.id == placeId) {
            _uiState.update { it.copy(selectedPlaceId = placeId) }
            viewModelScope.launch {
                _effect.emit(ExploreEffect.AnimateGlobe(state.searchedPlace.latitude, state.searchedPlace.longitude))
                _effect.emit(ExploreEffect.ScrollCarousel(0))
            }
            return
        }

        val currentPlaces = state.places
        val index = currentPlaces.indexOfFirst { it.id == placeId }
        if (index != -1) {
            _uiState.update { it.copy(selectedPlaceId = placeId) }
            val place = currentPlaces[index]
            val carouselIndex = if (state.searchedPlace != null) index + 1 else index
            
            viewModelScope.launch {
                _effect.emit(ExploreEffect.AnimateGlobe(place.latitude, place.longitude))
                _effect.emit(ExploreEffect.ScrollCarousel(carouselIndex))
            }
        }
    }

    fun selectPlaceAt(position: Int) {
        val places = _uiState.value.places
        if (position in places.indices) {
            val place = places[position]
            _uiState.update { it.copy(selectedPlaceId = place.id) }
            viewModelScope.launch {
                _effect.emit(ExploreEffect.AnimateGlobe(place.latitude, place.longitude))
                _effect.emit(ExploreEffect.ScrollCarousel(position))
            }
        }
    }

    fun selectRandomPlace() {
        val places = _uiState.value.places
        if (places.isNotEmpty()) {
            val index = places.indices.random()
            val randomPlace = places[index]
            _uiState.update { it.copy(selectedPlaceId = randomPlace.id) }
            viewModelScope.launch {
                _effect.emit(ExploreEffect.AnimateGlobe(randomPlace.latitude, randomPlace.longitude))
                _effect.emit(ExploreEffect.ScrollCarousel(index))
            }
        }
    }

    fun exploreSelectedPlace() {
        val state = _uiState.value
        val placeId = state.selectedPlaceId ?: state.places.firstOrNull()?.id
        if (placeId != null) {
            viewModelScope.launch {
                _effect.emit(ExploreEffect.OpenPlaceDetail(placeId))
            }
        }
    }

    fun retryCurrentRegion() {
        viewModelScope.launch {
            val state = currentCameraState ?: return@launch
            fetchPlacesForRegion(toRegionKey(state))
        }
    }

    suspend fun getPhotoUri(metadata: PhotoMetadata): String? {
        return repository.getResolvedPhotoUri(metadata)
    }
}
