package com.nhn.gps.location.phone.tracker.ui.explore

import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.nhn.gps.location.phone.tracker.base.BaseViewModel
import com.nhn.gps.location.phone.tracker.data.model.FamousPlaceModel
import com.nhn.gps.location.phone.tracker.data.repository.ExploreRepository
import com.nhn.gps.location.phone.tracker.data.repository.ExploreResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

data class FamousPlaceUiState(
    val featuredPlace: FamousPlaceModel? = null,
    val trendingPlaces: List<FamousPlaceModel> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedCategory: String = "All",
    val hasMore: Boolean = false,
    val favoriteIds: Set<String> = emptySet(),
    val isReset: Boolean = false
)

sealed interface FamousPlaceEffect {
    data class OpenPlaceDetail(val placeId: String) : FamousPlaceEffect
}

@HiltViewModel
class FamousPlaceViewModel @Inject constructor(
    private val repository: ExploreRepository
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(FamousPlaceUiState())
    val uiState: StateFlow<FamousPlaceUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<FamousPlaceEffect>()
    val effect: SharedFlow<FamousPlaceEffect> = _effect.asSharedFlow()

    private val searchQueryFlow = MutableStateFlow("")

    private var fullFilteredList: List<FamousPlaceModel> = emptyList()
    private var visibleCount = INITIAL_PAGE_SIZE
    private var isAppending = false
    private var appendJob: Job? = null
    private var userLocation: LatLng? = null

    private val categoryMap = mapOf(
        "All" to 0,
        "Romantic" to 1,
        "Theme Parks" to 2,
        "Mountains" to 3,
        "Nature" to 4,
        "Dangerous" to 5,
        "Mysterious" to 6,
        "Surf" to 7,
        "Ghost Towns" to 8,
        "Film Locations" to 9,
        "Extreme Weather" to 10,
        "Family" to 11,
        "Cities" to 12,
        "Clubs" to 13,
        "Nightlife" to 14
    )

    init {
        observeSearch()
        observeFavorites()
        fetchInitialPlaces()
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            repository.observeFavoritePlaceIds().collectLatest { ids ->
                _uiState.update { state ->
                    state.copy(
                        favoriteIds = ids,
                        featuredPlace = state.featuredPlace?.copy(isFavorite = ids.contains(state.featuredPlace.id)),
                        trendingPlaces = state.trendingPlaces.map { it.copy(isFavorite = ids.contains(it.id)) },
                        isReset = false
                    )
                }
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeSearch() {
        viewModelScope.launch {
            searchQueryFlow
                .debounce(500)
                .map { it.trim() }
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.isNotEmpty()) {
                        performSearch(query)
                    } else {
                        fetchPlacesByCategory(_uiState.value.selectedCategory)
                    }
                }
        }
    }

    private fun fetchInitialPlaces() {
        fetchPlacesByCategory("All")
    }

    fun onCategorySelected(category: String) {
        if (_uiState.value.selectedCategory == category) return
        _uiState.update { it.copy(selectedCategory = category) }
        fetchPlacesByCategory(category)
    }

    private fun fetchPlacesByCategory(category: String) {
        cancelPendingAppend()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val typeId = categoryMap[category] ?: 0

            when (val result = repository.getAllFamousPlaces()) {
                is ExploreResult.Success -> {
                    val allPlaces = result.data
                    fullFilteredList = filterFamousPlaces(allPlaces, typeId = typeId)
                        .withCurrentDistances()

                    resetPagination()
                }
                is ExploreResult.Empty -> {
                    fullFilteredList = emptyList()
                    _uiState.update { it.copy(featuredPlace = null, trendingPlaces = emptyList(), isLoading = false, hasMore = false) }
                }
                is ExploreResult.ApiError -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
                else -> {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    private suspend fun performSearch(query: String) {
        cancelPendingAppend()
        _uiState.update { it.copy(isLoading = true, error = null) }
        when (val result = repository.getAllFamousPlaces()) {
            is ExploreResult.Success -> {
                val allPlaces = result.data
                fullFilteredList = filterFamousPlaces(allPlaces, query = query)
                    .withCurrentDistances()
                resetPagination()
            }
            is ExploreResult.Empty -> {
                fullFilteredList = emptyList()
                _uiState.update { it.copy(featuredPlace = null, trendingPlaces = emptyList(), isLoading = false, hasMore = false) }
            }
            else -> {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun resetPagination() {
        cancelPendingAppend()
        visibleCount = INITIAL_PAGE_SIZE
        updatePaginatedList(isReset = true)
    }

    fun loadMore() {
        if (_uiState.value.isLoading || isAppending || !_uiState.value.hasMore || fullFilteredList.isEmpty()) return

        isAppending = true
        appendJob = viewModelScope.launch {
            try {
                delay(LOAD_MORE_DELAY_MS)

                if (_uiState.value.hasMore && fullFilteredList.isNotEmpty()) {
                    visibleCount = (visibleCount + LOAD_MORE_PAGE_SIZE)
                        .coerceAtMost(fullFilteredList.size)
                    updatePaginatedList(isReset = false)
                }
            } finally {
                isAppending = false
            }
        }
    }

    private fun cancelPendingAppend() {
        appendJob?.cancel()
        appendJob = null
        isAppending = false
    }

    private fun updatePaginatedList(isReset: Boolean) {
        val page = paginateFamousPlaces(fullFilteredList, visibleCount)
        val favoriteIds = _uiState.value.favoriteIds

        val featured = page.featured?.let {
            it.copy(isFavorite = favoriteIds.contains(it.id))
        }

        val trending = page.trending.map { it.copy(isFavorite = favoriteIds.contains(it.id)) }

        _uiState.update { it.copy(
            featuredPlace = featured,
            trendingPlaces = trending,
            isLoading = false,
            hasMore = page.hasMore,
            isReset = isReset
        ) }
        isAppending = false
    }

    fun onSearchQueryChanged(query: String) {
        searchQueryFlow.value = query
    }

    fun updateUserLocation(location: LatLng?) {
        val previous = userLocation
        if (previous == location) return
        if (previous != null && location != null &&
            com.google.maps.android.SphericalUtil.computeDistanceBetween(previous, location) < LOCATION_UPDATE_THRESHOLD_METERS
        ) {
            return
        }

        userLocation = location
        if (fullFilteredList.isNotEmpty()) {
            fullFilteredList = fullFilteredList.withCurrentDistances()
            updatePaginatedList(isReset = false)
        }
    }

    private fun List<FamousPlaceModel>.withCurrentDistances(): List<FamousPlaceModel> = map { place ->
        place.copy(
            distanceKm = calculateFamousPlaceDistanceKm(
                userLocation = userLocation,
                placeLatitude = place.latitude,
                placeLongitude = place.longitude,
            ),
        )
    }

    fun onPlaceClicked(place: FamousPlaceModel) {
        viewModelScope.launch {
            _effect.emit(FamousPlaceEffect.OpenPlaceDetail(place.id))
        }
    }

    fun toggleFavorite(placeId: String) {
        viewModelScope.launch {
            repository.toggleFavorite(placeId)
        }
    }

    suspend fun getPhotoUri(metadata: PhotoMetadata): String? {
        return repository.getResolvedPhotoUri(metadata)
    }

    companion object {
        private const val INITIAL_PAGE_SIZE = 10
        private const val LOAD_MORE_PAGE_SIZE = 5
        private const val LOAD_MORE_DELAY_MS = 250L
        private const val LOCATION_UPDATE_THRESHOLD_METERS = 25.0
    }
}
