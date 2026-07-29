package com.nhn.gps.location.phone.tracker.navigation

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Stack

@Singleton
class NavigationManager @Inject constructor() {

    private val backStack = Stack<AppDestination>()

    private val _currentDestination = MutableStateFlow<AppDestination?>(null)
    val currentDestination: StateFlow<AppDestination?> = _currentDestination.asStateFlow()

    private var lastNavigateTime = 0L
    private val NAVIGATE_THRESHOLD = 500L

    fun navigateTo(destination: AppDestination) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastNavigateTime < NAVIGATE_THRESHOLD) return
        lastNavigateTime = currentTime

        if (destination == _currentDestination.value) return
        
        _currentDestination.value?.let { 
            backStack.push(it)
        }
        _currentDestination.value = destination
    }

    fun navigateBack(): Boolean {
        if (backStack.isEmpty()) return false
        
        val previous = backStack.pop()
        _currentDestination.value = previous
        return true
    }

    fun reset() {
        backStack.clear()
        _currentDestination.value = AppDestination.Home
    }
}
