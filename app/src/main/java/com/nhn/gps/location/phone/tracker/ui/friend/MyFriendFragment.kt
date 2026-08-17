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
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.leansoft.ads.AdManager
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.ads.GpsAdPlacement
import com.nhn.gps.location.phone.tracker.ads.GpsAdViewBinder
import com.nhn.gps.location.phone.tracker.ads.GpsAds
import com.nhn.gps.location.phone.tracker.ads.NativeAdRowAdapter
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.databinding.FragmentMyFriendBinding
import com.nhn.gps.location.phone.tracker.navigation.AppDestination
import com.nhn.gps.location.phone.tracker.ui.main.MainActivity
import com.nhn.gps.location.phone.tracker.util.loadAvatar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MyFriendFragment : BaseFragment<FragmentMyFriendBinding, FriendListViewModel>() {

    override val viewModel: FriendListViewModel by viewModels()
    private val locationViewModel: com.nhn.gps.location.phone.tracker.ui.location.LocationViewModel by activityViewModels()

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

        layoutFriendList.rvFriends.layoutManager = LinearLayoutManager(requireContext())

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
        
        viewRemoveDialog.imgDelete.loadAvatar(friend.avatarKey, friend.avatarUrl, fallbackRes = R.drawable.ic_avt_location)

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
        (activity as? MainActivity)?.showScreenNative(
            GpsAdPlacement.NATIVE_FRIEND_DETAIL,
            GpsAdViewBinder.NativeFormat.MEDIUM,
        )
        (activity as? MainActivity)?.overrideNextRouteInterstitial(GpsAdPlacement.INTER_FRIEND_DETAIL)
        layoutFriendProfile.tvName.text = friend.name
        layoutFriendProfile.tvUserId.text = "ID: ${friend.id}"
        layoutFriendProfile.tvPhoneNumber.text = "+84 000 0000"

        layoutFriendProfile.imgAvatar.loadAvatar(friend.avatarKey, friend.avatarUrl, fallbackRes = R.drawable.ic_avt_location )

        layoutFriendProfile.ivMore.setOnClickListener {
            viewModel.onMoreClicked(friend)
        }

        viewDim.visibility = View.VISIBLE
        layoutFriendProfile.root.visibility = View.VISIBLE
    }

    private fun hideProfile() = with(binding) {
        viewDim.visibility = View.GONE
        layoutFriendProfile.root.visibility = View.GONE
        (activity as? MainActivity)?.clearScreenAd()
        (activity as? MainActivity)?.overrideNextRouteInterstitial(null)
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
        val adapters = mutableListOf<RecyclerView.Adapter<out RecyclerView.ViewHolder>>()
        friends.chunked(3).forEach { group ->
            adapters += FriendAdapter(
                showMoreButton = true,
                onItemClick = { friend ->
                    showListInterThen { viewModel.onFriendClicked(friend) }
                },
                onMoreClick = { friend ->
                    showListInterThen { viewModel.onMoreClicked(friend) }
                },
            ).apply { submitList(group) }
            if (group.size == 3) {
                adapters += NativeAdRowAdapter(
                    GpsAdPlacement.NATIVE_LIST_FRIEND,
                    GpsAdViewBinder.NativeFormat.SMALL,
                )
            }
        }
        layoutFriendList.rvFriends.adapter = ConcatAdapter(adapters)
        layoutFriendList.tvFriendCount.text = "Friends (${friends.size})"
        
        if (friends.isEmpty()) {
            layoutNoFriend.root.visibility = View.VISIBLE
            layoutFriendList.root.visibility = View.GONE
        } else {
            layoutNoFriend.root.visibility = View.GONE
            layoutFriendList.root.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        binding.layoutFriendList.rvFriends.adapter = null
        runCatching { AdManager.instance.destroyNativeAd(GpsAdPlacement.NATIVE_LIST_FRIEND) }
        super.onDestroyView()
    }

    private fun showListInterThen(next: () -> Unit) {
        GpsAds.showInterThen(
            placement = GpsAdPlacement.INTER_LIST_FRIEND,
            fragmentManager = parentFragmentManager,
            next = { if (isAdded) next() },
        )
    }

    companion object {
        fun newInstance() = MyFriendFragment()
    }
}
