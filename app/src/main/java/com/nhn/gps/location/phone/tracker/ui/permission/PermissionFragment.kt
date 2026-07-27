package com.nhn.gps.location.phone.tracker.ui.permission

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.google.android.material.materialswitch.MaterialSwitch
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.databinding.FragmentPermissionBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PermissionFragment : BaseFragment<FragmentPermissionBinding, PermissionViewModel>() {

    override val viewModel: PermissionViewModel by viewModels()

    private val locationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val granted = results.values.all { it }
            viewModel.updatePermission(PermissionType.LOCATION, granted)
        }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.updatePermission(PermissionType.CAMERA, granted)
        }

    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.updatePermission(PermissionType.NOTIFICATION, granted)
        }

    override fun createBinding(
        inflater: LayoutInflater, container: ViewGroup?
    ): FragmentPermissionBinding = FragmentPermissionBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) = with(binding) {
        imgBack.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }

        cardLocation.swPermission.setOnClickListener {
            viewModel.onPermissionSwitchClicked(
                PermissionType.LOCATION, cardLocation.swPermission.isChecked
            )
        }

        cardCamera.swPermission.setOnClickListener {
            viewModel.onPermissionSwitchClicked(
                PermissionType.CAMERA, cardCamera.swPermission.isChecked
            )
        }

        cardNotification.swPermission.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                viewModel.onPermissionSwitchClicked(
                    PermissionType.NOTIFICATION, cardNotification.swPermission.isChecked
                )
            } else {
                viewModel.updatePermission(PermissionType.NOTIFICATION, true)
            }
        }

        btnContinue.setOnClickListener {
            viewModel.onContinueClicked()
        }

        btnLater.setOnClickListener {
            viewModel.onLaterClicked()
        }
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.permissionState.collect { state ->
                        updateSwitchUi(binding.cardLocation.swPermission, state.isLocationGranted)
                        updateSwitchUi(binding.cardCamera.swPermission, state.isCameraGranted)
                        updateSwitchUi(
                            binding.cardNotification.swPermission, state.isNotificationGranted
                        )
                    }
                }

                launch {
                    viewModel.requestPermission.collect { type ->
                        when (type) {
                            PermissionType.LOCATION -> locationLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )

                            PermissionType.CAMERA -> cameraLauncher.launch(Manifest.permission.CAMERA)
                            PermissionType.NOTIFICATION -> {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun checkPermissions() {
        if (!isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            viewModel.updatePermission(PermissionType.LOCATION, false)
        }

        if (!isPermissionGranted(Manifest.permission.CAMERA)) {
            viewModel.updatePermission(PermissionType.CAMERA, false)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)) {
                viewModel.updatePermission(PermissionType.NOTIFICATION, false)
            }
        }
    }

    private fun updateSwitchUi(sw: MaterialSwitch, isGranted: Boolean) {
        sw.isChecked = isGranted
        if (isGranted) {
            sw.trackTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    requireContext(), R.color.bg_switch_permission
                )
            )
            sw.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        } else {
            sw.trackTintList = ColorStateList.valueOf(Color.WHITE)
            sw.thumbTintList = ColorStateList.valueOf(Color.DKGRAY)
        }
    }

    private fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(), permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        fun newInstance() = PermissionFragment()
    }
}
