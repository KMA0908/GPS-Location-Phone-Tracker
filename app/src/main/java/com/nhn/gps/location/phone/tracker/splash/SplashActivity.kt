package com.nhn.gps.location.phone.tracker.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nhn.gps.location.phone.tracker.base.BaseActivity
import com.nhn.gps.location.phone.tracker.databinding.ActivitySplashBinding
import com.nhn.gps.location.phone.tracker.permission.PermissionActivity
import com.nhn.gps.location.phone.tracker.ui.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
@AndroidEntryPoint
class SplashActivity : BaseActivity<ActivitySplashBinding, SplashViewModel>() {

    override val viewModel: SplashViewModel by viewModels()

    override fun createBinding(inflater: LayoutInflater): ActivitySplashBinding =
        ActivitySplashBinding.inflate(inflater)

    override fun setupViews(savedInstanceState: Bundle?) {
    }

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.navigationEvent.collect { event ->
                    when (event) {
                        is SplashNavigation.ToMain -> {
                            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                        }
                        is SplashNavigation.ToPermission -> {
                            startActivity(Intent(this@SplashActivity, PermissionActivity::class.java))
                        }
                    }
                    finish()
                }
            }
        }
    }
}
