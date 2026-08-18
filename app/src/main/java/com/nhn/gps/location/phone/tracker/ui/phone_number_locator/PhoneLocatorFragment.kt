package com.nhn.gps.location.phone.tracker.ui.phone_number_locator

import android.os.Bundle
import androidx.core.os.BundleCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.transition.TransitionManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import android.graphics.Bitmap
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.ads.GpsAdPlacement
import com.nhn.gps.location.phone.tracker.ads.GpsAds
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.data.model.Country
import com.nhn.gps.location.phone.tracker.data.model.UserLocation
import com.nhn.gps.location.phone.tracker.data.model.UserProfile
import com.nhn.gps.location.phone.tracker.databinding.FragmentPhoneLocatorBinding
import com.nhn.gps.location.phone.tracker.databinding.LayoutCustomMarkerBinding
import com.nhn.gps.location.phone.tracker.util.MapMarkerHelper
import com.nhn.gps.location.phone.tracker.util.MapUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PhoneLocatorFragment : BaseFragment<FragmentPhoneLocatorBinding, PhoneLocatorViewModel>(), OnMapReadyCallback {

    private enum class ScreenMode { SEARCH, RESULT }
    private var currentMode: ScreenMode = ScreenMode.SEARCH

    override val viewModel: PhoneLocatorViewModel by viewModels()
    
    private var selectedCountry: Country? = null
    private var googleMap: GoogleMap? = null
    private var currentUserMarker: Marker? = null

    // Lưu trữ thông tin Marker nếu Map chưa sẵn sàng
    private data class PendingMarker(val latLng: LatLng, val profile: UserProfile)
    private var pendingMarker: PendingMarker? = null

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentPhoneLocatorBinding = FragmentPhoneLocatorBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) = with(binding) {
        btnBack.setOnClickListener {
            if (currentMode == ScreenMode.RESULT) {
                viewModel.resetState()
            } else {
                handleToolbarBack()
            }
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentMode == ScreenMode.RESULT) {
                    viewModel.resetState()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        layoutCountry.setOnClickListener {
            showCountrySelector()
        }
        
        layoutCountry.editText?.setOnClickListener {
            showCountrySelector()
        }

        layoutCountry.setEndIconOnClickListener {
            showCountrySelector()
        }

        btnFind.setOnClickListener {
            val phone = layoutPhone.editText?.text?.toString()?.trim().orEmpty()
            val dialCode = selectedCountry?.dialCode ?: DEFAULT_DIAL_CODE
            
            if (phone.isNotBlank()) {
                GpsAds.showInterThen(
                    placement = GpsAdPlacement.INTER_PHONE_NUMBER,
                    fragmentManager = parentFragmentManager,
                    next = { if (isAdded) viewModel.findUser(dialCode, phone) },
                )
            } else {
                layoutPhone.error = getString(R.string.err_enter_phone)
            }
        }

        setupFragmentResultListeners()
        observeViewModel()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap?.uiSettings?.isMapToolbarEnabled = false
        
        // Nếu có Marker đang chờ (từ kết quả Search trước đó), hãy vẽ nó ngay
        pendingMarker?.let {
            drawMarkerOnMap(it.latLng, it.profile)
            pendingMarker = null
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    render(state)
                }
            }
        }
    }

    private fun render(state: PhoneLocatorUiState) {
        binding.loadingIndicator.visibility = if (state is PhoneLocatorUiState.Loading) View.VISIBLE else View.GONE
        binding.btnFind.isEnabled = state !is PhoneLocatorUiState.Loading

        when (state) {
            is PhoneLocatorUiState.Loading -> {}
            is PhoneLocatorUiState.Success -> {
                applyScreenMode(ScreenMode.RESULT)

                val loc = state.location
                if (loc != null) {
                    val latLng = LatLng(loc.latitude, loc.longitude)
                    updateMapMarker(latLng, state.profile)
                    showInfoBottomSheet(state.profile, state.location, state.address)
                } else {
                    showInfoBottomSheet(state.profile, null, null)
                    Toast.makeText(requireContext(), R.string.err_location_not_available, Toast.LENGTH_LONG).show()
                }
            }
            is PhoneLocatorUiState.NoResult -> {
                showSearchMode()
                Toast.makeText(requireContext(), R.string.err_no_user_found, Toast.LENGTH_LONG).show()
            }
            is PhoneLocatorUiState.Error -> {
                showSearchMode()
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
            }
            is PhoneLocatorUiState.Idle -> {
                showSearchMode()
            }
        }
    }

    private fun showSearchMode() {
        applyScreenMode(ScreenMode.SEARCH)
    }

    private fun applyScreenMode(mode: ScreenMode) = with(binding) {
        if (currentMode == mode && mode == ScreenMode.SEARCH) return@with // Avoid unnecessary resets in SEARCH mode
        currentMode = mode
        TransitionManager.beginDelayedTransition(mainContent)
        
        val isSearching = mode == ScreenMode.SEARCH
        val searchVisibility = if (isSearching) View.VISIBLE else View.GONE

        cardTrusted.visibility = searchVisibility
        mapSpace.visibility = searchVisibility
        cardInput.visibility = searchVisibility
        
        // Cập nhật hiển thị Map và Hero Background
        if (isSearching) {
            imgHeroBackground.visibility = View.VISIBLE
            mapContainer.visibility = View.GONE
            // Xóa marker và reset camera khi quay lại chế độ tìm kiếm
            currentUserMarker?.remove()
            currentUserMarker = null
            googleMap?.clear()
        } else {
            // Hiệu ứng Fade Out cho Hero Background khi chuyển sang RESULT
            if (imgHeroBackground.visibility == View.VISIBLE) {
                imgHeroBackground.animate()
                    .alpha(0f)
                    .setDuration(250)
                    .withEndAction {
                        imgHeroBackground.visibility = View.GONE
                        imgHeroBackground.alpha = 1.0f 
                    }
                    .start()
            }
            
            mapContainer.visibility = View.VISIBLE
            
            // Khởi tạo Map động nếu chưa tồn tại trong mapContainer
            var mapFrag = childFragmentManager.findFragmentById(R.id.mapContainer) as? SupportMapFragment
            if (mapFrag == null) {
                mapFrag = SupportMapFragment.newInstance()
                childFragmentManager.beginTransaction()
                    .replace(R.id.mapContainer, mapFrag)
                    .commit()
                mapFrag.getMapAsync(this@PhoneLocatorFragment)
            } else if (googleMap == null) {
                mapFrag.getMapAsync(this@PhoneLocatorFragment)
            }
            
            // Cuộn lên đầu để xem Map rõ hơn
            scrollView.smoothScrollTo(0, 0)
        }
    }

    private fun updateMapMarker(latLng: LatLng, profile: UserProfile) {
        if (googleMap != null) {
            drawMarkerOnMap(latLng, profile)
        } else {
            // Lưu lại để vẽ sau khi onMapReady
            pendingMarker = PendingMarker(latLng, profile)
        }
    }

    private fun drawMarkerOnMap(latLng: LatLng, profile: UserProfile) {
        googleMap?.let { map ->
            currentUserMarker?.remove()
            
            // Create initial marker with default avatar
            val bitmap = MapMarkerHelper.createDefaultMarkerBitmap(requireContext(), style = MapMarkerHelper.MarkerStyle.GLOW)
            
            currentUserMarker = map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(profile.name)
                    .icon(BitmapDescriptorFactory.fromBitmap(bitmap))
            )
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM))
            
            // Update marker icon with profile avatar
            currentUserMarker?.let { marker ->
                MapMarkerHelper.updateMarkerIcon(requireContext(), marker, profile.avatarUrl, style = MapMarkerHelper.MarkerStyle.GLOW)
            }
        }
    }

    private fun updateMarkerIcon(marker: Marker, avatarUrl: String) {
        MapMarkerHelper.updateMarkerIcon(requireContext(), marker, avatarUrl, style = MapMarkerHelper.MarkerStyle.GLOW)
    }

    private fun setupFragmentResultListeners() {
        parentFragmentManager.setFragmentResultListener(
            CountrySelectorBottomSheet.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val country = BundleCompat.getParcelable(bundle, CountrySelectorBottomSheet.EXTRA_COUNTRY, Country::class.java)
            country?.let {
                updateSelectedCountry(it)
            }
        }
    }

    private fun showInfoBottomSheet(profile: UserProfile, location: UserLocation?, address: String? = null) {
        PhoneLocatorInfoBottomSheet.newInstance(profile, location, address)
            .show(parentFragmentManager, PhoneLocatorInfoBottomSheet.TAG)
    }

    private fun showCountrySelector() {
        CountrySelectorBottomSheet.newInstance(selectedCountry?.iso)
            .show(parentFragmentManager, CountrySelectorBottomSheet.TAG)
    }

    private fun updateSelectedCountry(country: Country) {
        selectedCountry = country
        binding.layoutCountry.editText?.setText(getString(R.string.country_format, country.emoji, country.name, country.dialCode))
        binding.layoutPhone.error = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        googleMap = null
        currentUserMarker = null
    }

    companion object {
        private const val DEFAULT_ZOOM = 15f
        private const val DEFAULT_DIAL_CODE = "+84"
        fun newInstance() = PhoneLocatorFragment()
    }
}
