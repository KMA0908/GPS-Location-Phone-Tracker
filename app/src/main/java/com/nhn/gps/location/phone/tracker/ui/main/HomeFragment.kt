package com.nhn.gps.location.phone.tracker.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.databinding.FragmentHomeBinding
import com.nhn.gps.location.phone.tracker.ui.explore.ExploreCardAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : BaseFragment<FragmentHomeBinding, MainViewModel>() {

    override val viewModel: MainViewModel by viewModels({ requireActivity() })

    private val exploreAdapter by lazy {
        ExploreCardAdapter(
            onClick = { place ->
                viewModel.setSelectedPlaceId(place.id)
                navigationManager.navigateTo(com.nhn.gps.location.phone.tracker.navigation.AppDestination.PlaceDetail)
            },
            onBindPhoto = { item, imageView ->
                val photoFileName = item.previewPhotos.firstOrNull()
                if (photoFileName != null) {
                    com.bumptech.glide.Glide.with(imageView)
                        .load("file:///android_asset/famous_places_images/$photoFileName")
                        .placeholder(R.drawable.ic_paris)
                        .error(R.drawable.ic_paris)
                        .centerCrop()
                        .into(imageView)
                } else {
                    imageView.setImageResource(R.drawable.ic_paris)
                }
            },
            layoutMode = ExploreCardAdapter.LayoutMode.HOME_HORIZONTAL
        )
    }

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentHomeBinding = FragmentHomeBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) = with(binding) {
        setupExploreRecyclerView()

        bottomNavigationCustom.navHome.setOnClickListener {
            navigationManager.navigateTo(com.nhn.gps.location.phone.tracker.navigation.AppDestination.Home)
        }
        bottomNavigationCustom.navMap.setOnClickListener {
            navigationManager.navigateTo(com.nhn.gps.location.phone.tracker.navigation.AppDestination.FamousPlace)
        }
        bottomNavigationCustom.navLocation.setOnClickListener {
            viewModel.openMap()
        }
        bottomNavigationCustom.navShield.setOnClickListener {
            navigationManager.navigateTo(com.nhn.gps.location.phone.tracker.navigation.AppDestination.MyZones)
        }
        bottomNavigationCustom.navProfile.setOnClickListener {
            updateSelectedItem(it.id)
            viewModel.openSettings()
        }

        btnNotifications.setOnClickListener { navigationManager.navigateTo(com.nhn.gps.location.phone.tracker.navigation.AppDestination.Notifications) }

        cardMapPreview.setOnClickListener {
            viewModel.openMap()
        }

        viewZone.root.setOnClickListener { navigationManager.navigateTo(com.nhn.gps.location.phone.tracker.navigation.AppDestination.ZoneAlerts) }
        viewMyZones.root.setOnClickListener { navigationManager.navigateTo(com.nhn.gps.location.phone.tracker.navigation.AppDestination.MyZones) }
        viewStreet.root.setOnClickListener { navigationManager.navigateTo(com.nhn.gps.location.phone.tracker.navigation.AppDestination.FamousPlace) }

        viewFriend.root.setOnClickListener {
            viewModel.openViewFriends()
        }

        phone.root.setOnClickListener {
            viewModel.openPhoneLocator()
        }
    }

    private fun setupExploreRecyclerView() = with(binding.rvExplore) {
        layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        adapter = exploreAdapter
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState
                        .map { it.famousPlaces }
                        .distinctUntilChanged()
                        .collectLatest { places ->
                            exploreAdapter.submitList(places)
                        }
                }

                launch {
                    navigationManager.currentDestination.collectLatest { destination ->
                        val selectedId = when (destination) {
                            is com.nhn.gps.location.phone.tracker.navigation.AppDestination.Home -> R.id.navHome
                            is com.nhn.gps.location.phone.tracker.navigation.AppDestination.FamousPlace -> R.id.navMap
                            is com.nhn.gps.location.phone.tracker.navigation.AppDestination.MyZones -> R.id.navShield
                            else -> null
                        }
                        selectedId?.let { updateSelectedItem(it) }
                    }
                }
            }
        }
    }

    private fun updateSelectedItem(selectedId: Int) = with(binding.bottomNavigationCustom) {
        val navItems = mapOf(
            R.id.navHome to imgHome,
            R.id.navMap to imgMap,
            R.id.navShield to imgShield,
            R.id.navProfile to imgProfile
        )

        navItems.forEach { (id, imageView) ->
            if (id == selectedId) {
                imageView.setBackgroundResource(R.drawable.bg_bottom_nav_selected)
            } else {
                imageView.background = null
            }
        }
    }

    companion object {
        fun newInstance() = HomeFragment()
    }
}
