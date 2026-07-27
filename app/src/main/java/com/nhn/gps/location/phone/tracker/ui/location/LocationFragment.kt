package com.nhn.gps.location.phone.tracker.ui.location

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polygon
import com.google.android.gms.maps.model.PolygonOptions
import com.google.maps.android.SphericalUtil
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.databinding.FragmentLocationBinding
import com.nhn.gps.location.phone.tracker.navigation.AppDestination
import com.nhn.gps.location.phone.tracker.navigation.NavigationManager
import com.nhn.gps.location.phone.tracker.ui.main.MainViewModel
import com.nhn.gps.location.phone.tracker.ui.permission.LocationPermissionBottomSheet
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LocationFragment : BaseFragment<FragmentLocationBinding, LocationViewModel>(),
    OnMapReadyCallback {

    override val viewModel: LocationViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels({ requireActivity() })

    @Inject
    lateinit var navigationManager: NavigationManager

    @Inject
    lateinit var compassManager: CompassManager

    private var googleMap: GoogleMap? = null
    private val DEFAULT_ZOOM = 15f
    private val BASE_CONE_LENGTH = 180.0 // Chiều dài cơ sở tại zoom 15

    private var selfMarker: Marker? = null
    private var directionCone: Polygon? = null
    private val friendMarkers = mutableMapOf<String, Marker>()

    private var isCompassEnabled = false

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentLocationBinding = FragmentLocationBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) = with(binding) {
        val mapFragment =
            childFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this@LocationFragment)

        cardBack.setOnClickListener {
            navigationManager.navigateTo(AppDestination.Home)
        }

        itemLocation.root.setOnClickListener {
            centerCameraOnSelf()
        }

        itemLayerStack.root.setOnClickListener {
            cycleMapType()
        }

        itemCompass.root.setOnClickListener {
            toggleCompass()
        }
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Quan sát quyền
                launch {
                    combine(
                        mainViewModel.isLocationPermanentlyEnabled,
                        mainViewModel.isSessionLocationGranted
                    ) { permanent, session ->
                        permanent || session
                    }.collectLatest { isGranted ->
                        updateUiForPermission(isGranted)
                    }
                }

                // Quan sát vị trí Marker
                launch {
                    combine(
                        viewModel.selfLocation,
                        viewModel.friendsLocations
                    ) { self, friends ->
                        Pair(self, friends)
                    }.collectLatest { (self, friends) ->
                        updateMarkers(self, friends)
                        if (isCompassEnabled) {
                            updateDirectionUI(compassManager.bearing.value)
                        }
                    }
                }

                // Quan sát Bearing từ CompassManager
                launch {
                    compassManager.bearing.collect { bearing ->
                        if (isCompassEnabled) {
                            updateDirectionUI(bearing)
                        }
                    }
                }
            }
        }
    }

    private fun updateUiForPermission(isGranted: Boolean) = with(binding) {
        if (isGranted) {
            viewDim.visibility = View.GONE
            imgAccessLocation.visibility = View.GONE
            groupMapUI.visibility = View.VISIBLE
        } else {
            viewDim.visibility = View.VISIBLE
            imgAccessLocation.visibility = View.VISIBLE
            groupMapUI.visibility = View.GONE

            if (childFragmentManager.findFragmentByTag(LocationPermissionBottomSheet.TAG) == null) {
                LocationPermissionBottomSheet.newInstance().show(
                    childFragmentManager, LocationPermissionBottomSheet.TAG
                )
            }
        }
    }

    private fun updateMarkers(
        self: LatLng?,
        friends: List<com.nhn.gps.location.phone.tracker.data.model.FriendLocation>
    ) {
        if (googleMap == null) return

        self?.let {
            showSelfMarker(it)
        }

        showFriendMarkers(friends)

        updateCamera(self, friends)
    }

    private fun showSelfMarker(location: LatLng) {
        val map = googleMap ?: return
        if (selfMarker == null) {
            selfMarker = map.addMarker(
                MarkerOptions()
                    .position(location)
                    .anchor(0.5f, 1f)
                    //.icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_map_my_location))
                    .flat(true)
                    .zIndex(10f)
            )
        } else {
            selfMarker?.position = location
        }
    }

    private fun showFriendMarkers(friends: List<com.nhn.gps.location.phone.tracker.data.model.FriendLocation>) {
        val map = googleMap ?: return

        val friendIds = friends.map { it.id }.toSet()
        val iterator = friendMarkers.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!friendIds.contains(entry.key)) {
                entry.value.remove()
                iterator.remove()
            }
        }

        friends.forEach { friend ->
            val existingMarker = friendMarkers[friend.id]
            val position = LatLng(friend.latitude, friend.longitude)
            if (existingMarker == null) {
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(position)
                        .title(friend.name)
                        .snippet(friend.id)
                )
                if (marker != null) {
                    friendMarkers[friend.id] = marker
                }
            } else {
                existingMarker.position = position
                existingMarker.title = friend.name
            }
        }
    }

    private fun updateCamera(
        self: LatLng?,
        friends: List<com.nhn.gps.location.phone.tracker.data.model.FriendLocation>
    ) {
        val map = googleMap ?: return

        if (friends.isEmpty()) {
            self?.let {
                if ((googleMap?.cameraPosition?.zoom ?: 0f) < 2f) {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(it, DEFAULT_ZOOM))
                }
            }
        } else {
            val builder = LatLngBounds.Builder()
            self?.let { builder.include(it) }
            friends.forEach {
                builder.include(LatLng(it.latitude, it.longitude))
            }
            try {
                val bounds = builder.build()
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150))
            } catch (e: Exception) {
            }
        }
    }

    private fun centerCameraOnSelf() {
        val map = googleMap ?: return
        viewModel.selfLocation.value?.let {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(it, DEFAULT_ZOOM))
        }
    }

    private fun cycleMapType() {
        val map = googleMap ?: return
        val nextType = when (map.mapType) {
            GoogleMap.MAP_TYPE_NORMAL -> GoogleMap.MAP_TYPE_SATELLITE
            GoogleMap.MAP_TYPE_SATELLITE -> GoogleMap.MAP_TYPE_TERRAIN
            GoogleMap.MAP_TYPE_TERRAIN -> GoogleMap.MAP_TYPE_HYBRID
            else -> GoogleMap.MAP_TYPE_NORMAL
        }
        map.mapType = nextType
    }

    private fun toggleCompass() {
        isCompassEnabled = !isCompassEnabled
        if (isCompassEnabled) {
            compassManager.start()
            binding.itemCompass.root.setCardBackgroundColor(
                resources.getColor(
                    R.color.bg_switch_permission,
                    null
                )
            )
        } else {
            compassManager.stop()
            selfMarker?.rotation = 0f
            directionCone?.isVisible = false
            binding.itemCompass.root.setCardBackgroundColor(android.graphics.Color.WHITE)
        }
    }

    /**
     * Cập nhật hướng xoay của Self Marker và tọa độ của Direction Cone.
     */
    private fun updateDirectionUI(bearing: Float) {
        val selfLoc = viewModel.selfLocation.value ?: return
        val map = googleMap ?: return

        // 1. Xoay Marker (Đã yêu cầu giữ nguyên icon self nên set rotation = 0)
        selfMarker?.rotation = 0f

        // 2. Tính toán độ dài Cone dựa trên Zoom
        val currentZoom = map.cameraPosition.zoom
        // Công thức: Chiều dài tăng dần theo mức zoom để dễ quan sát khi phóng to
        // Tại zoom 15 là BASE_CONE_LENGTH (100m). Mỗi đơn vị zoom tăng/giảm, chiều dài thay đổi theo hệ số 1.5
        val coneLength =
            (BASE_CONE_LENGTH * Math.pow(1.5, (currentZoom - 15).toDouble())).coerceIn(10.0, 1000.0)

        // 3. Cập nhật Direction Cone (Polygon)
        val coneAngle = 40.0 // Góc mở của hình quạt (độ)

        // Tính toán 2 đỉnh ngoài của tam giác
        val leftEdge =
            SphericalUtil.computeOffset(selfLoc, coneLength, (bearing - coneAngle / 2).toDouble())
        val rightEdge =
            SphericalUtil.computeOffset(selfLoc, coneLength, (bearing + coneAngle / 2).toDouble())

        val points = listOf(selfLoc, leftEdge, rightEdge)

        if (directionCone == null) {
            directionCone = map.addPolygon(
                PolygonOptions()
                    .addAll(points)
                    .fillColor(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.color_Polygon
                        )
                    ) // Màu xanh 30% alpha
                    .strokeWidth(0f)
                    .zIndex(9f) // Nằm ngay dưới Self Marker (10f)
            )
        } else {
            directionCone?.points = points
            directionCone?.isVisible = true
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        // Lắng nghe khi camera dừng di chuyển (bao gồm cả khi kết thúc thao tác zoom)
        map.setOnCameraIdleListener {
            if (isCompassEnabled) {
                updateDirectionUI(compassManager.bearing.value)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (isCompassEnabled) {
            compassManager.start()
        }
    }

    override fun onStop() {
        super.onStop()
        // Dừng sensor để tiết kiệm tài nguyên khi không ở trong màn hình
        compassManager.stop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        selfMarker = null
        directionCone = null
        friendMarkers.clear()
    }

    companion object {
        fun newInstance() = LocationFragment()
    }
}
