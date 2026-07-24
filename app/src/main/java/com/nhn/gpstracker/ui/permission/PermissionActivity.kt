package com.nhn.gpstracker.ui.permission

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nhn.gpstracker.base.BaseActivity
import com.nhn.gpstracker.databinding.ActivityPermissionBinding
import com.nhn.gpstracker.ui.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PermissionActivity : BaseActivity<ActivityPermissionBinding, PermissionViewModel>() {

    override val viewModel: PermissionViewModel by viewModels()

    private val locationLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val granted = results.values.all { it }
        viewModel.updatePermission(PermissionType.LOCATION, granted)
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.updatePermission(PermissionType.CAMERA, granted)
    }

    private val notificationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.updatePermission(PermissionType.NOTIFICATION, granted)
    }

    override fun createBinding(inflater: LayoutInflater): ActivityPermissionBinding =
        ActivityPermissionBinding.inflate(inflater)

    override fun setupViews(savedInstanceState: Bundle?) = with(binding) {
        imgBack.setOnClickListener { finish() }

        // Thứ 2: Sử dụng ViewBinding thay cho findViewById
        cardLocation.swPermission.setOnClickListener {
            viewModel.onPermissionSwitchClicked(PermissionType.LOCATION, cardLocation.swPermission.isChecked)
        }

        cardCamera.swPermission.setOnClickListener {
            viewModel.onPermissionSwitchClicked(PermissionType.CAMERA, cardCamera.swPermission.isChecked)
        }

        cardNotification.swPermission.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                viewModel.onPermissionSwitchClicked(PermissionType.NOTIFICATION, cardNotification.swPermission.isChecked)
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
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.permissionState.collect { state ->
                        // Cập nhật UI thông qua binding trực tiếp
                        updateSwitchUi(binding.cardLocation.swPermission, state.isLocationGranted)
                        updateSwitchUi(binding.cardCamera.swPermission, state.isCameraGranted)
                        updateSwitchUi(binding.cardNotification.swPermission, state.isNotificationGranted)
                    }
                }

                launch {
                    viewModel.navigateToMain.collect {
                        startActivity(Intent(this@PermissionActivity, MainActivity::class.java))
                        finish()
                    }
                }

                launch {
                    viewModel.requestPermission.collect { type ->
                        when (type) {
                            PermissionType.LOCATION -> locationLauncher.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
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
        // Nếu quyền hệ thống vẫn còn nhưng người dùng tắt trong App thì không tự bật lại.
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

    private fun updateSwitchUi(sw: com.google.android.material.materialswitch.MaterialSwitch, isGranted: Boolean) {
        sw.isChecked = isGranted
        if (isGranted) {
            sw.trackTintList = ColorStateList.valueOf(Color.parseColor("#35C759"))
            sw.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        } else {
            sw.trackTintList = ColorStateList.valueOf(Color.DKGRAY)
            sw.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        }
    }

    private fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }
}
