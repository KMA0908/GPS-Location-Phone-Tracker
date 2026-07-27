package com.nhn.gps.location.phone.tracker.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nhn.gps.location.phone.tracker.base.BaseActivity
import com.nhn.gps.location.phone.tracker.base.UiMessage
import com.nhn.gps.location.phone.tracker.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : BaseActivity<ActivityMainBinding, MainViewModel>() {

    override val viewModel: MainViewModel by viewModels()

    override fun createBinding(inflater: LayoutInflater): ActivityMainBinding =
        ActivityMainBinding.inflate(inflater)

    override fun setupViews(savedInstanceState: Bundle?) = with(binding) {
        openMapButton.setOnClickListener { viewModel.openMap() }
        startTrackingButton.setOnClickListener { viewModel.startTracking() }
        homeButton.setOnClickListener { viewModel.goHome() }
    }

    override fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect(::render)
                }
                launch {
                    viewModel.messages.collect(::showMessage)
                }
            }
        }
    }

    private fun render(state: MainUiState) = with(binding) {
        routeValue.text = state.currentRoute
        openCountValue.text = state.appOpenCount.toString()
    }

    private fun showMessage(message: UiMessage) {
        val text = when (message) {
            is UiMessage.Error -> message.message
            is UiMessage.Info -> message.message
        }
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
