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
import kotlin.math.*

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
    private var selectedCategoryId: Int = 1

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
        when (val result = repository.getAllFamousPlaces()) {
            is ExploreResult.Success -> {
                val place = result.data.find { it.name.contains(query, ignoreCase = true) }
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

    fun setCategoryFilter(categoryId: Int) {
        val normalized = categoryId.coerceIn(1, 14)
        if (selectedCategoryId == normalized && _uiState.value.places.isNotEmpty()) return
        selectedCategoryId = normalized
        viewModelScope.launch {
            val state = currentCameraState ?: GlobeCameraState(20.0, 0.0, 0f)
            currentCameraState = state
            fetchPlacesForRegion(toRegionKey(state))
        }
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
        val state = currentCameraState ?: return
        
        val allPlacesResult = repository.getAllFamousPlaces()
        if (allPlacesResult !is ExploreResult.Success) {
            _uiState.update { it.copy(places = emptyList(), isLoading = false, isRefreshing = false) }
            return
        }
        
        val allPlaces = allPlacesResult.data
        val centerLat = state.centerLatitude
        val centerLng = state.centerLongitude

        // Keep all photo markers from the category visible, like the reference globe.
        val nearby = allPlaces.asSequence()
            .filter { it.idPlaceType == selectedCategoryId }
            .map { place ->
                place to calculateAngularDistance(centerLat, centerLng, place.latitude, place.longitude)
            }
            .sortedBy { it.second }
            .map { it.first }
            .toList()

        _uiState.update { it.copy(
            places = nearby,
            selectedPlaceId = it.selectedPlaceId?.takeIf { selectedId ->
                nearby.any { place -> place.id == selectedId }
            },
            isLoading = false,
            isRefreshing = false,
            error = null
        ) }
    }

    private fun calculateAngularDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lng2 - lng1)
        
        // Simple angular distance on sphere: cos(d) = sin(phi1)sin(phi2) + cos(phi1)cos(phi2)cos(deltaLambda)
        val cosD = sin(phi1) * sin(phi2) + cos(phi1) * cos(phi2) * cos(deltaLambda)
        return acos(cosD.coerceIn(-1.0, 1.0))
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
                _effect.emit(ExploreEffect.OpenPlaceDetail(randomPlace.id))
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

    fun clearSelection() {
        _uiState.update { it.copy(selectedPlaceId = null) }
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
