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
        runCatching { AdManager.instance.preloadAppOpenAd(GpsAdPlacement.AOA_RESUME) }
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
        val route = state.currentRoute ?: return
        if (route == renderedRoute) return
        if (pendingRoute != null) {
            queuedState = state
            return
        }

        val sourceRoute = renderedRoute
        pendingRoute = route
        val fragment = when (route) {
            AppDestination.Permission.route -> PermissionFragment.newInstance()
            AppDestination.SetUpProfile.route -> SetUpProfileFragment.newInstance()
            AppDestination.Home.route -> HomeFragment.newInstance()
            AppDestination.Map.route -> LocationFragment.newInstance()
            AppDestination.AddFriend.route -> AddFriendFragment.newInstance()
            AppDestination.MyFriend.route -> MyFriendFragment.newInstance()
            AppDestination.ShowQrFriend.route -> ShowQrFriendFragment.newInstance()
            AppDestination.Settings.route -> SettingsFragment.newInstance()
            AppDestination.SettingsLanguage.route -> SettingsLanguageFragment.newInstance()
            AppDestination.PhoneLocator.route -> PhoneLocatorFragment.newInstance()
            AppDestination.MyZones.route -> MyZonesFragment.newInstance()
            AppDestination.CreateZone.route -> CreateZoneFragment.newInstance()
            AppDestination.AlertDetail.route -> AlertDetailFragment.newInstance()
            AppDestination.ZoneDetail.route -> ZoneDetailFragment.newInstance()
            AppDestination.ZoneAlerts.route -> ZoneAlertsFragment.newInstance()
            AppDestination.Notifications.route -> NotificationsFragment.newInstance()
            AppDestination.FamousPlace.route -> FamousPlaceFragment.newInstance()
            AppDestination.Explore.route -> ExploreFragment.newInstance()
            AppDestination.PlaceDetail.route -> PlaceDetailFragment.newInstance()
            else -> HomeFragment.newInstance()
        }

        val completeNavigation = {
            if (!isFinishing && !isDestroyed && viewModel.uiState.value.currentRoute == route) {
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
