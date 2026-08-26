package com.nhn.gps.location.phone.tracker.ui.zone

import androidx.lifecycle.viewModelScope
import com.nhn.gps.location.phone.tracker.base.BaseViewModel
import com.nhn.gps.location.phone.tracker.data.model.ZoneAlert
import com.nhn.gps.location.phone.tracker.data.model.ZoneStatus
import com.nhn.gps.location.phone.tracker.data.repository.ZoneRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class NotificationFilterState(
    val searchQuery: String = "",
    val status: ZoneStatus? = null,
    val dateRange: DateRange = DateRange.ALL,
    val sortType: SortType = SortType.TIME_DESC
)

enum class DateRange { ALL, TODAY, LAST_7_DAYS, LAST_30_DAYS }
enum class SortType { TIME_DESC, NAME_ASC, NAME_DESC }

@kotlinx.coroutines.ExperimentalCoroutinesApi
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val zoneRepository: ZoneRepository,
) : BaseViewModel() {

    private val _filterState = MutableStateFlow(NotificationFilterState())
    val filterState = _filterState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    val filteredAlerts = combine(
        zoneRepository.alerts,
        _filterState
    ) { alerts, filter ->
        var result = alerts.asSequence()

        // 1. Search Query
        if (filter.searchQuery.isNotBlank()) {
            result = result.filter {
                it.zoneName.contains(filter.searchQuery, true) ||
                        it.userName.contains(filter.searchQuery, true)
            }
        }

        // 2. Status Filter
        if (filter.status != null) {
            result = result.filter { it.status == filter.status }
        }

        // 3. Date Range Filter
        val now = System.currentTimeMillis()
        result = result.filter { alert ->
            when (filter.dateRange) {
                DateRange.ALL -> true
                DateRange.TODAY -> isToday(alert.time)
                DateRange.LAST_7_DAYS -> alert.time >= now - 7 * 24 * 60 * 60 * 1000L
                DateRange.LAST_30_DAYS -> alert.time >= now - 30 * 24 * 60 * 60 * 1000L
            }
        }

        // 4. Sort
        when (filter.sortType) {
            SortType.TIME_DESC -> result = result.sortedByDescending { it.time }
            SortType.NAME_ASC -> result = result.sortedWith(compareBy({ it.userName.lowercase() }, { it.zoneName.lowercase() }))
            SortType.NAME_DESC -> result = result.sortedWith(compareByDescending<ZoneAlert> { it.userName.lowercase() }.thenByDescending { it.zoneName.lowercase() })
        }

        result.toList()
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    fun updateSearchQuery(query: String) {
        _filterState.update { it.copy(searchQuery = query) }
    }

    fun setStatusFilter(status: ZoneStatus?) {
        _filterState.update { it.copy(status = status) }
    }

    fun setDateRangeFilter(range: DateRange) {
        _filterState.update { it.copy(dateRange = range) }
    }

    fun applyAdvancedFilter(status: ZoneStatus?, dateRange: DateRange, sortType: SortType) {
        _filterState.update { it.copy(status = status, dateRange = dateRange, sortType = sortType) }
    }

    fun toggleNameSort() {
        _filterState.update { 
            val nextType = when(it.sortType) {
                SortType.NAME_ASC -> SortType.NAME_DESC
                SortType.NAME_DESC -> SortType.NAME_ASC
                else -> SortType.NAME_ASC
            }
            it.copy(sortType = nextType)
        }
    }

    fun deleteAlert(alertId: Long) {
        viewModelScope.launch {
            try {
                zoneRepository.deleteAlert(alertId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {}
        }
    }

    fun clearAlerts() {
        viewModelScope.launch {
            try {
                zoneRepository.clearAlerts()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {}
        }
    }

    fun resetFilters() {
        _filterState.value = NotificationFilterState(searchQuery = _filterState.value.searchQuery)
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            delay(500) 
            _isRefreshing.value = false
        }
    }

    private fun isToday(timestamp: Long): Boolean {
        val date = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
        return date == LocalDate.now(ZoneId.systemDefault())
    }
}
