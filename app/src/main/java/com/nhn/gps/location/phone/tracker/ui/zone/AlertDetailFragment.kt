package com.nhn.gps.location.phone.tracker.ui.zone

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.data.model.Zone
import com.nhn.gps.location.phone.tracker.data.model.ZoneAlert
import com.nhn.gps.location.phone.tracker.data.model.ZoneAlertType
import com.nhn.gps.location.phone.tracker.data.model.ZoneStatus
import com.nhn.gps.location.phone.tracker.data.repository.ZoneRepository
import com.nhn.gps.location.phone.tracker.data.repository.UserRepository
import com.nhn.gps.location.phone.tracker.databinding.FragmentAlertDetailLocalBinding
import com.nhn.gps.location.phone.tracker.ui.main.MainViewModel
import com.nhn.gps.location.phone.tracker.ui.location.LocationViewModel
import com.nhn.gps.location.phone.tracker.navigation.AppDestination
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import com.nhn.gps.location.phone.tracker.util.loadAvatar

@AndroidEntryPoint
class AlertDetailFragment : BaseFragment<FragmentAlertDetailLocalBinding, MainViewModel>(), OnMapReadyCallback {
    override val viewModel: MainViewModel by viewModels({ requireActivity() })
    private val locationViewModel: LocationViewModel by activityViewModels()
    
    @Inject lateinit var zoneRepository: ZoneRepository
    @Inject lateinit var userRepository: UserRepository
    
    private var map: GoogleMap? = null
    private var currentAlert: ZoneAlert? = null
    private var targetZone: Zone? = null

    override fun createBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentAlertDetailLocalBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) {
        currentAlert = AlertDetailState.selectedAlert
        with(binding) {
            btnBack.setOnClickListener { handleToolbarBack() }
            btnMenu.setOnClickListener { showAlertMenu() }
            
            val mapFragment = childFragmentManager.findFragmentById(R.id.mapFragment) as? SupportMapFragment
            mapFragment?.getMapAsync(this@AlertDetailFragment)
            
            currentAlert?.let { alert ->
                tvUserName.text = alert.userName
                tvTime.text = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(alert.time))
                tvZoneName.text = alert.zoneName
                tvAddress.text = "Lat: ${alert.latitude}, Lng: ${alert.longitude}"
                tvRadius.text = "Fetching details..."
                tvZoneType.text = "${alert.status.label} Zone"
            }
        }
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                currentAlert?.let { alert ->
                    launch {
                        zoneRepository.zones.collectLatest { zones ->
                            targetZone = zones.find { it.id == alert.zoneId }
                            updateZoneUI()
                            drawOnMap()
                        }
                    }
                    launch {
                        val currentUid = userRepository.getCurrentUserId().orEmpty()
                        val shouldResolveFriend = alert.userId.isNotBlank() && alert.userId != currentUid ||
                            alert.userId.isBlank() && alert.userName != "You"

                        if (shouldResolveFriend) {
                            locationViewModel.friendsLocations.collectLatest { friends ->
                                val friend = if (alert.userId.isNotBlank()) {
                                    friends.firstOrNull { it.id == alert.userId }
                                } else {
                                    friends.filter { it.name.equals(alert.userName, ignoreCase = true) }
                                        .singleOrNull()
                                }
                                friend?.let {
                                    binding.imgAvatar.loadAvatar(
                                        avatarKey = it.avatarKey,
                                        avatarUrl = it.avatarUrl,
                                        fallbackRes = R.drawable.ic_avt,
                                    )
                                }
                            }
                        } else {
                            val profileUid = alert.userId.ifBlank { currentUid }
                            val profile = runCatching {
                                userRepository.getUserProfile(profileUid)
                            }.getOrNull()
                            if (profile != null) {
                                binding.imgAvatar.loadAvatar(
                                    avatarKey = profile.avatarKey,
                                    avatarUrl = profile.avatarUrl,
                                    fallbackRes = R.drawable.ic_avt,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showAlertMenu() {
        val alert = currentAlert ?: return
        PopupMenu(requireContext(), binding.btnMenu).apply {
            menu.add(0, MENU_DELETE, 0, R.string.remove)
            setOnMenuItemClickListener { item ->
                if (item.itemId == MENU_DELETE) {
                    deleteAlert(alert)
                    true
                } else {
                    false
                }
            }
            show()
        }
    }

    private fun deleteAlert(alert: ZoneAlert) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                zoneRepository.deleteAlert(alert.id)
                AlertDetailState.selectedAlert = null
                Toast.makeText(requireContext(), R.string.zone_alert_removed, Toast.LENGTH_SHORT).show()
                navigationManager.navigateTo(AppDestination.ZoneAlerts)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Toast.makeText(requireContext(), R.string.zone_alert_remove_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateZoneUI() = withBinding {
        val alert = currentAlert ?: return@withBinding
        val zone = targetZone
        
        tvUserName.text = getString(
            when (alert.type) {
                ZoneAlertType.ENTER -> R.string.zone_event_entered
                ZoneAlertType.LEAVE -> R.string.zone_event_left
                ZoneAlertType.NEAR_DANGEROUS -> R.string.zone_event_near_dangerous
                ZoneAlertType.RETURNED_SAFE -> R.string.zone_event_returned_safe
            },
            alert.userName,
            alert.zoneName,
        )
        
        zone?.let {
            tvZoneName.text = it.name
            tvRadius.text = getString(R.string.radius_val, it.radiusMeters)
            tvAddress.text = it.address.ifBlank { "Lat: ${alert.latitude}, Lng: ${alert.longitude}" }
            tvZoneType.text = getString(R.string.zone_type_status, it.type.label, it.status.label)
        } ?: run {
            tvRadius.text = getString(R.string.zone_no_longer_exists)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        googleMap.uiSettings.isMapToolbarEnabled = false
        googleMap.uiSettings.isMyLocationButtonEnabled = false
        drawOnMap()
    }

    private fun drawOnMap() {
        val googleMap = map ?: return
        val alert = currentAlert ?: return
        googleMap.clear()

        val alertPos = LatLng(alert.latitude, alert.longitude)
        
        targetZone?.let { zone ->
            val zoneCenter = LatLng(zone.latitude, zone.longitude)
            val color = if (zone.status == ZoneStatus.DANGEROUS) 0x44F44336 else 0x4435C759
            val stroke = if (zone.status == ZoneStatus.DANGEROUS) 0xFFF44336.toInt() else 0xFF35C759.toInt()
            
            googleMap.addCircle(CircleOptions()
                .center(zoneCenter)
                .radius(zone.radiusMeters.toDouble())
                .fillColor(color)
                .strokeColor(stroke)
                .strokeWidth(2f))
        }

        googleMap.addMarker(MarkerOptions()
            .position(alertPos)
            .title(alert.zoneName)
            .snippet(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(alert.time))))

        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(alertPos, 15f))
    }

    companion object {
        private const val MENU_DELETE = 1
        fun newInstance() = AlertDetailFragment()
    }
}
