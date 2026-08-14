package com.nhn.gps.location.phone.tracker.ui.phone_number_locator

import androidx.lifecycle.viewModelScope
import com.nhn.gps.location.phone.tracker.base.BaseViewModel
import com.nhn.gps.location.phone.tracker.data.model.Country
import com.nhn.gps.location.phone.tracker.data.repository.CountryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class CountryViewModel @Inject constructor(
    private val repository: CountryRepository
) : BaseViewModel() {

    private val _searchQuery = MutableStateFlow("")
    
    private val _originalCountries = MutableStateFlow<List<Country>>(emptyList())

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val filteredCountries: StateFlow<List<Country>> = _originalCountries
        .combine(_searchQuery.debounce(300L)) { countries, query ->
            if (query.isBlank()) {
                countries
            } else {
                val lowercaseQuery = query.lowercase(Locale.getDefault())
                countries.filter { 
                    it.name.lowercase(Locale.getDefault()).contains(lowercaseQuery) ||
                    it.dialCode.contains(query)
                }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        loadCountries()
    }

    private fun loadCountries() {
        launchCatching {
            val countries = withContext(Dispatchers.IO) {
                repository.getCountries().sortedBy { it.name.trim().lowercase(Locale.getDefault()) }
            }
            _originalCountries.value = countries
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }
}
