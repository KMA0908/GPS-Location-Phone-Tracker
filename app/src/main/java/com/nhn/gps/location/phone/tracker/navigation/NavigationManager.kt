package com.nhn.gps.location.phone.tracker.navigation

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class NavigationManager @Inject constructor() {

    private val _currentDestination = MutableStateFlow<AppDestination?>(null)
    val currentDestination: StateFlow<AppDestination?> = _currentDestination.asStateFlow()

    private val _previousDestination = MutableStateFlow<AppDestination?>(null)
    val previousDestination: StateFlow<AppDestination?> = _previousDestination.asStateFlow()

    fun navigateTo(destination: AppDestination) {
        if (destination == _currentDestination.value) return
        _previousDestination.value = _currentDestination.value
        _currentDestination.value = destination
    }

    fun reset() {
        _previousDestination.value = null
        _currentDestination.value = AppDestination.Home
    }
}
