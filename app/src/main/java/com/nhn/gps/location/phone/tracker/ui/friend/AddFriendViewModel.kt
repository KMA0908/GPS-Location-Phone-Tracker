package com.nhn.gps.location.phone.tracker.ui.friend

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.nhn.gps.location.phone.tracker.base.BaseViewModel
import com.nhn.gps.location.phone.tracker.data.local.AppPreferences
import com.nhn.gps.location.phone.tracker.data.model.FriendLocation
import com.nhn.gps.location.phone.tracker.data.repository.FriendLimitReachedException
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

    private val _alreadyFriend = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val alreadyFriend: SharedFlow<Unit> = _alreadyFriend.asSharedFlow()

    private val _premiumRequired = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val premiumRequired: SharedFlow<Int> = _premiumRequired.asSharedFlow()

    private val _addError = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val addError: SharedFlow<Unit> = _addError.asSharedFlow()

    fun findFriend(input: String) {
        val normalizedInput = input.trim()
        if (normalizedInput.isBlank()) return
        Log.d("AddFriendVM", "Processing friend code")
        
        val prefix = "gps_friend:"
        val friendUid = if (normalizedInput.startsWith(prefix)) {
            normalizedInput.substring(prefix.length).trim()
        } else {
            // Nếu không có prefix, có thể là nhập tay ID trực tiếp (tùy nhu cầu UI)
            // Theo yêu cầu "Xử lý QR không hợp lệ", nếu scan QR không có prefix thì không add.
            // Để đảm bảo tính năng nhập tay vẫn chạy, ta cho phép nếu input không chứa ":"
            if (normalizedInput.contains(":")) {
                Log.d("AddFriendVM", "Invalid QR format")
                _friendNotFound.tryEmit(Unit)
                return
            }
            normalizedInput
        }

        if (friendUid.isBlank()) return

        viewModelScope.launch {
            val myUid = appPreferences.userId.first()
            if (friendUid == myUid) {
                Log.d("AddFriendVM", "Cannot add yourself")
                _friendNotFound.emit(Unit) // Hoặc hiển thị thông báo riêng nếu cần
                return@launch
            }

            if (myUid != null && repository.isFriend(myUid, friendUid)) {
                Log.d("AddFriendVM", "Already friends")
                _alreadyFriend.emit(Unit)
                return@launch
            }

            _isLoading.value = true
            repository.findFriendById(friendUid).fold(
                onSuccess = { profile ->
                    Log.d("AddFriendVM", "Friend found: ${profile.name}")
                    _friendFound.value = FriendLocation(
                        id = profile.uid,
                        name = profile.name,
                        avatarUrl = profile.avatarUrl,
                        avatarKey = profile.avatarKey,
                    )
                },
                onFailure = {
                    Log.d("AddFriendVM", "Friend not found or error: ${it.message}")
                    _friendNotFound.emit(Unit)
                }
            )
            _isLoading.value = false
        }
    }

    fun addFriend() {
        if (_isLoading.value) return
        val friend = _friendFound.value ?: return
        Log.d("AddFriendVM", "Adding friend: ${friend.name} (${friend.id})")
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val myUid = appPreferences.userId.first()
                if (myUid.isNullOrBlank()) {
                    Log.e("AddFriendVM", "Cannot add friend: active user is unavailable")
                    _addError.emit(Unit)
                    return@launch
                }

                repository.addFriend(myUid, friend.id).fold(
                    onSuccess = {
                        Log.d("AddFriendVM", "Friend added successfully")
                        _addSuccess.emit(Unit)
                    },
                    onFailure = { error ->
                        Log.e("AddFriendVM", "Failed to add friend", error)
                        if (error is FriendLimitReachedException) {
                            _premiumRequired.emit(error.limit)
                        } else {
                            _addError.emit(Unit)
                        }
                    },
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearFoundFriend() {
        _friendFound.value = null
    }
}
