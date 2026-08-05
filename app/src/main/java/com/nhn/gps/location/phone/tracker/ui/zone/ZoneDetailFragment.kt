package com.nhn.gps.location.phone.tracker.ui.zone

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.data.model.Zone
import com.nhn.gps.location.phone.tracker.data.model.ZoneStatus
import com.nhn.gps.location.phone.tracker.data.model.ZoneType
import com.nhn.gps.location.phone.tracker.data.repository.ZoneRepository
import com.nhn.gps.location.phone.tracker.databinding.FragmentZoneDetailLocalBinding
import com.nhn.gps.location.phone.tracker.ui.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class ZoneDetailFragment : BaseFragment<FragmentZoneDetailLocalBinding, MainViewModel>() {
    override val viewModel: MainViewModel by viewModels({ requireActivity() })
    
    @Inject 
    lateinit var zoneRepository: ZoneRepository

    private var currentZone: Zone? = null

    override fun createBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentZoneDetailLocalBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) {
        with(binding) {
            btnBack.setOnClickListener { handleToolbarBack() }
            btnEdit.setOnClickListener { navigateToEdit() }
            tvViewAllZones.setOnClickListener { handleToolbarBack() }
            
            // Slider is read-only in detail view by default, or used for display
            sliderRadius.isEnabled = false
        }
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val zoneId = ZoneEditorState.selectedZoneId ?: return@repeatOnLifecycle
                zoneRepository.zones.collect { zones ->
                    val zone = zones.find { it.id == zoneId }
                    zone?.let { bindZone(it) }
                }
            }
        }
    }

    private fun bindZone(zone: Zone) = with(binding) {
        currentZone = zone
        tvZoneName.text = zone.name
        tvZoneAddress.text = zone.address.ifBlank { "Location not set" }
        tvRadiusValue.text = "${zone.radiusMeters} m"
        sliderRadius.value = zone.radiusMeters.toFloat().coerceIn(40f, 1000f)
        
        val dateFormat = SimpleDateFormat("MMMM d'st' yyyy", Locale.ENGLISH)
        tvCreatedAt.text = dateFormat.format(Date(zone.createdAt))

        // Set icon and gradient based on type
        when(zone.type) {
            ZoneType.HOME -> {
                layoutIcon.setBackgroundResource(R.drawable.bg_zone_home)
                ivZoneIcon.setImageResource(R.drawable.ic_launcher) // Replace with ic_home if available
            }
            ZoneType.SCHOOL -> {
                layoutIcon.setBackgroundResource(R.drawable.bg_zone_school)
                ivZoneIcon.setImageResource(R.drawable.ic_launcher)
            }
            ZoneType.WORK -> {
                layoutIcon.setBackgroundResource(R.drawable.bg_zone_work)
                ivZoneIcon.setImageResource(R.drawable.ic_launcher)
            }
            else -> {
                layoutIcon.setBackgroundResource(R.drawable.bg_zone_home)
                ivZoneIcon.setImageResource(R.drawable.ic_launcher)
            }
        }
    }

    private fun navigateToEdit() {
        // Since ZoneEditorState.selectedZoneId is already set, we just navigate to CreateZone
        navigationManager.navigateTo(com.nhn.gps.location.phone.tracker.navigation.AppDestination.CreateZone)
    }

    companion object {
        fun newInstance() = ZoneDetailFragment()
    }
}
