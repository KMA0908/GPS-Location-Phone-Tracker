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
import com.leansoft.ads.AdManager
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.ads.GpsAdConfig
import com.nhn.gps.location.phone.tracker.ads.GpsAdPlacement
import com.nhn.gps.location.phone.tracker.ads.GpsAdScenario
import com.nhn.gps.location.phone.tracker.ads.GpsAdViewBinder
import com.nhn.gps.location.phone.tracker.ads.GpsAds
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
import com.nhn.gps.location.phone.tracker.ui.settings.EditProfileFragment
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
    private var renderedRoute: String? = null
    private var pendingRoute: String? = null
    private var queuedState: MainUiState? = null
    private var suppressNextRouteInterstitial = false
    private var outgoingInterstitialOverride: String? = null

    override fun createBinding(inflater: LayoutInflater): ActivityMainBinding =
        ActivityMainBinding.inflate(inflater)

    override fun setupViews(savedInstanceState: Bundle?) {
        val target = intent.getStringExtra("TARGET_DESTINATION")
        viewModel.handleIntent(target)
        setupBackPress()
        if (GpsAdConfig.ADS_ENABLED) {
            runCatching { AdManager.instance.preloadAppOpenAd(GpsAdPlacement.AOA_RESUME) }
        }
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateBackWithAd()
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
        val route = state.currentRoute ?: return
        if (route == renderedRoute) return
        if (pendingRoute != null) {
            queuedState = state
            return
        }

        val sourceRoute = renderedRoute
        pendingRoute = route
        val fragment = when (destination) {
            is AppDestination.Permission -> PermissionFragment.newInstance()
            is AppDestination.SetUpProfile -> SetUpProfileFragment.newInstance()
            is AppDestination.Home -> HomeFragment.newInstance()
            is AppDestination.Map -> LocationFragment.newInstance()
            is AppDestination.AddFriend -> AddFriendFragment.newInstance()
            is AppDestination.MyFriend -> MyFriendFragment.newInstance()
            is AppDestination.ShowQrFriend -> ShowQrFriendFragment.newInstance(destination.mode)
            is AppDestination.Settings -> SettingsFragment.newInstance()
            is AppDestination.EditProfile -> EditProfileFragment.newInstance()
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

        val completeNavigation = {
            if (!isFinishing && !isDestroyed &&
                viewModel.uiState.value.currentDestination == destination
            ) {
                replaceFragment(fragment)
                renderedRoute = route
                restoreCurrentScreenAd()
            }
            pendingRoute = null
            val queued = queuedState
            queuedState = null
            if (queued != null && queued.currentRoute != renderedRoute) render(queued)
        }

        val skipInterstitial = suppressNextRouteInterstitial.also {
            suppressNextRouteInterstitial = false
        }
        val placement = outgoingInterstitialOverride
            ?.also { outgoingInterstitialOverride = null }
            ?: sourceRoute?.let(GpsAdScenario::interstitialLeaving)
        if (sourceRoute == null || skipInterstitial || placement == null) {
            completeNavigation()
        } else {
            GpsAds.showInterThen(
                placement = placement,
                fragmentManager = supportFragmentManager,
                next = completeNavigation,
            )
        }
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

    fun navigateBackWithAd() {
        if (isFinishing || isDestroyed) return
        GpsAds.showInterThen(
            placement = GpsAdPlacement.INTER_BACK,
            fragmentManager = supportFragmentManager,
        ) {
            if (isFinishing || isDestroyed) return@showInterThen
            suppressNextRouteInterstitial = true
            if (!viewModel.navigateBack()) finish()
        }
    }

    fun showScreenBanner(placement: String) {
        if (isFinishing || isDestroyed) return
        GpsAdViewBinder.bindBanner(binding.screenAdHost, placement)
    }

    fun showScreenNative(placement: String, format: GpsAdViewBinder.NativeFormat) {
        if (isFinishing || isDestroyed) return
        GpsAdViewBinder.bindNative(binding.screenAdHost, placement, format)
    }

    fun clearScreenAd() {
        GpsAdViewBinder.clear(binding.screenAdHost)
    }

    fun overrideNextRouteInterstitial(placement: String?) {
        outgoingInterstitialOverride = placement
    }

    fun restoreCurrentScreenAd() {
        when (val ad = viewModel.uiState.value.currentRoute?.let(GpsAdScenario::screenAd)) {
            is GpsAdScenario.ScreenAd.Banner -> showScreenBanner(ad.placement)
            is GpsAdScenario.ScreenAd.Native -> showScreenNative(ad.placement, ad.format)
            null -> clearScreenAd()
        }
    }

    override fun onDestroy() {
        GpsAdViewBinder.clear(binding.screenAdHost)
        super.onDestroy()
    }
}
