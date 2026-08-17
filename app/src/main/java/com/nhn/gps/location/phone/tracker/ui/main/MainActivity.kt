package com.nhn.gps.location.phone.tracker.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nhn.gps.location.phone.tracker.base.BaseActivity
import com.nhn.gps.location.phone.tracker.base.UiMessage
import androidx.fragment.app.Fragment
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.databinding.ActivityMainBinding
import com.nhn.gps.location.phone.tracker.navigation.AppDestination
import com.nhn.gps.location.phone.tracker.ui.friend.AddFriendFragment
import com.nhn.gps.location.phone.tracker.ui.friend.MyFriendFragment
import com.nhn.gps.location.phone.tracker.ui.friend.ShowQrFriendFragment
import com.nhn.gps.location.phone.tracker.ui.location.LocationFragment
import com.nhn.gps.location.phone.tracker.ui.permission.PermissionFragment
import com.nhn.gps.location.phone.tracker.ui.phone_number_locator.PhoneLocatorFragment
import com.nhn.gps.location.phone.tracker.ui.setup_profile.SetUpProfileFragment
import com.nhn.gps.location.phone.tracker.ui.zone.AlertDetailFragment
import com.nhn.gps.location.phone.tracker.ui.settings.SettingsFragment
import com.nhn.gps.location.phone.tracker.ui.settings.SettingsLanguageFragment
import com.nhn.gps.location.phone.tracker.ui.zone.CreateZoneFragment
import com.nhn.gps.location.phone.tracker.ui.zone.MyZonesFragment
import com.nhn.gps.location.phone.tracker.ui.zone.NotificationsFragment
import com.nhn.gps.location.phone.tracker.ui.zone.ZoneAlertsFragment
import com.nhn.gps.location.phone.tracker.ui.zone.ZoneDetailFragment
import com.nhn.gps.location.phone.tracker.ui.explore.FamousPlaceFragment
import com.nhn.gps.location.phone.tracker.ui.explore.ExploreFragment
import com.nhn.gps.location.phone.tracker.ui.explore.PlaceDetailFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : BaseActivity<ActivityMainBinding, MainViewModel>() {

    override val viewModel: MainViewModel by viewModels()

    override fun createBinding(inflater: LayoutInflater): ActivityMainBinding =
        ActivityMainBinding.inflate(inflater)

    override fun setupViews(savedInstanceState: Bundle?) {
        val target = intent.getStringExtra("TARGET_DESTINATION")
        viewModel.handleIntent(target)
        setupBackPress()
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentRoute = viewModel.uiState.value.currentRoute
                // Đồng bộ logic: Home và Permission nhấn Back hệ thống -> Thoát app
                if (currentRoute == AppDestination.Permission.route || 
                    currentRoute == AppDestination.Home.route) {
                    finish()
                } else {
                    // Các màn hình khác: navigateBack
                    if (!viewModel.navigateBack()) {
                        finish()
                    }
                }
            }
        })
    }

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collectLatest { state ->
                        render(state)
                    }
                }
                launch {
                    viewModel.messages.collect(::showMessage)
                }
            }
        }
    }

    private fun render(state: MainUiState) {
        val destination = state.currentDestination ?: return
        val fragment = when (destination) {
            is AppDestination.Permission -> PermissionFragment.newInstance()
            is AppDestination.SetUpProfile -> SetUpProfileFragment.newInstance()
            is AppDestination.Home -> HomeFragment.newInstance()
            is AppDestination.Map -> LocationFragment.newInstance()
            is AppDestination.AddFriend -> AddFriendFragment.newInstance()
            is AppDestination.MyFriend -> MyFriendFragment.newInstance()
            is AppDestination.ShowQrFriend -> ShowQrFriendFragment.newInstance()
            is AppDestination.Settings -> SettingsFragment.newInstance()
            is AppDestination.SettingsLanguage -> SettingsLanguageFragment.newInstance()
            is AppDestination.PhoneLocator -> PhoneLocatorFragment.newInstance()
            is AppDestination.MyZones -> MyZonesFragment.newInstance()
            is AppDestination.CreateZone -> CreateZoneFragment.newInstance(
                initialLatitude = destination.initialLatitude,
                initialLongitude = destination.initialLongitude,
                initialAddress = destination.initialAddress,
                initialPlaceName = destination.initialPlaceName
            )
            is AppDestination.Tracking -> HomeFragment.newInstance() // Fallback if Tracking fragment is missing
            is AppDestination.AlertDetail -> AlertDetailFragment.newInstance()
            is AppDestination.ZoneDetail -> ZoneDetailFragment.newInstance()
            is AppDestination.ZoneAlerts -> ZoneAlertsFragment.newInstance()
            is AppDestination.Notifications -> NotificationsFragment.newInstance()
            is AppDestination.FamousPlace -> FamousPlaceFragment.newInstance()
            is AppDestination.Explore -> ExploreFragment.newInstance()
            is AppDestination.PlaceDetail -> PlaceDetailFragment.newInstance()
        }
        replaceFragment(fragment)
    }

    private fun replaceFragment(fragment: Fragment) {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (currentFragment != null && currentFragment::class == fragment::class) {
            return
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commitAllowingStateLoss()
    }

    private fun showMessage(message: UiMessage) {
        val text = when (message) {
            is UiMessage.Error -> message.message
            is UiMessage.Info -> message.message
        }
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
