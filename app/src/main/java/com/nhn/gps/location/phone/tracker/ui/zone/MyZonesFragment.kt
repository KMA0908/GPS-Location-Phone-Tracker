package com.nhn.gps.location.phone.tracker.ui.zone

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.data.model.Zone
import com.nhn.gps.location.phone.tracker.data.repository.ZoneRepository
import com.nhn.gps.location.phone.tracker.databinding.FragmentMyZonesLocalBinding
import com.nhn.gps.location.phone.tracker.ui.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MyZonesFragment : BaseFragment<FragmentMyZonesLocalBinding, MainViewModel>(), OnMapReadyCallback {
    override val viewModel: MainViewModel by viewModels({ requireActivity() })
    @Inject lateinit var zoneRepository: ZoneRepository
    private lateinit var adapter: ZoneAdapter
    private var allZones: List<Zone> = emptyList()
    private var map: GoogleMap? = null
    private val circles = mutableListOf<Circle>()

    override fun createBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentMyZonesLocalBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) {
        with(binding) {
            adapter = ZoneAdapter { openEditor(it) }
            recyclerZones.adapter = adapter
            btnBack.setOnClickListener { handleToolbarBack() }
            btnAdd.setOnClickListener { openEditor(null) }
            btnCreate.setOnClickListener { openEditor(null) }
            editSearch.doAfterTextChanged { query -> applyFilter(query?.toString().orEmpty()) }
            (childFragmentManager.findFragmentById(R.id.zonesMap) as? SupportMapFragment)?.getMapAsync(this@MyZonesFragment)
        }
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
        drawZonesOnMap()
    }

    private fun drawZonesOnMap() {
        val map = map ?: return
        circles.forEach(Circle::remove)
        circles.clear()
        allZones.forEach { zone ->
            val center = LatLng(zone.latitude, zone.longitude)
            circles += map.addCircle(CircleOptions().center(center).radius(zone.radiusMeters.toDouble())
                .fillColor(if (zone.status.code == 1) 0x44F44336 else 0x4435C759)
                .strokeColor(if (zone.status.code == 1) 0xFFF44336.toInt() else 0xFF35C759.toInt())
                .strokeWidth(2f))
        }
        allZones.firstOrNull()?.let { zone ->
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(zone.latitude, zone.longitude), 13f))
        }
    }

    private fun applyFilter(query: String) = with(binding) {
        val filtered = if (query.isBlank()) allZones else allZones.filter {
            it.name.contains(query, true) || it.address.contains(query, true)
        }
        adapter.submitList(filtered)
        layoutEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        recyclerZones.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        tvCount.text = if (allZones.isEmpty()) "No zones yet" else "${allZones.size} saved zones"
    }

    private fun openEditor(zone: Zone?) {
        ZoneEditorState.selectedZoneId = zone?.id
        navigationManager.navigateTo(com.nhn.gps.location.phone.tracker.navigation.AppDestination.CreateZone)
    }

    companion object { fun newInstance() = MyZonesFragment() }
}
