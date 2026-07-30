package com.nhn.gps.location.phone.tracker.ui.location

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.GroundOverlay
import com.google.android.gms.maps.model.GroundOverlayOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.databinding.FragmentLocationBinding
import com.nhn.gps.location.phone.tracker.databinding.LayoutCustomMarkerBinding
import com.nhn.gps.location.phone.tracker.navigation.AppDestination
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.card.MaterialCardView
import com.nhn.gps.location.phone.tracker.ui.friend.FriendAdapter
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

    override val viewModel: LocationViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by viewModels({ requireActivity() })

    @Inject
    lateinit var compassManager: CompassManager

    private var googleMap: GoogleMap? = null
    private val DEFAULT_ZOOM = 15f
    private val BASE_CONE_HEIGHT = 500.0 // Chiều dài cơ sở tại zoom 15

    private var selfMarker: Marker? = null
    private var directionOverlay: GroundOverlay? = null
    private val friendMarkers = mutableMapOf<String, Marker>()
    private val friendAvatars = mutableMapOf<String, String>()
    private var selfAvatarUrl: String? = null

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<MaterialCardView>
    private var bottomSheetCallback: BottomSheetBehavior.BottomSheetCallback? = null
    private lateinit var friendAdapter: FriendAdapter

    private var isCompassEnabled = false
    private var hasAutoZoomed = false
    private var pendingDestination: AppDestination? = null

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentLocationBinding = FragmentLocationBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) = with(binding) {
        val mapFragment =
            childFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this@LocationFragment)

        cardBack.setOnClickListener {
            handleToolbarBack()
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

        cardImgFriend.setOnClickListener {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            } else {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }

        setupFriendBottomSheet()
    }

    private fun setupFriendBottomSheet() = with(binding) {
        bottomSheetBehavior = BottomSheetBehavior.from(friendBottomSheetLayout.friendBottomSheet)
        bottomSheetBehavior.isHideable = true
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        friendAdapter = FriendAdapter { _ ->
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            navigationManager.navigateTo(AppDestination.MyFriend)
        }

        friendBottomSheetLayout.rvFriends.layoutManager = LinearLayoutManager(requireContext())
        friendBottomSheetLayout.rvFriends.adapter = friendAdapter

        friendBottomSheetLayout.layoutEmpty.btnAddFriendEmpty.setOnClickListener {
            if (isAdded) {
                pendingDestination = AppDestination.AddFriend
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            }
        }

        friendBottomSheetLayout.btnAddFriend.setOnClickListener {
            if (isAdded) {
                pendingDestination = AppDestination.AddFriend
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            }
        }

        friendBottomSheetLayout.tvViewAll.setOnClickListener {
            if (isAdded) {
                pendingDestination = AppDestination.MyFriend
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            }
        }

        bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                withBinding {
                    when (newState) {
                        BottomSheetBehavior.STATE_EXPANDED,
                        BottomSheetBehavior.STATE_HALF_EXPANDED,
                        BottomSheetBehavior.STATE_DRAGGING,
                        BottomSheetBehavior.STATE_SETTLING -> {
                            cardImgFriend.setCardBackgroundColor(
                                resources.getColor(R.color.bg_botton_friend, null)
                            )
                        }

                        BottomSheetBehavior.STATE_HIDDEN,
                        BottomSheetBehavior.STATE_COLLAPSED -> {
                            cardImgFriend.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
                            pendingDestination?.let {
                                navigationManager.navigateTo(it)
                                pendingDestination = null
                            }
                        }
                    }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                withBinding {
                    val bottomSheetTop = bottomSheet.top
                    val margin = 16 * resources.displayMetrics.density
                    val parentHeight = (root as ViewGroup).height

                    if (bottomSheetTop < parentHeight) {
                        val targetTranslationY = -(parentHeight - bottomSheetTop + margin)
                        // Bù đắp cho margin mặc định 28dp của cardSearch
                        val defaultBottomMargin = 28 * resources.displayMetrics.density
                        cardSearch.translationY = targetTranslationY + defaultBottomMargin
                    } else {
                        cardSearch.translationY = 0f
                    }
                }
            }
        }
        bottomSheetBehavior.addBottomSheetCallback(bottomSheetCallback!!)
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
                        if (isGranted) {
                            viewModel.getCurrentLocation()
                        }
                    }
                }

                // Quan sát vị trí Marker
                launch {
                    combine(
                        viewModel.selfLocation,
                        viewModel.friendsLocations,
                        mainViewModel.userAvatar,
                        viewModel.isFriendsDataLoaded
                    ) { self, friends, avatar, friendsLoaded ->
                        DataPackage(self, friends, avatar, friendsLoaded)
                    }.collectLatest { data ->
                        updateMarkersWithAvatars(data.self, data.friends, data.avatar)
                        if (!hasAutoZoomed && data.self != null && data.friendsLoaded) {
                            centerCameraOnAll()
                            hasAutoZoomed = true
                        }
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

                // Quan sát danh sách bạn bè cho Bottom Sheet
                launch {
                    viewModel.friendsLocations.collectLatest { friends ->
                        val displayList = if (friends.size > 2) friends.take(2) else friends
                        friendAdapter.submitList(displayList)
                        updateBottomSheetUi(friends)
                    }
                }
            }
        }
    }

    private fun updateBottomSheetUi(friends: List<com.nhn.gps.location.phone.tracker.data.model.FriendLocation>) =
        with(binding.friendBottomSheetLayout) {
            tvFriendCount.text = "Friends (${friends.size})"
            if (friends.isEmpty()) {
                layoutEmpty.root.visibility = View.VISIBLE
                rvFriends.visibility = View.GONE
                btnAddFriend.visibility = View.GONE
                tvViewAll.visibility = View.GONE
            } else {
                layoutEmpty.root.visibility = View.GONE
                rvFriends.visibility = View.VISIBLE
                btnAddFriend.visibility = View.VISIBLE
                tvViewAll.visibility = if (friends.size > 2) View.VISIBLE else View.GONE
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

    private fun updateMarkersWithAvatars(
        self: LatLng?,
        friends: List<com.nhn.gps.location.phone.tracker.data.model.FriendLocation>,
        selfAvatar: String
    ) {
        if (googleMap == null) return

        self?.let { latLng ->
            showSelfMarker(latLng, selfAvatar) 
        }

        showFriendMarkers(friends)
    }

    private fun showSelfMarker(location: LatLng, avatarUrl: String) {
        val map = googleMap ?: return
        if (selfMarker == null) {
            selfMarker = map.addMarker(
                MarkerOptions()
                    .position(location)
                    .anchor(0.5f, 1f)
                    .flat(false)
                    .zIndex(10f)
            )
            updateMarkerIcon(selfMarker!!, avatarUrl)
        } else {
            selfMarker?.position = location
            if (selfAvatarUrl != avatarUrl) {
                updateMarkerIcon(selfMarker!!, avatarUrl)
            }
        }
        selfAvatarUrl = avatarUrl

        if (directionOverlay == null) {
            createDirectionCone(map, location)
        } else {
            directionOverlay?.position = location
        }
        directionOverlay?.isVisible = isCompassEnabled
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
                friendAvatars.remove(entry.key)
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
                        .anchor(0.5f, 1f)
                        .flat(false)
                        .rotation(0f)
                )
                if (marker != null) {
                    friendMarkers[friend.id] = marker
                    updateMarkerIcon(marker, friend.avatarUrl)
                }
            } else {
                existingMarker.position = position
                existingMarker.title = friend.name
                existingMarker.rotation = 0f
                existingMarker.isFlat = false
                if (friendAvatars[friend.id] != friend.avatarUrl) {
                    updateMarkerIcon(existingMarker, friend.avatarUrl)
                }
            }
            friendAvatars[friend.id] = friend.avatarUrl
        }
    }

    private fun updateMarkerIcon(marker: Marker, avatarUrl: String) {
        val markerViewBinding = LayoutCustomMarkerBinding.inflate(layoutInflater)
        
        if (avatarUrl.isEmpty()) {
            markerViewBinding.imgAvatar.setImageResource(R.drawable.ic_avt_location)
            val bitmap = createBitmapFromView(markerViewBinding.root)
            marker.setIcon(BitmapDescriptorFactory.fromBitmap(bitmap))
            return
        }

        Glide.with(this)
            .asBitmap()
            .load(avatarUrl)
            .circleCrop()
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    markerViewBinding.imgAvatar.setImageBitmap(resource)
                    val bitmap = createBitmapFromView(markerViewBinding.root)
                    marker.setIcon(BitmapDescriptorFactory.fromBitmap(bitmap))
                }

                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                }

                override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                    markerViewBinding.imgAvatar.setImageResource(R.drawable.ic_avt_location)
                    val bitmap = createBitmapFromView(markerViewBinding.root)
                    marker.setIcon(BitmapDescriptorFactory.fromBitmap(bitmap))
                }
            })
    }

    private fun createBitmapFromView(view: View): Bitmap {
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        val bitmap = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    private fun centerCameraOnSelf() {
        val map = googleMap ?: return
        viewModel.selfLocation.value?.let {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(it, DEFAULT_ZOOM))
        }
    }

    private fun centerCameraOnAll() {
        val map = googleMap ?: return
        val self = viewModel.selfLocation.value
        val friends = viewModel.friendsLocations.value

        if (self == null && friends.isEmpty()) return

        val builder = LatLngBounds.Builder()
        self?.let { builder.include(it) }
        friends.forEach {
            builder.include(LatLng(it.latitude, it.longitude))
        }

        try {
            val bounds = builder.build()
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150))
        } catch (e: Exception) {
            self?.let {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(it, DEFAULT_ZOOM))
            }
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
        directionOverlay?.isVisible = isCompassEnabled
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
            binding.itemCompass.root.setCardBackgroundColor(android.graphics.Color.WHITE)
        }
    }

    private fun updateDirectionUI(bearing: Float) {
        val selfLoc = viewModel.selfLocation.value ?: return
        val map = googleMap ?: return

        selfMarker?.rotation = 0f

        if (directionOverlay == null) {
            createDirectionCone(map, selfLoc)
        }

        directionOverlay?.isVisible = isCompassEnabled
        directionOverlay?.position = selfLoc
        directionOverlay?.bearing = bearing
        updateDirectionConeSize(map.cameraPosition.zoom)
    }

    private fun createDirectionCone(map: GoogleMap, location: LatLng) {
        directionOverlay = map.addGroundOverlay(
            GroundOverlayOptions()
                .image(BitmapDescriptorFactory.fromResource(R.drawable.ic_polygon))
                .position(location, 10f)
                .anchor(0.5f, 1f)
                .zIndex(9f)
        )
    }

    private fun updateDirectionConeSize(zoom: Float) {
        val height = (BASE_CONE_HEIGHT * Math.pow(1.5, (zoom - 15).toDouble()))
            .coerceIn(20.0, 300.0).toFloat()
        val width = height * 0.6f
        directionOverlay?.setDimensions(width, height)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        
        map.setOnCameraIdleListener {
            if (isCompassEnabled) {
                updateDirectionUI(compassManager.bearing.value)
            }

            // Check if all markers are in view, if not, auto-zoom logic could go here
            // but the user only wanted it "once" or on button click.
        }
    }

    override fun onStart() {
        super.onStart()
        if (isCompassEnabled) {
            compassManager.start()
        }
    }

    override fun onResume() {
        super.onResume()
        checkLocationPermission()
    }

    private fun checkLocationPermission() {
        val fineLocationGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            requireContext(),
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarseLocationGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            requireContext(),
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val isGranted = fineLocationGranted || coarseLocationGranted
        
        mainViewModel.updateLocationPermissionStatus(isGranted)

        if (!isGranted) {
            viewModel.stopLocationUpdates()
            viewModel.clearLocationData()
        } else {
            viewModel.getCurrentLocation()
        }
    }

    override fun onStop() {
        super.onStop()
        compassManager.stop()
    }

    override fun onDestroyView() {
        bottomSheetCallback?.let {
            if (::bottomSheetBehavior.isInitialized) {
                bottomSheetBehavior.removeBottomSheetCallback(it)
            }
        }
        bottomSheetCallback = null
        super.onDestroyView()
        selfMarker = null
        directionOverlay = null
        friendMarkers.clear()
    }

    companion object {
        fun newInstance() = LocationFragment()

        private data class DataPackage(
            val self: LatLng?,
            val friends: List<com.nhn.gps.location.phone.tracker.data.model.FriendLocation>,
            val avatar: String,
            val friendsLoaded: Boolean
        )
    }
}
