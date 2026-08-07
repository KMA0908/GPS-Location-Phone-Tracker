package com.nhn.gps.location.phone.tracker.ui.explore

import androidx.lifecycle.viewModelScope
import com.nhn.gps.location.phone.tracker.base.BaseViewModel
import com.nhn.gps.location.phone.tracker.data.model.FamousPlaceModel
import com.nhn.gps.location.phone.tracker.data.repository.ExploreRepository
import com.nhn.gps.location.phone.tracker.data.repository.ExploreResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaceDetailUiState(
    val place: FamousPlaceModel? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class PlaceDetailViewModel @Inject constructor(
    private val repository: ExploreRepository
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(PlaceDetailUiState())
    val uiState: StateFlow<PlaceDetailUiState> = _uiState.asStateFlow()

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
}
