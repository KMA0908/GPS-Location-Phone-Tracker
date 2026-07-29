package com.nhn.gps.location.phone.tracker.ui.friend

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.nhn.gps.location.phone.tracker.base.BaseViewModel
import com.nhn.gps.location.phone.tracker.data.local.AppPreferences
import com.nhn.gps.location.phone.tracker.data.model.FriendLocation
import com.nhn.gps.location.phone.tracker.data.repository.FriendRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class FriendListViewModel @Inject constructor(
    private val repository: FriendRepository,
    private val auth: FirebaseAuth,
    private val appPreferences: AppPreferences
) : BaseViewModel() {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val friends: StateFlow<List<FriendLocation>> = MutableSharedFlow<String>(extraBufferCapacity = 1)
        .apply { tryEmit(auth.currentUser?.uid ?: "") }
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

    fun onFriendClicked(friend: FriendLocation) {
        _navigateToDetail.tryEmit(friend)
    }

    fun removeFriend(friendId: String) {
        launchCatching {
            val myUid = auth.currentUser?.uid ?: return@launchCatching
            repository.removeFriend(myUid, friendId)
        }
    }
}
