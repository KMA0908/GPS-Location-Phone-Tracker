package com.nhn.gps.location.phone.tracker.ui.friend

import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.nhn.gps.location.phone.tracker.base.BaseViewModel
import com.nhn.gps.location.phone.tracker.data.local.AppPreferences
import com.nhn.gps.location.phone.tracker.data.model.FriendLocation
import com.nhn.gps.location.phone.tracker.data.repository.FriendRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddFriendViewModel @Inject constructor(
    private val repository: FriendRepository,
    private val appPreferences: AppPreferences,
    private val auth: FirebaseAuth
) : BaseViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _friendFound = MutableStateFlow<FriendLocation?>(null)
    val friendFound: StateFlow<FriendLocation?> = _friendFound.asStateFlow()

    private val _addSuccess = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val addSuccess: SharedFlow<Unit> = _addSuccess.asSharedFlow()

    private val _friendNotFound = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val friendNotFound: SharedFlow<Unit> = _friendNotFound.asSharedFlow()

    fun findFriend(code: String) {
        if (code.isBlank()) return
        
        viewModelScope.launch {
            _isLoading.value = true
            repository.findFriendById(code).fold(
                onSuccess = {
                    _friendFound.value = it
                },
                onFailure = {
                    _friendNotFound.emit(Unit)
                }
            )
            _isLoading.value = false
        }
    }

    fun addFriend() {
        val friend = _friendFound.value ?: return
        val myUid = auth.currentUser?.uid ?: return
        
        viewModelScope.launch {
            _isLoading.value = true
            val myName = appPreferences.userName.first()
            
            repository.addFriend(myUid, myName, friend).fold(
                onSuccess = {
                    _addSuccess.emit(Unit)
                },
                onFailure = {
                }
            )
            _isLoading.value = false
        }
    }

    fun clearFoundFriend() {
        _friendFound.value = null
    }
}
