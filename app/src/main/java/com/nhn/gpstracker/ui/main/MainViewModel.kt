package com.nhn.gpstracker.ui.main

import androidx.lifecycle.viewModelScope
import com.nhn.gpstracker.base.BaseViewModel
import com.nhn.gpstracker.data.local.AppPreferences
import com.nhn.gpstracker.navigation.AppDestination
import com.nhn.gpstracker.navigation.NavigationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val navigationManager: NavigationManager,
) : BaseViewModel() {

    val uiState = combine(
        preferences.appOpenCount,
        navigationManager.currentDestination,
    ) { appOpenCount, destination ->
        MainUiState(
            appOpenCount = appOpenCount,
            currentRoute = destination.route,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(),
    )

    init {
        launchCatching {
            preferences.incrementAppOpenCount()
        }
    }

    fun openMap() {
        navigationManager.navigateTo(AppDestination.Map)
    }

    fun startTracking() {
        navigationManager.navigateTo(AppDestination.Tracking)
    }

    fun goHome() {
        navigationManager.navigateTo(AppDestination.Home)
    }
}
