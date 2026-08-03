package com.nhn.gps.location.phone.tracker.ui.setup_profile

import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.nhn.gps.location.phone.tracker.base.BaseViewModel
import com.nhn.gps.location.phone.tracker.data.local.AppPreferences
import com.nhn.gps.location.phone.tracker.data.model.UserProfile
import com.nhn.gps.location.phone.tracker.navigation.AppDestination
import com.nhn.gps.location.phone.tracker.navigation.NavigationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class SetUpProfileViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val navigationManager: NavigationManager,
    private val database: FirebaseDatabase,
    private val auth: FirebaseAuth
) : BaseViewModel() {

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _phone = MutableStateFlow("")
    val phone: StateFlow<String> = _phone.asStateFlow()

    val isSaveEnabled: StateFlow<Boolean> = combine(_name, _phone) { name, phone ->
        name.isNotBlank() && phone.isNotBlank() && isValidPhone(phone)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private fun isValidPhone(phone: String): Boolean {
        return android.util.Patterns.PHONE.matcher(phone).matches()
    }

    fun onNameChanged(name: String) {
        _name.value = name
    }

    fun onPhoneChanged(phone: String) {
        _phone.value = phone
    }

    fun onSaveClicked() {
        if (!isSaveEnabled.value) return

        launchCatching {
            // 1. Ensure anonymous authentication
            var currentUser = auth.currentUser
            if (currentUser == null) {
                auth.signInAnonymously().await()
                currentUser = auth.currentUser
            }
            
            val uid = currentUser?.uid ?: return@launchCatching

            // 2. Prepare Profile Object
            val profile = UserProfile(
                uid = uid,
                name = _name.value,
                phone = _phone.value,
                avatarUrl = "" // Default or handle if UI allows
            )

            // 3. Save to Firebase: users/{uid}/profile
            database.getReference("users").child(uid).child("profile").setValue(profile).await()

            // 4. Save to DataStore for local state consistency
            appPreferences.setUserId(uid)
            appPreferences.setUserName(_name.value)
            appPreferences.setUserPhone(_phone.value)

            // 5. Navigate Home
            navigationManager.navigateTo(AppDestination.Home, clearStack = true)
        }
    }
}
