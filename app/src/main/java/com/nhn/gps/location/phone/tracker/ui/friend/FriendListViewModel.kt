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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FriendListViewModel @Inject constructor(
    private val repository: FriendRepository,
    private val appPreferences: AppPreferences
) : BaseViewModel() {

    private val currentUserId = MutableStateFlow<String?>(null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val friends: StateFlow<List<FriendLocation>> = currentUserId
        .filterNotNull()
        .flatMapLatest { uid ->
            if (uid.isNotBlank()) repository.getFriends(uid)
            else kotlinx.coroutines.flow.flowOf(emptyList())
        }
        .onEach { Log.d("FriendDebug", "FriendListViewModel update size = ${it.size}") }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _navigateToDetail = MutableSharedFlow<FriendLocation>(extraBufferCapacity = 1)
    val navigateToDetail: SharedFlow<FriendLocation> = _navigateToDetail.asSharedFlow()

    private val _selectedFriend = MutableStateFlow<FriendLocation?>(null)
    val selectedFriend: StateFlow<FriendLocation?> = _selectedFriend.asStateFlow()

    private val _removeSuccess = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val removeSuccess: SharedFlow<Unit> = _removeSuccess.asSharedFlow()

    private val _removeError = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val removeError: SharedFlow<Unit> = _removeError.asSharedFlow()

    private val _isRemoving = MutableStateFlow(false)
    val isRemoving: StateFlow<Boolean> = _isRemoving.asStateFlow()

    init {
        viewModelScope.launch {
            appPreferences.userId.collect {
                currentUserId.value = it
            }
        }
    }

    fun onFriendClicked(friend: FriendLocation) {
        _navigateToDetail.tryEmit(friend)
    }

    fun onMoreClicked(friend: FriendLocation) {
        _selectedFriend.value = friend
    }

    fun clearSelectedFriend() {
        _selectedFriend.value = null
    }

    fun removeFriend() {
        if (_isRemoving.value) return
        val friendId = _selectedFriend.value?.id ?: return
        val myUid = currentUserId.value ?: return

        viewModelScope.launch {
            _isRemoving.value = true
            try {
                repository.removeFriend(myUid, friendId).fold(
                    onSuccess = {
                        _removeSuccess.emit(Unit)
                        _selectedFriend.value = null
                    },
                    onFailure = { error ->
                        Log.e("FriendDebug", "Failed to remove friend", error)
                        _removeError.emit(Unit)
                    },
                )
            } finally {
                _isRemoving.value = false
            }
        }
    }
}
