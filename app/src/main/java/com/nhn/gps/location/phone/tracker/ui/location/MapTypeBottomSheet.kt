package com.nhn.gps.location.phone.tracker.ui.location

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.databinding.BottomSheetMapTypeBinding

class MapTypeBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetMapTypeBinding? = null
    private val binding get() = _binding!!

    private var options = MapDisplayOptions()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val state = savedInstanceState ?: requireArguments()
        options = MapDisplayOptions(
            baseType = MapBaseType.fromPersistedValue(
                state.getInt(KEY_MAP_TYPE, MapBaseType.NORMAL.persistedValue),
            ),
            trafficEnabled = state.getBoolean(KEY_TRAFFIC_ENABLED),
            buildings3dEnabled = state.getBoolean(KEY_BUILDINGS_3D_ENABLED),
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetMapTypeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.closeMapType.setOnClickListener { dismiss() }
        binding.mapNormal.setOnClickListener { updateOptions(options.selectBaseType(MapBaseType.NORMAL)) }
        binding.mapHybrid.setOnClickListener { updateOptions(options.selectBaseType(MapBaseType.HYBRID)) }
        binding.mapSatellite.setOnClickListener {
            updateOptions(options.selectBaseType(MapBaseType.SATELLITE))
        }
        binding.mapTraffic.setOnClickListener { updateOptions(options.toggleTraffic()) }
        binding.map3d.setOnClickListener { updateOptions(options.toggleBuildings3d()) }
        renderOptions()
    }

    override fun onStart() {
        super.onStart()
        (binding.root.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putOptions(options)
        super.onSaveInstanceState(outState)
    }

    private fun updateOptions(newOptions: MapDisplayOptions) {
        if (newOptions == options) return
        options = newOptions
        renderOptions()
        parentFragmentManager.setFragmentResult(
            REQUEST_KEY,
            Bundle().apply { putOptions(options) },
        )
    }

    private fun renderOptions() = with(binding) {
        renderSelected(cardNormal, options.baseType == MapBaseType.NORMAL)
        renderSelected(cardHybrid, options.baseType == MapBaseType.HYBRID)
        renderSelected(cardSatellite, options.baseType == MapBaseType.SATELLITE)
        renderSelected(cardTraffic, options.trafficEnabled)
        renderSelected(card3d, options.buildings3dEnabled)
    }

    private fun renderSelected(card: MaterialCardView, selected: Boolean) {
        card.strokeColor = ContextCompat.getColor(
            requireContext(),
            if (selected) R.color.ads_primary else R.color.color_d3f6fc,
        )
        card.strokeWidth = ((if (selected) 3 else 1) * resources.displayMetrics.density).toInt()
        card.setCardBackgroundColor(
            ContextCompat.getColor(
                requireContext(),
                if (selected) R.color.language_card_sdk else R.color.white,
            ),
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "MapTypeBottomSheet"
        const val REQUEST_KEY = "map_display_options"
        const val KEY_MAP_TYPE = "map_type"
        const val KEY_TRAFFIC_ENABLED = "traffic_enabled"
        const val KEY_BUILDINGS_3D_ENABLED = "buildings_3d_enabled"

        fun newInstance(options: MapDisplayOptions) = MapTypeBottomSheet().apply {
            arguments = Bundle().apply { putOptions(options) }
        }

        private fun Bundle.putOptions(options: MapDisplayOptions) {
            putInt(KEY_MAP_TYPE, options.baseType.persistedValue)
            putBoolean(KEY_TRAFFIC_ENABLED, options.trafficEnabled)
            putBoolean(KEY_BUILDINGS_3D_ENABLED, options.buildings3dEnabled)
        }
    }
}
