package com.nhn.gps.location.phone.tracker.ui.zone

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import android.widget.ArrayAdapter
import androidx.core.content.ContextCompat
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
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.switchmaterial.SwitchMaterial
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.data.model.Zone
import com.nhn.gps.location.phone.tracker.data.model.ZoneStatus
import com.nhn.gps.location.phone.tracker.data.model.ZoneType
import com.nhn.gps.location.phone.tracker.data.repository.ZoneRepository
import com.nhn.gps.location.phone.tracker.data.notification.ZoneMonitoringService
import com.nhn.gps.location.phone.tracker.databinding.FragmentCreateZoneLocalBinding
import com.nhn.gps.location.phone.tracker.ui.main.MainViewModel
import com.nhn.gps.location.phone.tracker.ui.main.ZoneAddressSearchState
import com.nhn.gps.location.phone.tracker.ui.main.ZonePlacePrediction
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CreateZoneFragment : BaseFragment<FragmentCreateZoneLocalBinding, MainViewModel>(), OnMapReadyCallback {
    override val viewModel: MainViewModel by viewModels({ requireActivity() })
    @Inject lateinit var zoneRepository: ZoneRepository
    private var map: GoogleMap? = null
    private var circle: Circle? = null
    private var center = LatLng(21.0285, 105.8542)
    private var selectedZoneLocation: LatLng? = null
    private var selectedFormattedAddress: String? = null
    private var selectedPlaceId: String? = null
    private var selectedMapMarker: Marker? = null
    private var isApplyingSearchResult = false
    private var currentPredictions: List<ZonePlacePrediction> = emptyList()
    private lateinit var searchSuggestionAdapter: ArrayAdapter<String>
    private var editing: Zone? = null
    private var hasAppliedInitialLocation = false
    private val radius get() = binding.sliderRadius.value.toInt()

    override fun createBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentCreateZoneLocalBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) {
        with(binding) {
            editing = null
            hasAppliedInitialLocation = false
            selectedZoneLocation = null
            selectedFormattedAddress = null
            selectedPlaceId = null

            searchSuggestionAdapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                mutableListOf(),
            )
            listSearchSuggestions.adapter = searchSuggestionAdapter
            listSearchSuggestions.setOnItemClickListener { _, _, position, _ ->
                currentPredictions.getOrNull(position)?.let(viewModel::selectZoneAddressPrediction)
            }

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

            // Search logic
            btnSearchLocation.setOnClickListener { submitAddressSearch() }
            edtSearchLocation.doAfterTextChanged { editable ->
                if (isApplyingSearchResult) return@doAfterTextChanged
                val query = editable?.toString()?.trim().orEmpty()
                if (query.length < MIN_SEARCH_QUERY_LENGTH) {
                    currentPredictions = emptyList()
                    listSearchSuggestions.visibility = View.GONE
                    viewModel.cancelZoneAddressSearch()
                } else {
                    viewModel.searchZoneAddress(query, currentVisibleMapBounds())
                }
            }
            edtSearchLocation.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    submitAddressSearch()
                    true
                } else {
                    false
                }
            }

            // Initial UI state
            updateSwitchUi(switchEnter, switchEnter.isChecked)
            updateSwitchUi(switchLeave, switchLeave.isChecked)

            val restoredSelection = restoreSelection(savedInstanceState)
            ZoneEditorState.selectedZoneId?.takeUnless { restoredSelection }?.let { id ->
                viewLifecycleOwner.lifecycleScope.launch {
                    editing = zoneRepository.zones.firstOrNullValue { it.id == id }
                    editing?.let {
                        bindZone(it)
                        hasAppliedInitialLocation = true
                    }
                }
            }

            // If not editing, try to apply location from arguments
            if (ZoneEditorState.selectedZoneId == null && !restoredSelection) {
                getInitialLocationFromArgs()?.let { argCenter ->
                    val argAddress = arguments?.getString(ARG_ADDRESS)
                    selectLocation(argCenter, argAddress, null, animateCamera = false)
                    hasAppliedInitialLocation = true
                }
            }

            observeSearchState()
        }
    }

    private fun getInitialLocationFromArgs(): LatLng? {
        val lat = if (arguments?.containsKey(ARG_LAT) == true) arguments?.getDouble(ARG_LAT) else null
        val lng = if (arguments?.containsKey(ARG_LNG) == true) arguments?.getDouble(ARG_LNG) else null
        return if (lat != null && lng != null && isValidCoordinate(lat, lng)) {
            LatLng(lat, lng)
        } else null
    }

    private fun isValidCoordinate(lat: Double, lng: Double): Boolean {
        return lat in -90.0..90.0 && lng in -180.0..180.0 && !lat.isNaN() && !lng.isNaN()
    }

    private fun submitAddressSearch() {
        val query = binding.edtSearchLocation.text?.toString()?.trim().orEmpty()
        if (query.isEmpty()) {
            Toast.makeText(requireContext(), R.string.search_address_empty, Toast.LENGTH_SHORT).show()
            return
        }
        hideKeyboard()
        currentPredictions.firstOrNull()?.let {
            viewModel.selectZoneAddressPrediction(it)
        } ?: viewModel.searchZoneAddress(query, currentVisibleMapBounds(), debounce = false)
    }

    private fun currentVisibleMapBounds() = if (selectedZoneLocation == null) {
        null
    } else {
        runCatching { map?.projection?.visibleRegion?.latLngBounds }.getOrNull()
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.edtSearchLocation.windowToken, 0)
    }

    private fun observeSearchState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.zoneAddressSearchState.collect { state ->
                    renderSearchState(state)
                }
            }
        }
    }

    private fun renderSearchState(state: ZoneAddressSearchState) = with(binding) {
        when (state) {
            is ZoneAddressSearchState.Idle -> {
                progressSearchLocation.visibility = android.view.View.GONE
                btnSearchLocation.visibility = android.view.View.VISIBLE
                listSearchSuggestions.visibility = View.GONE
            }
            is ZoneAddressSearchState.Loading -> {
                progressSearchLocation.visibility = android.view.View.VISIBLE
                btnSearchLocation.visibility = android.view.View.GONE
                currentPredictions = emptyList()
                listSearchSuggestions.visibility = View.GONE
            }
            is ZoneAddressSearchState.Predictions -> {
                progressSearchLocation.visibility = View.GONE
                btnSearchLocation.visibility = View.VISIBLE
                currentPredictions = state.items
                searchSuggestionAdapter.clear()
                searchSuggestionAdapter.addAll(state.items.map { it.displayText })
                searchSuggestionAdapter.notifyDataSetChanged()
                listSearchSuggestions.visibility = if (state.items.isEmpty()) View.GONE else View.VISIBLE
            }
            is ZoneAddressSearchState.Success -> {
                progressSearchLocation.visibility = android.view.View.GONE
                btnSearchLocation.visibility = android.view.View.VISIBLE
                val target = LatLng(state.location.latitude, state.location.longitude)
                if (!isValidCoordinate(target.latitude, target.longitude) ||
                    (target.latitude == 0.0 && target.longitude == 0.0)
                ) {
                    Toast.makeText(requireContext(), R.string.search_location_not_found, Toast.LENGTH_SHORT).show()
                    viewModel.consumeZoneAddressSearchResult()
                    return@with
                }
                selectLocation(
                    location = target,
                    address = state.location.formattedAddress,
                    placeId = state.location.placeId,
                    animateCamera = true,
                )
                isApplyingSearchResult = true
                edtSearchLocation.setText(state.displayName ?: state.location.formattedAddress)
                edtSearchLocation.setSelection(edtSearchLocation.text?.length ?: 0)
                isApplyingSearchResult = false
                currentPredictions = emptyList()
                listSearchSuggestions.visibility = View.GONE
                Log.d(TAG, "zone_marker_pinned placeId=${maskPlaceId(selectedPlaceId)}")
                viewModel.consumeZoneAddressSearchResult()
            }
            is ZoneAddressSearchState.NotFound -> {
                progressSearchLocation.visibility = android.view.View.GONE
                btnSearchLocation.visibility = android.view.View.VISIBLE
                listSearchSuggestions.visibility = View.GONE
                currentPredictions = emptyList()
                Toast.makeText(requireContext(), R.string.search_location_not_found, Toast.LENGTH_SHORT).show()
                viewModel.consumeZoneAddressSearchResult()
            }
            is ZoneAddressSearchState.Error -> {
                progressSearchLocation.visibility = android.view.View.GONE
                btnSearchLocation.visibility = android.view.View.VISIBLE
                listSearchSuggestions.visibility = View.GONE
                currentPredictions = emptyList()
                Toast.makeText(requireContext(), state.messageRes, Toast.LENGTH_SHORT).show()
                viewModel.consumeZoneAddressSearchResult()
            }
        }
    }

    private fun bindZone(zone: Zone) = with(binding) {
        selectLocation(
            location = LatLng(zone.latitude, zone.longitude),
            address = zone.address,
            placeId = null,
            animateCamera = false,
        )
        map?.moveCamera(CameraUpdateFactory.newLatLngZoom(center, 15f))
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
        
        selectedZoneLocation?.let { pinMarker(it) }

        // Camera movement only changes the viewport. It must never change the selected zone.
        googleMap.setOnCameraIdleListener {
            Log.v(TAG, "zone_camera_idle selectionPinned=${selectedZoneLocation != null}")
        }

        drawCircle()
    }

    private fun selectLocation(
        location: LatLng,
        address: String?,
        placeId: String?,
        animateCamera: Boolean,
    ) {
        val selection = ZoneLocationSelectionPolicy.select(location, address, placeId) ?: return
        selectedZoneLocation = selection.location
        selectedFormattedAddress = selection.address?.takeIf { it.isNotBlank() }
        selectedPlaceId = selection.placeId
        center = selection.location
        selectedFormattedAddress?.let(binding.edtAddress::setText)
        pinMarker(location)
        drawCircle()
        if (animateCamera) map?.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 15f))
    }

    private fun pinMarker(location: LatLng) {
        val googleMap = map ?: return
        selectedMapMarker?.remove()
        selectedMapMarker = googleMap.addMarker(
            MarkerOptions().position(location).title(selectedFormattedAddress)
        )
    }

    private fun drawCircle() {
        val map = map ?: return
        circle?.remove()
        val location = selectedZoneLocation ?: return
        val fill = if (binding.radioDangerous.isChecked) 0x44F44336 else 0x4435C759
        val stroke = if (binding.radioDangerous.isChecked) 0xFFF44336.toInt() else 0xFF35C759.toInt()
        circle = map.addCircle(CircleOptions().center(location).radius(radius.toDouble()).fillColor(fill).strokeColor(stroke).strokeWidth(2f))
    }

    private fun saveZone() {
        val name = binding.edtName.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) { binding.edtName.error = "Enter a zone name"; return }
        val currentSelection = selectedZoneLocation?.let {
            PinnedZoneLocation(it, selectedFormattedAddress, selectedPlaceId)
        }
        val location = ZoneLocationSelectionPolicy.resolveForSave(currentSelection)
        if (location == null) {
            Toast.makeText(requireContext(), R.string.zone_location_required, Toast.LENGTH_SHORT).show()
            return
        }
        
        val zoneType = when (binding.toggleZoneType.checkedButtonId) {
            R.id.btnTypeHome -> ZoneType.HOME
            R.id.btnTypeSchool -> ZoneType.SCHOOL
            R.id.btnTypeWork -> ZoneType.WORK
            else -> ZoneType.OTHER
        }

        val existingId = editing?.id ?: System.currentTimeMillis()
        val zone = Zone(
            id = existingId, name = name,
            address = selectedFormattedAddress ?: binding.edtAddress.text?.toString()?.trim().orEmpty(),
            latitude = location.latitude, longitude = location.longitude,
            type = zoneType, radiusMeters = radius,
            onEnter = binding.switchEnter.isChecked, onLeave = binding.switchLeave.isChecked,
            status = if (binding.radioDangerous.isChecked) ZoneStatus.DANGEROUS else ZoneStatus.SAFE,
            createdAt = editing?.createdAt ?: System.currentTimeMillis()
        )
        viewLifecycleOwner.lifecycleScope.launch {
            zoneRepository.upsert(zone)
            Log.d(TAG, "zone_saved_from_search_result hasPlaceId=${selectedPlaceId != null}")
            ZoneMonitoringService.start(requireContext())
            ZoneEditorState.selectedZoneId = null
            Toast.makeText(requireContext(), "Zone saved", Toast.LENGTH_SHORT).show()
            navigationManager.navigateBack()
        }
    }

    override fun onDestroyView() {
        viewModel.cancelZoneAddressSearch()
        map?.apply {
            setOnCameraIdleListener(null)
            clear()
        }
        map = null
        circle = null
        selectedMapMarker = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selectedZoneLocation?.let {
            outState.putDouble(STATE_SELECTED_LAT, it.latitude)
            outState.putDouble(STATE_SELECTED_LNG, it.longitude)
            outState.putString(STATE_SELECTED_ADDRESS, selectedFormattedAddress)
            outState.putString(STATE_SELECTED_PLACE_ID, selectedPlaceId)
        }
    }

    private fun restoreSelection(savedInstanceState: Bundle?): Boolean {
        val state = savedInstanceState ?: return false
        if (!state.containsKey(STATE_SELECTED_LAT) || !state.containsKey(STATE_SELECTED_LNG)) return false
        val location = LatLng(
            state.getDouble(STATE_SELECTED_LAT),
            state.getDouble(STATE_SELECTED_LNG),
        )
        if (!isValidCoordinate(location.latitude, location.longitude)) return false
        selectLocation(
            location,
            state.getString(STATE_SELECTED_ADDRESS),
            state.getString(STATE_SELECTED_PLACE_ID),
            animateCamera = false,
        )
        return true
    }

    private fun maskPlaceId(placeId: String?): String = placeId
        ?.takeLast(4)
        ?.padStart(7, '*')
        ?: "none"

    companion object {
        private const val ARG_LAT = "arg_lat"
        private const val ARG_LNG = "arg_lng"
        private const val ARG_ADDRESS = "arg_address"
        private const val ARG_PLACE_NAME = "arg_place_name"
        private const val TAG = "CreateZoneFragment"
        private const val MIN_SEARCH_QUERY_LENGTH = 2
        private const val STATE_SELECTED_LAT = "state_selected_lat"
        private const val STATE_SELECTED_LNG = "state_selected_lng"
        private const val STATE_SELECTED_ADDRESS = "state_selected_address"
        private const val STATE_SELECTED_PLACE_ID = "state_selected_place_id"

        fun newInstance(
            initialLatitude: Double? = null,
            initialLongitude: Double? = null,
            initialAddress: String? = null,
            initialPlaceName: String? = null
        ) = CreateZoneFragment().apply {
            arguments = Bundle().apply {
                initialLatitude?.let { putDouble(ARG_LAT, it) }
                initialLongitude?.let { putDouble(ARG_LNG, it) }
                initialAddress?.let { putString(ARG_ADDRESS, it) }
                initialPlaceName?.let { putString(ARG_PLACE_NAME, it) }
            }
        }
    }
}

private suspend fun <T> kotlinx.coroutines.flow.Flow<List<T>>.firstOrNullValue(predicate: (T) -> Boolean): T? =
    first().firstOrNull(predicate)
