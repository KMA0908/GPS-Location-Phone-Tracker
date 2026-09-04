package com.nhn.gps.location.phone.tracker.ui.permission

import android.Manifest
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.ads.ResumeAdGuard
import com.nhn.gps.location.phone.tracker.databinding.BottomSheetPermissionBinding
import com.nhn.gps.location.phone.tracker.navigation.AppDestination
import com.nhn.gps.location.phone.tracker.navigation.NavigationManager
import com.nhn.gps.location.phone.tracker.ui.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LocationPermissionBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetPermissionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PermissionViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels({ requireActivity() })

    @Inject
    lateinit var navigationManager: NavigationManager

    private val locationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        ResumeAdGuard.onSystemDialogFinished()
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.updatePermission(PermissionType.LOCATION, true)
            mainViewModel.setSessionLocationGranted(true)
            dismiss()
            navigationManager.navigateTo(AppDestination.Map)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetPermissionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
    }

    override fun onStart() {
        super.onStart()
        // Dùng binding.root.parent để thay thế findViewById cho design_bottom_sheet
        (binding.root.parent as? View)?.setBackgroundResource(android.R.color.transparent)

        dialog?.window?.let { window ->
            val params = window.attributes
            window.attributes = params
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        navigationManager.navigateTo(AppDestination.Home)
    }

    private fun setupViews() = with(binding) {
        btnAllowWhileUsing.setOnClickListener {
            showLocationDisclosure {
                ResumeAdGuard.onSystemDialogRequested()
                locationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            }
        }

        // Android's own permission dialog offers a one-time option where
        // supported; a custom second button cannot enforce that distinction.
        btnAllowOnce.visibility = View.GONE

        txtNotNow.setOnClickListener {
            dismiss()
            navigationManager.navigateTo(AppDestination.Home)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "LocationPermissionBottomSheet"
        fun newInstance() = LocationPermissionBottomSheet()
    }
}
