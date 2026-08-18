package com.nhn.gps.location.phone.tracker.ui.zone

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.leansoft.ads.AdManager
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.ads.GpsAdPlacement
import com.nhn.gps.location.phone.tracker.ads.GpsAdViewBinder
import com.nhn.gps.location.phone.tracker.ads.GpsAds
import com.nhn.gps.location.phone.tracker.ads.NativeAdRowAdapter
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.data.model.Zone
import com.nhn.gps.location.phone.tracker.data.model.ZoneType
import com.nhn.gps.location.phone.tracker.data.repository.ZoneRepository
import com.nhn.gps.location.phone.tracker.databinding.FragmentMyZonesLocalBinding
import com.nhn.gps.location.phone.tracker.ui.main.MainViewModel
import com.nhn.gps.location.phone.tracker.ui.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MyZonesFragment : BaseFragment<FragmentMyZonesLocalBinding, MainViewModel>(), OnMapReadyCallback {
    override val viewModel: MainViewModel by viewModels({ requireActivity() })
    @Inject lateinit var zoneRepository: ZoneRepository
    private var allZones: List<Zone> = emptyList()
    private var map: GoogleMap? = null
    private val circles = mutableListOf<Circle>()
    private val markers = mutableListOf<Marker>()
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

    override fun createBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentMyZonesLocalBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) {
        with(binding) {
            bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            
            // Set initial peekHeight to show SearchBar + Handle + ~1-2 items
            bottomSheetBehavior.peekHeight = (300 * resources.displayMetrics.density).toInt()
            
            bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                        layoutListState.isVisible = allZones.isNotEmpty()
                        zonesMap.isVisible = false
                        layoutZoneDetail.isVisible = false
                        (activity as? MainActivity)?.clearScreenAd()
                        (activity as? MainActivity)?.overrideNextRouteInterstitial(null)
                    }
                }
                override fun onSlide(bottomSheet: View, slideOffset: Float) {}
            })

            recyclerZones.adapter = buildZoneListAdapter(emptyList())
            recyclerZonesSheet.adapter = buildZoneListAdapter(emptyList())

            btnBack.setOnClickListener { 
                if (layoutZoneDetail.isVisible) {
                    layoutZoneDetail.isVisible = false
                    (activity as? MainActivity)?.clearScreenAd()
                    (activity as? MainActivity)?.overrideNextRouteInterstitial(null)
                } else if (bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
                    bottomSheetBehavior.isHideable = true
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                } else {
                    handleToolbarBack() 
                }
            }
            btnAdd.setOnClickListener { openEditor(null) }
            btnCreateZone.setOnClickListener { openEditor(null) }
            
            editSearch.doAfterTextChanged { query -> applyFilter(query?.toString().orEmpty()) }
            editSearchSheet.doAfterTextChanged { query -> applyFilter(query?.toString().orEmpty()) }

            cardFilter.setOnClickListener {
                applyFilter(editSearch.text?.toString()?.trim().orEmpty())
            }
            cardFilterSheet.setOnClickListener {
                applyFilter(editSearchSheet.text?.toString()?.trim().orEmpty())
            }

            val actionHandler = { v: View, actionId: Int ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                    v.clearFocus()
                    true
                } else false
            }
            editSearch.setOnEditorActionListener { v, actionId, _ -> actionHandler(v, actionId) }
            editSearchSheet.setOnEditorActionListener { v, actionId, _ -> actionHandler(v, actionId) }
            
            (childFragmentManager.findFragmentById(R.id.zonesMap) as? SupportMapFragment)?.getMapAsync(this@MyZonesFragment)
        }
        
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.layoutZoneDetail.isVisible) {
                    binding.layoutZoneDetail.isVisible = false
                    (activity as? MainActivity)?.clearScreenAd()
                    (activity as? MainActivity)?.overrideNextRouteInterstitial(null)
                } else if (bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
                    bottomSheetBehavior.isHideable = true
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                zoneRepository.zones.collectLatest {
                    allZones = it.sortedBy { zone -> zone.name.lowercase() }
                    applyFilter(binding.editSearch.text?.toString().orEmpty())
                    drawZonesOnMap()
                }
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        googleMap.uiSettings.isMapToolbarEnabled = false
        googleMap.setOnMarkerClickListener { marker ->
            val zoneId = marker.snippet?.toLongOrNull()
            allZones.find { it.id == zoneId }?.let { showZoneDetail(it) }
            true
        }
        drawZonesOnMap()
    }

    private fun drawZonesOnMap() {
        val map = map ?: return
        circles.forEach(Circle::remove)
        circles.clear()
        markers.forEach(Marker::remove)
        markers.clear()
        
        allZones.forEach { zone ->
            val center = LatLng(zone.latitude, zone.longitude)
            circles += map.addCircle(CircleOptions().center(center).radius(zone.radiusMeters.toDouble())
                .fillColor(if (zone.status.code == 1) 0x44F44336 else 0x4435C759)
                .strokeColor(if (zone.status.code == 1) 0xFFF44336.toInt() else 0xFF35C759.toInt())
                .strokeWidth(2f))
                
            markers += map.addMarker(MarkerOptions()
                .position(center)
                .title(zone.name)
                .snippet(zone.id.toString())
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_location_permission)))!!
        }
    }

    private fun applyFilter(query: String) = with(binding) {
        val filtered = if (query.isBlank()) allZones else allZones.filter {
            it.name.contains(query, true)
        }
        recyclerZones.adapter = buildZoneListAdapter(filtered)
        recyclerZonesSheet.adapter = buildZoneListAdapter(filtered)

        val isEmpty = allZones.isEmpty()
        layoutEmptyState.isVisible = isEmpty
        zonesMap.isVisible = !isEmpty
        
        if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
            layoutListState.isVisible = !isEmpty
        } else if (isEmpty) {
            bottomSheetBehavior.isHideable = true
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    private fun openEditor(zone: Zone?) {
        ZoneEditorState.selectedZoneId = zone?.id
        navigationManager.navigateTo(com.nhn.gps.location.phone.tracker.navigation.AppDestination.CreateZone)
    }

    private fun selectZone(zone: Zone) {
        GpsAds.showInterThen(
            placement = GpsAdPlacement.INTER_ZONE,
            fragmentManager = parentFragmentManager,
            next = { if (isAdded) showZoneDetail(zone) },
        )
    }

    private fun navigateToDetail(zone: Zone) {
        ZoneEditorState.selectedZoneId = zone.id
        navigationManager.navigateTo(com.nhn.gps.location.phone.tracker.navigation.AppDestination.ZoneDetail)
    }

    private fun showZoneDetail(zone: Zone) = with(binding) {
        (activity as? MainActivity)?.showScreenBanner(GpsAdPlacement.BANNER_ZONE_DETAIL)
        (activity as? MainActivity)?.overrideNextRouteInterstitial(GpsAdPlacement.INTER_ZONE_DETAIL)
        // Show the Detail UI inside the Sheet
        layoutZoneDetail.isVisible = true
        
        tvSheetName.text = zone.name
        tvSheetAddress.text = if (zone.address.isNotBlank()) {
            "${zone.address} • ${zone.radiusMeters}m"
        } else {
            "${zone.radiusMeters}m"
        }
        
        when (zone.type) {
            ZoneType.HOME -> {
                layoutSheetIcon.setBackgroundResource(R.drawable.bg_zone_home)
                ivSheetIcon.setImageResource(R.drawable.ic_home_zone)
            }
            ZoneType.SCHOOL -> {
                layoutSheetIcon.setBackgroundResource(R.drawable.bg_zone_school)
                ivSheetIcon.setImageResource(R.drawable.ic_school_zone)
            }
            ZoneType.WORK -> {
                layoutSheetIcon.setBackgroundResource(R.drawable.bg_zone_work)
                ivSheetIcon.setImageResource(R.drawable.ic_bag_zone)
            }
            else -> {
                layoutSheetIcon.setBackgroundResource(R.drawable.bg_zone_home)
                ivSheetIcon.setImageResource(R.drawable.ic_home_zone)
            }
        }

        btnEditSheet.setOnClickListener { openEditor(zone) }

        // Switch to map view if not already there
        layoutListState.isVisible = false
        zonesMap.isVisible = true

        val cameraUpdate = CameraUpdateFactory.newLatLngZoom(LatLng(zone.latitude, zone.longitude), 15f)
        
        if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
            map?.animateCamera(cameraUpdate, object : GoogleMap.CancelableCallback {
                override fun onFinish() {
                    bottomSheetBehavior.isHideable = false
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                }
                override fun onCancel() {
                    bottomSheetBehavior.isHideable = false
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                }
            })
        } else {
            map?.animateCamera(cameraUpdate)
            // Ensure it's at least collapsed if it was hidden somehow
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                bottomSheetBehavior.isHideable = false
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }
    }

    override fun onDestroyView() {
        map?.apply {
            setOnMarkerClickListener(null)
            clear()
        }
        map = null
        circles.clear()
        markers.clear()
        binding.recyclerZones.adapter = null
        binding.recyclerZonesSheet.adapter = null
        runCatching { AdManager.instance.destroyNativeAd(GpsAdPlacement.NATIVE_ZONE) }
        super.onDestroyView()
    }

    private fun buildZoneListAdapter(zones: List<Zone>): ConcatAdapter {
        val adapters = mutableListOf<RecyclerView.Adapter<out RecyclerView.ViewHolder>>()
        zones.chunked(3).forEach { group ->
            adapters += ZoneAdapter(
                onClick = { selectZone(it) },
                onActionClick = { navigateToDetail(it) },
            ).apply { submitList(group) }
            if (group.size == 3) {
                adapters += NativeAdRowAdapter(
                    GpsAdPlacement.NATIVE_ZONE,
                    GpsAdViewBinder.NativeFormat.SMALL,
                )
            }
        }
        return ConcatAdapter(adapters)
    }

    companion object { fun newInstance() = MyZonesFragment() }
}
