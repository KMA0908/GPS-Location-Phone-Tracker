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

data class FamousPlaceUiState(
    val featuredPlace: FamousPlaceModel? = null,
    val trendingPlaces: List<FamousPlaceModel> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedCategory: String = "All"
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
        fetchInitialPlaces()
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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val typeId = categoryMap[category] ?: 0

            when (val result = repository.getAllFamousPlaces()) {
                is ExploreResult.Success -> {
                    val allPlaces = result.data
                    val filtered = if (typeId == 0) {
                        allPlaces
                    } else {
                        allPlaces.filter { it.idPlaceType == typeId }
                    }
                    
                    _uiState.update { it.copy(
                        featuredPlace = filtered.firstOrNull(),
                        trendingPlaces = if (filtered.size > 1) filtered.drop(1) else emptyList(),
                        isLoading = false
                    ) }
                }
                is ExploreResult.Empty -> {
                    _uiState.update { it.copy(featuredPlace = null, trendingPlaces = emptyList(), isLoading = false) }
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
        _uiState.update { it.copy(isLoading = true, error = null) }
        when (val result = repository.getAllFamousPlaces()) {
            is ExploreResult.Success -> {
                val allPlaces = result.data
                val filtered = allPlaces.filter { it.name.contains(query, ignoreCase = true) }
                _uiState.update { it.copy(
                    featuredPlace = filtered.firstOrNull(),
                    trendingPlaces = if (filtered.size > 1) filtered.drop(1) else emptyList(),
                    isLoading = false
                ) }
            }
            is ExploreResult.Empty -> {
                _uiState.update { it.copy(featuredPlace = null, trendingPlaces = emptyList(), isLoading = false) }
            }
            else -> {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        searchQueryFlow.value = query
    }

    fun onPlaceClicked(place: FamousPlaceModel) {
        viewModelScope.launch {
            _effect.emit(FamousPlaceEffect.OpenPlaceDetail(place.id))
        }
    }

    suspend fun getPhotoUri(metadata: PhotoMetadata): String? {
        return repository.getResolvedPhotoUri(metadata)
    }
}
