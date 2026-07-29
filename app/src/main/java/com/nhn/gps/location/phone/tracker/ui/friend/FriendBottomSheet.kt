package com.nhn.gps.location.phone.tracker.ui.friend

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nhn.gps.location.phone.tracker.databinding.BottomSheetFriendBinding
import com.nhn.gps.location.phone.tracker.navigation.AppDestination
import com.nhn.gps.location.phone.tracker.navigation.NavigationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FriendBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetFriendBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FriendListViewModel by viewModels()
    private lateinit var adapter: FriendAdapter

    @Inject
    lateinit var navigationManager: NavigationManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetFriendBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        observeData()
    }

    private fun setupViews() = with(binding) {
        adapter = FriendAdapter { friend ->
            dismiss()
            navigationManager.navigateTo(AppDestination.MyFriend)
        }
        
        rvFriends.layoutManager = LinearLayoutManager(requireContext())
        rvFriends.adapter = adapter

        layoutEmpty.btnAddFriendEmpty.setOnClickListener {
            dismiss()
            navigationManager.navigateTo(AppDestination.AddFriend)
        }
        
        btnAddFriend.setOnClickListener {
            dismiss()
            navigationManager.navigateTo(AppDestination.AddFriend)
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.friends.collectLatest { friends ->
                    updateUi(friends)
                }
            }
        }
    }

    private fun updateUi(friends: List<com.nhn.gps.location.phone.tracker.data.model.FriendLocation>) = with(binding) {
        tvFriendCount.text = "Friends (${friends.size})"
        adapter.submitList(friends)
        
        if (friends.isEmpty()) {
            layoutEmpty.root.visibility = View.VISIBLE
            rvFriends.visibility = View.GONE
            btnAddFriend.visibility = View.GONE
        } else {
            layoutEmpty.root.visibility = View.GONE
            rvFriends.visibility = View.VISIBLE
            btnAddFriend.visibility = View.VISIBLE
        }
    }

    override fun onStart() {
        super.onStart()
        (binding.root.parent as? View)?.setBackgroundResource(android.R.color.transparent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "FriendBottomSheet"
        fun newInstance() = FriendBottomSheet()
    }
}
