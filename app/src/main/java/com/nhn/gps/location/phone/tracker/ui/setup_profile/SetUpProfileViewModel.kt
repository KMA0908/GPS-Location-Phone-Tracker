package com.nhn.gps.location.phone.tracker.ui.setup_profile

import androidx.lifecycle.viewModelScope
import com.nhn.gps.location.phone.tracker.base.BaseViewModel
import com.nhn.gps.location.phone.tracker.data.local.AppPreferences
import com.nhn.gps.location.phone.tracker.navigation.AppDestination
import com.nhn.gps.location.phone.tracker.navigation.NavigationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SetUpProfileViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val navigationManager: NavigationManager
) : BaseViewModel() {

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _phone = MutableStateFlow("")
    val phone: StateFlow<String> = _phone.asStateFlow()

    val isSaveEnabled: StateFlow<Boolean> = combine(_name, _phone) { name, phone ->
        name.isNotBlank() && phone.isNotBlank()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun onNameChanged(name: String) {
        _name.value = name
    }

    fun onPhoneChanged(phone: String) {
        _phone.value = phone
    }

    fun onSaveClicked() {
        viewModelScope.launch {
            appPreferences.setUserName(_name.value)
            appPreferences.setUserPhone(_phone.value)
            navigationManager.navigateTo(AppDestination.Home, clearStack = true)
        }
    }
}
