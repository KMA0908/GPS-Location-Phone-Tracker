package com.nhn.gps.location.phone.tracker.ui.friend

import android.util.Log
import androidx.lifecycle.viewModelScope
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
    private val appPreferences: AppPreferences
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
        Log.d("AddFriendVM", "Finding friend with code: $code")
        
        viewModelScope.launch {
            _isLoading.value = true
            repository.findFriendById(code).fold(
                onSuccess = { profile ->
                    Log.d("AddFriendVM", "Friend found: ${profile.name}")
                    // Map UserProfile to FriendLocation for UI
                    _friendFound.value = FriendLocation(
                        id = profile.uid,
                        name = profile.name,
                        avatarUrl = profile.avatarUrl
                    )
                },
                onFailure = {
                    Log.d("AddFriendVM", "Friend not found")
                    _friendNotFound.emit(Unit)
                }
            )
            _isLoading.value = false
        }
    }

    fun addFriend() {
        val friend = _friendFound.value ?: return
        Log.d("AddFriendVM", "Adding friend: ${friend.name} (${friend.id})")
        
        viewModelScope.launch {
            _isLoading.value = true
            val myUid = appPreferences.userId.first()
            
            if (myUid != null) {
                repository.addFriend(myUid, friend.id).fold(
                    onSuccess = {
                        Log.d("AddFriendVM", "Friend added successfully")
                        _addSuccess.emit(Unit)
                    },
                    onFailure = {
                        Log.e("AddFriendVM", "Failed to add friend", it)
                    }
                )
            }
            _isLoading.value = false
        }
    }

    fun clearFoundFriend() {
        _friendFound.value = null
    }
}
