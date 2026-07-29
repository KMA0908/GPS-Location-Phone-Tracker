package com.nhn.gps.location.phone.tracker.ui.friend

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.databinding.FragmentMyFriendBinding
import com.nhn.gps.location.phone.tracker.navigation.AppDestination
import com.nhn.gps.location.phone.tracker.navigation.NavigationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MyFriendFragment : BaseFragment<FragmentMyFriendBinding, FriendListViewModel>() {

    override val viewModel: FriendListViewModel by viewModels()
    private val locationViewModel: com.nhn.gps.location.phone.tracker.ui.location.LocationViewModel by activityViewModels()
    private lateinit var adapter: FriendAdapter

    @Inject
    lateinit var navigationManager: NavigationManager

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentMyFriendBinding = FragmentMyFriendBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) = with(binding) {
        header.btnBack.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        
        header.btnRight.setOnClickListener {
            if (isAdded) {
                navigationManager.navigateTo(AppDestination.AddFriend)
            }
        }

        adapter = FriendAdapter { friend ->
            showProfile(friend)
        }
        
        layoutFriendList.rvFriends.layoutManager = LinearLayoutManager(requireContext())
        layoutFriendList.rvFriends.adapter = adapter

        // btnAddFriend is part of the layout_no_friend include
        layoutNoFriend.btnAddFriend.setOnClickListener {
            if (isAdded) {
                navigationManager.navigateTo(AppDestination.AddFriend)
            }
        }

        layoutFriendList.fabAddFriend.setOnClickListener {
            if (isAdded) {
                Toast.makeText(requireContext(), "Ads feature coming soon!", Toast.LENGTH_SHORT).show()
            }
        }

        layoutFriendProfile.ivBack.setOnClickListener {
            hideProfile()
        }

        layoutFriendProfile.cardShowCode.setOnClickListener {
            navigationManager.navigateTo(AppDestination.ShowQrFriend)
        }

        layoutFriendProfile.cardShareProfile.setOnClickListener {
            shareProfile()
        }

        viewDim.setOnClickListener {
            hideProfile()
        }
    }

    private fun showProfile(friend: com.nhn.gps.location.phone.tracker.data.model.FriendLocation) = with(binding) {
        layoutFriendProfile.tvName.text = friend.name
        layoutFriendProfile.tvUserId.text = "ID: ${friend.id}"
        layoutFriendProfile.tvPhoneNumber.text = "+84 000 0000"

        viewDim.visibility = View.VISIBLE
        layoutFriendProfile.root.visibility = View.VISIBLE
    }

    private fun hideProfile() = with(binding) {
        viewDim.visibility = View.GONE
        layoutFriendProfile.root.visibility = View.GONE
    }

    private fun shareProfile() {
        val userId = "00000000" 
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "My Friend Profile")
            putExtra(Intent.EXTRA_TEXT, "Join me on GPS Location Phone Tracker! Code: $userId")
        }
        startActivity(Intent.createChooser(intent, "Share via"))
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    locationViewModel.friendsLocations.collectLatest { list ->
                        updateFriendList(list)
                    }
                }

                launch {
                    viewModel.navigateToDetail.collect { friend ->
                        showProfile(friend)
                    }
                }
            }
        }
    }

    private fun updateFriendList(friends: List<com.nhn.gps.location.phone.tracker.data.model.FriendLocation>) = with(binding) {
        adapter.submitList(friends)
        layoutFriendList.tvFriendCount.text = "Friends (${friends.size})"
        
        if (friends.isEmpty()) {
            layoutNoFriend.root.visibility = View.VISIBLE
            layoutFriendList.root.visibility = View.GONE
        } else {
            layoutNoFriend.root.visibility = View.GONE
            layoutFriendList.root.visibility = View.VISIBLE
        }
    }

    companion object {
        fun newInstance() = MyFriendFragment()
    }
}
