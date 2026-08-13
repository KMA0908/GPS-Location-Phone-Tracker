package com.nhn.gps.location.phone.tracker.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.databinding.FragmentHomeBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : BaseFragment<FragmentHomeBinding, MainViewModel>() {

    override val viewModel: MainViewModel by viewModels({ requireActivity() })

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentHomeBinding = FragmentHomeBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) = with(binding) {
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
            navigationManager.navigateTo(com.nhn.gps.location.phone.tracker.navigation.AppDestination.ZoneAlerts)
        }
        bottomNavigationCustom.navProfile.setOnClickListener {
            updateSelectedItem(it.id)
            viewModel.openSettings()
        }

        btnNotifications.setOnClickListener { navigationManager.navigateTo(com.nhn.gps.location.phone.tracker.navigation.AppDestination.Notifications) }

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

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                navigationManager.currentDestination.collectLatest { destination ->
                    val selectedId = when (destination) {
                        is com.nhn.gps.location.phone.tracker.navigation.AppDestination.Home -> R.id.navHome
                        is com.nhn.gps.location.phone.tracker.navigation.AppDestination.FamousPlace -> R.id.navMap
                        is com.nhn.gps.location.phone.tracker.navigation.AppDestination.ZoneAlerts -> R.id.navShield
                        else -> null
                    }
                    selectedId?.let { updateSelectedItem(it) }
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
