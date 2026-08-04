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
        val route = state.currentRoute ?: return
        val fragment = when (route) {
            AppDestination.Permission.route -> PermissionFragment.newInstance()
            AppDestination.SetUpProfile.route -> SetUpProfileFragment.newInstance()
            AppDestination.Home.route -> HomeFragment.newInstance()
            AppDestination.Map.route -> LocationFragment.newInstance()
            AppDestination.AddFriend.route -> AddFriendFragment.newInstance()
            AppDestination.MyFriend.route -> MyFriendFragment.newInstance()
            AppDestination.ShowQrFriend.route -> ShowQrFriendFragment.newInstance()
            AppDestination.PhoneLocator.route -> PhoneLocatorFragment.newInstance()
            else -> HomeFragment.newInstance()
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
