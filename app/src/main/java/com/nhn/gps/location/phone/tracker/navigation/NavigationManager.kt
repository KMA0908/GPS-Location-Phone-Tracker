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

    fun navigateTo(destination: AppDestination, clearStack: Boolean = false) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastNavigateTime < NAVIGATE_THRESHOLD) return
        lastNavigateTime = currentTime

        // Kiểm tra tránh điều hướng trùng màn hình hiện tại
        if (destination == _currentDestination.value) return

        if (clearStack) {
            backStack.clear()
        } else {
            // Chỉ push vào stack nếu màn hình hiện tại không null
            _currentDestination.value?.let {
                // Tránh duplicate màn hình liên tiếp trong stack (nếu có logic phức tạp sau này)
                if (backStack.isEmpty() || backStack.peek() != it) {
                    backStack.push(it)
                }
            }
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
