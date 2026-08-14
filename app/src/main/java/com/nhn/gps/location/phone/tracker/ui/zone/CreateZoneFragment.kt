package com.nhn.gps.location.phone.tracker.ui.zone

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.switchmaterial.SwitchMaterial
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.data.model.Zone
import com.nhn.gps.location.phone.tracker.data.model.ZoneStatus
import com.nhn.gps.location.phone.tracker.data.model.ZoneType
import com.nhn.gps.location.phone.tracker.data.repository.PhoneLocatorRepository
import com.nhn.gps.location.phone.tracker.data.repository.ZoneRepository
import com.nhn.gps.location.phone.tracker.data.notification.ZoneMonitoringService
import com.nhn.gps.location.phone.tracker.databinding.FragmentCreateZoneLocalBinding
import com.nhn.gps.location.phone.tracker.ui.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CreateZoneFragment : BaseFragment<FragmentCreateZoneLocalBinding, MainViewModel>(), OnMapReadyCallback {
    override val viewModel: MainViewModel by viewModels({ requireActivity() })
    @Inject lateinit var zoneRepository: ZoneRepository
    @Inject lateinit var phoneLocatorRepository: PhoneLocatorRepository
    private var map: GoogleMap? = null
    private var circle: Circle? = null
    private var center = LatLng(21.0285, 105.8542)
    private var editing: Zone? = null
    private val radius get() = binding.sliderRadius.value.toInt()

    override fun createBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentCreateZoneLocalBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) {
        with(binding) {
            editing = null
            val mapFragment = childFragmentManager.findFragmentById(R.id.zoneMap) as? SupportMapFragment
            mapFragment?.getMapAsync(this@CreateZoneFragment)
            btnBack.setOnClickListener { handleToolbarBack() }
            btnCancel.setOnClickListener { handleToolbarBack() }
            btnSave.setOnClickListener { saveZone() }
            sliderRadius.addOnChangeListener { _, value, _ ->
                tvRadius.text = "Radius: ${value.toInt()}m"
                drawCircle()
            }
            radioSafe.setOnClickListener { drawCircle() }
            radioDangerous.setOnClickListener { drawCircle() }

            switchEnter.setOnCheckedChangeListener { _, isChecked ->
                updateSwitchUi(switchEnter, isChecked)
            }
            switchLeave.setOnCheckedChangeListener { _, isChecked ->
                updateSwitchUi(switchLeave, isChecked)
            }

            // Initial UI state
            updateSwitchUi(switchEnter, switchEnter.isChecked)
            updateSwitchUi(switchLeave, switchLeave.isChecked)

            ZoneEditorState.selectedZoneId?.let { id ->
                viewLifecycleOwner.lifecycleScope.launch {
                    editing = zoneRepository.zones.firstOrNullValue { it.id == id }
                    editing?.let(::bindZone)
                }
            }
        }
    }

    private fun bindZone(zone: Zone) = with(binding) {
        center = LatLng(zone.latitude, zone.longitude)
        edtName.setText(zone.name)
        edtAddress.setText(zone.address)
        sliderRadius.value = zone.radiusMeters.toFloat().coerceIn(40f, 500f)
        tvRadius.text = "Radius: ${zone.radiusMeters}m"
        radioSafe.isChecked = zone.status == ZoneStatus.SAFE
        radioDangerous.isChecked = zone.status == ZoneStatus.DANGEROUS
        
        val typeButtonId = when (zone.type) {
            ZoneType.HOME -> R.id.btnTypeHome
            ZoneType.SCHOOL -> R.id.btnTypeSchool
            ZoneType.WORK -> R.id.btnTypeWork
            ZoneType.OTHER -> R.id.btnTypeOther
        }
        toggleZoneType.check(typeButtonId)

        switchEnter.isChecked = zone.onEnter
        switchLeave.isChecked = zone.onLeave
        updateSwitchUi(switchEnter, zone.onEnter)
        updateSwitchUi(switchLeave, zone.onLeave)
        drawCircle()
    }

    private fun updateSwitchUi(sw: SwitchMaterial, isChecked: Boolean) {
        if (isChecked) {
            sw.trackTintList = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.bg_switch_permission)
            )
            sw.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        } else {
            sw.trackTintList = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.color_bdbdbd)
            )
            sw.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        googleMap.uiSettings.isZoomControlsEnabled = false
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(center, 15f))
        
        // Initial address fetch
        updateAddress()

        // Cập nhật center khi bản đồ di chuyển xong (khớp với UI Drag to center)
        googleMap.setOnCameraIdleListener {
            center = googleMap.cameraPosition.target
            drawCircle()
            updateAddress()
        }

        drawCircle()
    }

    private fun updateAddress() {
        viewLifecycleOwner.lifecycleScope.launch {
            val address = phoneLocatorRepository.getAddressFromLocation(center.latitude, center.longitude)
            binding.edtAddress.setText(address ?: "Unknown location")
        }
    }

    private fun drawCircle() {
        val map = map ?: return
        circle?.remove()
        val fill = if (binding.radioDangerous.isChecked) 0x44F44336 else 0x4435C759
        val stroke = if (binding.radioDangerous.isChecked) 0xFFF44336.toInt() else 0xFF35C759.toInt()
        circle = map.addCircle(CircleOptions().center(center).radius(radius.toDouble()).fillColor(fill).strokeColor(stroke).strokeWidth(2f))
    }

    private fun saveZone() {
        val name = binding.edtName.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) { binding.edtName.error = "Enter a zone name"; return }
        
        val zoneType = when (binding.toggleZoneType.checkedButtonId) {
            R.id.btnTypeHome -> ZoneType.HOME
            R.id.btnTypeSchool -> ZoneType.SCHOOL
            R.id.btnTypeWork -> ZoneType.WORK
            else -> ZoneType.OTHER
        }

        val existingId = editing?.id ?: System.currentTimeMillis()
        val zone = Zone(
            id = existingId, name = name,
            address = binding.edtAddress.text?.toString()?.trim().orEmpty(),
            latitude = center.latitude, longitude = center.longitude,
            type = zoneType, radiusMeters = radius,
            onEnter = binding.switchEnter.isChecked, onLeave = binding.switchLeave.isChecked,
            status = if (binding.radioDangerous.isChecked) ZoneStatus.DANGEROUS else ZoneStatus.SAFE,
            createdAt = editing?.createdAt ?: System.currentTimeMillis()
        )
        viewLifecycleOwner.lifecycleScope.launch {
            zoneRepository.upsert(zone)
            ZoneMonitoringService.start(requireContext())
            ZoneEditorState.selectedZoneId = null
            Toast.makeText(requireContext(), "Zone saved", Toast.LENGTH_SHORT).show()
            navigationManager.navigateBack()
        }
    }

    override fun onDestroyView() {
        map?.apply {
            setOnCameraIdleListener(null)
            clear()
        }
        map = null
        circle = null
        super.onDestroyView()
    }

    companion object { fun newInstance() = CreateZoneFragment() }
}

private suspend fun <T> kotlinx.coroutines.flow.Flow<List<T>>.firstOrNullValue(predicate: (T) -> Boolean): T? =
    first().firstOrNull(predicate)
