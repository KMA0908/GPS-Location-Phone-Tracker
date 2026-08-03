package com.nhn.gps.location.phone.tracker.ui.friend

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import com.bumptech.glide.Glide
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.databinding.FragmentMyFriendBinding
import com.nhn.gps.location.phone.tracker.navigation.AppDestination
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MyFriendFragment : BaseFragment<FragmentMyFriendBinding, FriendListViewModel>() {

    override val viewModel: FriendListViewModel by viewModels()
    private val locationViewModel: com.nhn.gps.location.phone.tracker.ui.location.LocationViewModel by activityViewModels()
    private lateinit var adapter: FriendAdapter

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentMyFriendBinding = FragmentMyFriendBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) = with(binding) {
        header.btnBack.setOnClickListener { handleToolbarBack() }
        
        header.btnRight.setOnClickListener {
            if (isAdded) {
                navigationManager.navigateTo(AppDestination.AddFriend)
            }
        }

        adapter = FriendAdapter(
            showMoreButton = true,
            onItemClick = { friend -> viewModel.onFriendClicked(friend) },
            onMoreClick = { friend -> viewModel.onMoreClicked(friend) }
        )
        
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

        // Remove Friend Dialog listeners
        binding.viewRemoveDialog.btnCancel.setOnClickListener {
            viewModel.clearSelectedFriend()
        }

        binding.viewRemoveDialog.ivClose.setOnClickListener {
            viewModel.clearSelectedFriend()
        }

        binding.viewRemoveDialog.btnRemove.setOnClickListener {
            Log.d("TEST", "Remove clicked")
            viewModel.removeFriend()
        }

        binding.viewSuccessDialog.btnOk.setOnClickListener {
            binding.viewSuccessDialog.dialogSuccessContainer.visibility = View.GONE
            binding.viewDim.visibility = View.GONE
        }

        binding.viewDim.setOnClickListener {
            hideProfile()
            viewModel.clearSelectedFriend()
            binding.viewSuccessDialog.dialogSuccessContainer.visibility = View.GONE
        }
    }

    private fun showRemoveDialog(friend: com.nhn.gps.location.phone.tracker.data.model.FriendLocation) = with(binding) {
        viewRemoveDialog.tvTitle.text = getString(R.string.remove_friend_title, friend.name)
        
        // Glide avatar for dialog
        val avatarUrl = friend.avatarUrl
        if (avatarUrl.isEmpty() || avatarUrl == "null") {
            viewRemoveDialog.imgDelete.setImageResource(R.drawable.ic_avt_location)
        } else {
            Glide.with(this@MyFriendFragment)
                .load(avatarUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_avt_location)
                .error(R.drawable.ic_avt_location)
                .into(viewRemoveDialog.imgDelete)
        }

        viewDim.visibility = View.VISIBLE
        viewRemoveDialog.dialogRemoveFriendContainer.visibility = View.VISIBLE
    }

    private fun hideRemoveDialog() = with(binding) {
        viewRemoveDialog.dialogRemoveFriendContainer.visibility = View.GONE
        if (layoutFriendProfile.root.visibility == View.GONE && viewSuccessDialog.dialogSuccessContainer.visibility == View.GONE) {
            viewDim.visibility = View.GONE
        }
    }

    private fun showSuccessDialog() = with(binding) {
        viewRemoveDialog.dialogRemoveFriendContainer.visibility = View.GONE
        viewSuccessDialog.dialogSuccessContainer.visibility = View.VISIBLE
        viewDim.visibility = View.VISIBLE
    }

    private fun showProfile(friend: com.nhn.gps.location.phone.tracker.data.model.FriendLocation) = with(binding) {
        layoutFriendProfile.tvName.text = friend.name
        layoutFriendProfile.tvUserId.text = "ID: ${friend.id}"
        layoutFriendProfile.tvPhoneNumber.text = "+84 000 0000"

        // Load Avatar
        val avatarUrl = friend.avatarUrl
        if (avatarUrl.isEmpty() || avatarUrl == "null") {
            layoutFriendProfile.imgAvatar.setImageResource(R.drawable.ic_avt_location)
        } else {
            Glide.with(this@MyFriendFragment)
                .load(avatarUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_avt_location)
                .error(R.drawable.ic_avt_location)
                .into(layoutFriendProfile.imgAvatar)
        }

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

                launch {
                    viewModel.selectedFriend.collect { friend ->
                        if (friend != null) {
                            showRemoveDialog(friend)
                        } else {
                            hideRemoveDialog()
                        }
                    }
                }

                launch {
                    viewModel.removeSuccess.collect {
                        showSuccessDialog()
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
