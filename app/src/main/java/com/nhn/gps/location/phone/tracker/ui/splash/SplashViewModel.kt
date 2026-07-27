package com.nhn.gps.location.phone.tracker.ui.splash

import androidx.lifecycle.viewModelScope
import com.nhn.gps.location.phone.tracker.base.BaseViewModel
import com.nhn.gps.location.phone.tracker.data.local.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val appPreferences: AppPreferences
) : BaseViewModel() {

    private val _navigationEvent = MutableSharedFlow<SplashNavigation>(extraBufferCapacity = 1)
    val navigationEvent: SharedFlow<SplashNavigation> = _navigationEvent.asSharedFlow()

    init {
        startSplashTimer()
    }

    private fun startSplashTimer() {
        viewModelScope.launch {
            delay(2000)
            val isPermissionShown = appPreferences.isPermissionShown.first()
            if (isPermissionShown) {
                _navigationEvent.emit(SplashNavigation.ToMain)
            } else {
                _navigationEvent.emit(SplashNavigation.ToPermission)
            }
        }
    }
}

sealed class SplashNavigation {
    object ToMain : SplashNavigation()
    object ToPermission : SplashNavigation()
}
