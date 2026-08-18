package com.nhn.gps.location.phone.tracker.ui.location

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
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
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.GroundOverlay
import com.google.android.gms.maps.model.GroundOverlayOptions
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.RoundCap
import com.google.android.gms.maps.model.StrokeStyle
import com.google.android.gms.maps.model.StyleSpan
import com.google.maps.android.SphericalUtil
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.ads.GpsAdPlacement
import com.nhn.gps.location.phone.tracker.ads.ResumeAdGuard
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.databinding.FragmentLocationBinding
import com.nhn.gps.location.phone.tracker.databinding.LayoutCustomMarkerBinding
import com.nhn.gps.location.phone.tracker.data.repository.DrivingRoute
import com.nhn.gps.location.phone.tracker.data.repository.GoogleRoutesRepository
import com.nhn.gps.location.phone.tracker.navigation.AppDestination
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.card.MaterialCardView
import com.nhn.gps.location.phone.tracker.ui.friend.FriendAdapter
import com.nhn.gps.location.phone.tracker.data.model.FriendLocation
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doAfterTextChanged
import com.nhn.gps.location.phone.tracker.ui.main.MainViewModel
import com.nhn.gps.location.phone.tracker.ui.main.MainActivity
import com.nhn.gps.location.phone.tracker.ui.permission.LocationPermissionBottomSheet
import com.nhn.gps.location.phone.tracker.util.MapMarkerHelper
import com.nhn.gps.location.phone.tracker.util.MapUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LocationFragment : BaseFragment<FragmentLocationBinding, LocationViewModel>(),
    OnMapReadyCallback {

    override val viewModel: LocationViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by viewModels({ requireActivity() })

    @Inject
    lateinit var compassManager: CompassManager

    @Inject
    lateinit var routesRepository: GoogleRoutesRepository

    private var googleMap: GoogleMap? = null
    private val DEFAULT_ZOOM = 15f
    private val BASE_CONE_HEIGHT = 500.0 // Chiều dài cơ sở tại zoom 15

    private var selfMarker: Marker? = null
    private var selectedDestinationMarker: Marker? = null
    private var routeOutline: Polyline? = null
    private var routeLine: Polyline? = null
    private var directionOverlay: GroundOverlay? = null
    private val friendMarkers = mutableMapOf<String, Marker>()
    private val friendAvatars = mutableMapOf<String, String>()
    private val friendNames = mutableMapOf<String, String>()
    private var selfAvatarUrl: String? = null
    private var selfName: String? = null

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<MaterialCardView>
    private var bottomSheetCallback: BottomSheetBehavior.BottomSheetCallback? = null
    private lateinit var friendAdapter: FriendAdapter
    private lateinit var friendSearchAdapter: FriendAdapter
    private lateinit var friendSearchHistoryAdapter: FriendSearchHistoryAdapter
    private lateinit var friendSearchBottomSheetBehavior: BottomSheetBehavior<MaterialCardView>

    private var isCompassEnabled = false
    private var hasAutoZoomed = false
    private var lastDataPackage: DataPackage? = null
    private var pendingDestination: AppDestination? = null
    private var activeRouteName: String? = null
    private var activeRoutePosition: LatLng? = null
    private var activeRouteFriendId: String? = null
    private var selectedFriendMarkerId: String? = null
    private var pendingMapRouteRequest: MapRouteRequest? = null
    private var routeJob: Job? = null
    private var lastRouteOrigin: LatLng? = null
    private var lastRouteDestination: LatLng? = null
    private var lastRouteRequestAt = 0L

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentLocationBinding = FragmentLocationBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) = with(binding) {
        initializeGoogleMap()

        cardBack.setOnClickListener {
            if (activeRoutePosition != null) {
                clearRoute()
            } else {
                handleToolbarBack()
            }
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

        search.setOnClickListener { openFriendSearchSheet() }

        setupFriendBottomSheet()
        setupFriendSearchBottomSheet()
        setupDirectionPanel()
    }

    private fun setupFriendSearchBottomSheet() = with(binding) {
        friendSearchBottomSheetBehavior = BottomSheetBehavior.from(friendSearchBottomSheetLayout.friendSearchBottomSheet).apply {
            isHideable = true
            skipCollapsed = true
            isFitToContents = false
            expandedOffset = 80
            state = BottomSheetBehavior.STATE_HIDDEN
        }
        friendSearchAdapter = FriendAdapter(showMoreButton = false, onItemClick = ::onSearchFriendClick)
        friendSearchHistoryAdapter = FriendSearchHistoryAdapter(::onSearchFriendClick) { viewModel.removeFriendSearchHistory(it.id) }
        friendSearchBottomSheetLayout.rvFriendSearch.layoutManager = LinearLayoutManager(requireContext())
        friendSearchBottomSheetLayout.rvFriendSearch.adapter = friendSearchAdapter
        friendSearchBottomSheetLayout.rvFriendSearchHistory.layoutManager = LinearLayoutManager(requireContext())
        friendSearchBottomSheetLayout.rvFriendSearchHistory.adapter = friendSearchHistoryAdapter
        friendSearchBottomSheetLayout.edtFriendSearch.doAfterTextChanged { viewModel.updateFriendSearchInput(it?.toString().orEmpty()) }
        friendSearchBottomSheetLayout.edtFriendSearch.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER) { viewModel.submitFriendSearch(); true } else false
        }
        friendSearchBottomSheetLayout.searchInputLayout.setEndIconOnClickListener { viewModel.clearFriendSearch() }
        friendSearchBottomSheetLayout.btnClearHistory.setOnClickListener { viewModel.clearFriendSearchHistory() }
        friendSearchScrim.setOnClickListener { closeFriendSearchSheet() }
    }

    private fun openFriendSearchSheet() {
        viewModel.openFriendSearch()
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        binding.friendSearchScrim.visibility = View.VISIBLE
        binding.friendSearchBottomSheetLayout.friendSearchBottomSheet.post {
            friendSearchBottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            binding.friendSearchBottomSheetLayout.edtFriendSearch.requestFocus()
            val controller = androidx.core.view.WindowCompat.getInsetsController(requireActivity().window, binding.friendSearchBottomSheetLayout.edtFriendSearch)
            controller.show(androidx.core.view.WindowInsetsCompat.Type.ime())
        }
    }

    private fun closeFriendSearchSheet() {
        viewModel.closeFriendSearch()
        val controller = androidx.core.view.WindowCompat.getInsetsController(requireActivity().window, binding.friendSearchBottomSheetLayout.edtFriendSearch)
        controller.hide(androidx.core.view.WindowInsetsCompat.Type.ime())
        binding.friendSearchBottomSheetLayout.edtFriendSearch.clearFocus()
        binding.friendSearchScrim.visibility = View.GONE
        friendSearchBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    private fun onSearchFriendClick(friend: FriendLocation) {
        viewModel.recordFriendSearch(friend.id)
        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(friend.latitude, friend.longitude), DEFAULT_ZOOM))
    }

    private fun initializeGoogleMap() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.mapFragment)
            as? SupportMapFragment ?: run {
            Log.e(TAG, "SupportMapFragment was not found in fragment_location")
            return
        }

        try {
            MapsInitializer.initialize(
                requireContext(),
                MapsInitializer.Renderer.LATEST,
            ) {
                if (isAdded && !isRemoving) {
                    mapFragment.getMapAsync(this@LocationFragment)
                }
            }
        } catch (error: Exception) {
            // Fall back to the default renderer on devices with older Play services.
            Log.e(TAG, "Unable to initialize Google Maps renderer", error)
            mapFragment.getMapAsync(this@LocationFragment)
        }
    }

    private fun setupDirectionPanel() = with(binding) {
        directionBottomPanel.btnStartNavigation.setOnClickListener {
            startExternalNavigation()
        }
    }

    private fun setupFriendBottomSheet() = with(binding) {
        bottomSheetBehavior = BottomSheetBehavior.from(friendBottomSheetLayout.friendBottomSheet)
        bottomSheetBehavior.isHideable = true
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        friendAdapter = FriendAdapter(
            showMoreButton = false,
            onItemClick = { _ ->
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                navigationManager.navigateTo(AppDestination.MyFriend)
            }
        )

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
                        mainViewModel.userName,
                        viewModel.isFriendsDataLoaded
                    ) { self, friends, avatar, name, friendsLoaded ->
                        DataPackage(self, friends, avatar, name, friendsLoaded)
                    }.collectLatest { data ->
                        lastDataPackage = data
                        updateMarkersWithAvatars(data.self, data.friends, data.avatar, data.name)
                        tryStartPendingMapRoute()
                        activeRoutePosition?.let { destination ->
                            data.self?.let { origin ->
                                refreshRouteIfNeeded(origin, destination)
                            }
                        }
                        tryAutoZoom()
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
                launch { viewModel.displayedFriends.collectLatest { friendSearchAdapter.submitList(it); binding.friendSearchBottomSheetLayout.tvNoFriends.visibility = if (viewModel.appliedFriendSearchQuery.value.isNotBlank() && it.isEmpty()) View.VISIBLE else View.GONE } }
                launch { viewModel.recentSearchedFriends.collectLatest { history -> friendSearchHistoryAdapter.submitList(history); binding.friendSearchBottomSheetLayout.recentHeader.visibility = if (history.isEmpty()) View.GONE else View.VISIBLE; binding.friendSearchBottomSheetLayout.rvFriendSearchHistory.visibility = if (history.isEmpty()) View.GONE else View.VISIBLE } }

                launch {
                    mainViewModel.mapRouteRequest.collectLatest { request ->
                        if (request != null) {
                            pendingMapRouteRequest = request
                            tryStartPendingMapRoute()
                        }
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
        selfAvatar: String,
        selfName: String
    ) {
        if (googleMap == null) return

        self?.let { latLng ->
            showSelfMarker(latLng, selfAvatar, selfName)
        }

        showFriendMarkers(friends)
    }

    private fun showSelfMarker(location: LatLng, avatarUrl: String, name: String) {
        val map = googleMap ?: return
        val displayName = name.ifBlank { "You" }
        if (selfMarker == null) {
            selfMarker = map.addMarker(
                MarkerOptions()
                    .position(location)
                    .anchor(0.5f, 1f)
                    .flat(false)
                    .zIndex(10f)
            )
            updateMarkerIcon(selfMarker!!, mainViewModel.userAvatarKey.value, avatarUrl, displayName)
        } else {
            selfMarker?.position = location
            if (selfAvatarUrl != avatarUrl || selfName != displayName) {
                updateMarkerIcon(selfMarker!!, mainViewModel.userAvatarKey.value, avatarUrl, displayName)
            }
        }
        selfAvatarUrl = avatarUrl
        selfName = displayName

        if (directionOverlay == null) {
            createDirectionCone(map, location)
        } else {
            directionOverlay?.position = location
        }
        directionOverlay?.isVisible = isCompassEnabled
    }

    private fun showFriendMarkers(friends: List<com.nhn.gps.location.phone.tracker.data.model.FriendLocation>) {
        val map = googleMap ?: return
        val validFriends = friends.filter { isValidRoutePoint(LatLng(it.latitude, it.longitude)) }

        val friendIds = validFriends.map { it.id }.toSet()
        val iterator = friendMarkers.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!friendIds.contains(entry.key)) {
                entry.value.remove()
                iterator.remove()
                friendAvatars.remove(entry.key)
            }
        }

        validFriends.forEach { friend ->
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
                    marker.tag = friend
                    val displayName = friend.name.ifBlank { "Friend" }
                    updateMarkerIcon(marker, friend.avatarKey, friend.avatarUrl, displayName)
                    friendNames[friend.id] = displayName
                }
            } else {
                existingMarker.position = position
                existingMarker.title = friend.name
                existingMarker.tag = friend
                if (activeRouteFriendId == friend.id) {
                    activeRoutePosition = position
                    selectedDestinationMarker?.position = position
                }
                existingMarker.rotation = 0f
                existingMarker.isFlat = false
                val displayName = friend.name.ifBlank { "Friend" }
                if (friendAvatars[friend.id] != friend.avatarUrl || friendNames[friend.id] != displayName) {
                    updateMarkerIcon(existingMarker, friend.avatarKey, friend.avatarUrl, displayName)
                }
                friendNames[friend.id] = displayName
            }
            friendAvatars[friend.id] = friend.avatarUrl
        }
        applyMarkerSelection()
    }

    private fun updateMarkerIcon(marker: Marker, avatarKey: String?, avatarUrl: String?, label: String? = null) {
        MapMarkerHelper.updateMarkerIcon(requireContext(), marker, avatarKey, avatarUrl, style = MapMarkerHelper.MarkerStyle.DEFAULT, label = label)
    }

    private fun applyMarkerSelection() {
        val selectedId = selectedFriendMarkerId
        friendMarkers.forEach { (friendId, marker) ->
            marker.isVisible = friendId != selectedId
        }

        if (selectedId != null) {
            selfMarker?.apply {
                setIcon(BitmapDescriptorFactory.fromResource(R.drawable.ic_my_location))
                setAnchor(0.5f, 0.5f)
                isVisible = true
            }
        } else {
            selfMarker?.let { marker ->
                val self = lastDataPackage
                updateMarkerIcon(
                    marker,
                    mainViewModel.userAvatarKey.value,
                    self?.avatar.orEmpty(),
                    self?.name?.ifBlank { "You" } ?: "You",
                )
                marker.isVisible = true
            }
        }
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

    private fun tryAutoZoom() {
        val data = lastDataPackage ?: return
        if (!hasAutoZoomed && googleMap != null && data.self != null && data.friendsLoaded) {
            centerCameraOnAll()
            hasAutoZoomed = true
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
        lastDataPackage?.let { data ->
            updateMarkersWithAvatars(data.self, data.friends, data.avatar, data.name)
        }
        tryAutoZoom()

        map.setOnMarkerClickListener { marker ->
            (marker.tag as? com.nhn.gps.location.phone.tracker.data.model.FriendLocation)?.let {
                showRouteTo(
                    it.name.ifBlank { "Friend location" },
                    LatLng(it.latitude, it.longitude),
                    friendId = it.id
                )
                true
            } ?: run {
                if (marker == selfMarker && selectedFriendMarkerId != null) {
                    clearRoute()
                    true
                } else {
                    false
                }
            }
        }

        // Long press gives the user a destination even when no friend marker is available.
        map.setOnMapLongClickListener { location ->
            showRouteTo("Selected location", location)
        }
        
        map.setOnCameraIdleListener {
            if (isCompassEnabled) {
                updateDirectionUI(compassManager.bearing.value)
            }

            // Check if all markers are in view, if not, auto-zoom logic could go here
            // but the user only wanted it "once" or on button click.
        }

        tryStartPendingMapRoute()
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
        routeJob?.cancel()
        routeJob = null
        bottomSheetCallback?.let {
            if (::bottomSheetBehavior.isInitialized) {
                bottomSheetBehavior.removeBottomSheetCallback(it)
            }
        }
        bottomSheetCallback = null
        
        // Remove map objects before clearing the map reference
        googleMap?.apply {
            setOnMarkerClickListener(null)
            setOnMapLongClickListener(null)
            setOnCameraIdleListener(null)
            clear()
        }
        
        super.onDestroyView()
        
        googleMap = null
        selfMarker = null
        selectedDestinationMarker = null
        routeOutline = null
        routeLine = null
        directionOverlay = null
        
        activeRouteName = null
        activeRoutePosition = null
        activeRouteFriendId = null
        pendingMapRouteRequest = null
        lastRouteOrigin = null
        lastRouteDestination = null
        lastRouteRequestAt = 0L
        selfName = null
        
        friendMarkers.clear()
        friendAvatars.clear()
        friendNames.clear()
    }

    private fun tryStartPendingMapRoute() {
        val request = pendingMapRouteRequest ?: return
        if (googleMap == null || viewModel.selfLocation.value == null) return
        val started = showRouteTo(
            name = request.destinationName,
            destination = LatLng(request.latitude, request.longitude),
            friendId = request.friendId,
        )
        if (started) {
            pendingMapRouteRequest = null
            mainViewModel.consumeMapRouteRequest(request.requestId)
        }
    }

    private fun showRouteTo(
        name: String,
        destination: LatLng,
        friendId: String? = null,
    ): Boolean {
        val origin = viewModel.selfLocation.value
        if (origin == null || !isValidRoutePoint(origin)) {
            Toast.makeText(
                requireContext(),
                getString(R.string.route_waiting_for_location),
                Toast.LENGTH_SHORT,
            ).show()
            return false
        }

        if (!isValidRoutePoint(destination)) {
            Toast.makeText(
                requireContext(),
                getString(R.string.route_destination_unavailable),
                Toast.LENGTH_LONG,
            ).show()
            Log.w(TAG, "Ignoring route request with invalid destination: $destination")
            return false
        }

        val distanceMeters = SphericalUtil.computeDistanceBetween(origin, destination)
        if (distanceMeters < 1.0) {
            Toast.makeText(
                requireContext(),
                getString(R.string.route_same_location),
                Toast.LENGTH_SHORT,
            ).show()
            return false
        }

        activeRouteName = name
        activeRoutePosition = destination
        activeRouteFriendId = friendId
        selectedFriendMarkerId = friendId
        selectedDestinationMarker?.remove()
        selectedDestinationMarker = googleMap?.addMarker(
            MarkerOptions()
                .position(destination)
                .title(name)
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_marker_red))
                .anchor(0.5f, 1f)
                .zIndex(11f)
        )
        applyMarkerSelection()

        showDirectionLoading()
        requestRoute(origin, destination, fitBounds = true, force = true)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        return true
    }

    private fun refreshRouteIfNeeded(origin: LatLng, destination: LatLng) {
        val previousOrigin = lastRouteOrigin ?: return
        val previousDestination = lastRouteDestination ?: return
        val elapsed = android.os.SystemClock.elapsedRealtime() - lastRouteRequestAt
        if (elapsed < ROUTE_REFRESH_INTERVAL_MS) return

        val originMoved = SphericalUtil.computeDistanceBetween(previousOrigin, origin)
        val destinationMoved = SphericalUtil.computeDistanceBetween(previousDestination, destination)
        if (originMoved >= ROUTE_REFRESH_DISTANCE_METERS ||
            destinationMoved >= ROUTE_REFRESH_DISTANCE_METERS
        ) {
            requestRoute(origin, destination, fitBounds = false, force = false)
        }
    }

    private fun requestRoute(
        origin: LatLng,
        destination: LatLng,
        fitBounds: Boolean,
        force: Boolean,
    ) {
        if (!force && activeRoutePosition == null) return
        lastRouteOrigin = origin
        lastRouteDestination = destination
        lastRouteRequestAt = android.os.SystemClock.elapsedRealtime()
        routeJob?.cancel()
        routeJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val route = routesRepository.computeDrivingRoute(origin, destination)
                val currentDestination = activeRoutePosition ?: return@launch
                if (SphericalUtil.computeDistanceBetween(currentDestination, destination) >
                    ROUTE_RESPONSE_STALE_DISTANCE_METERS
                ) {
                    return@launch
                }
                drawRouteLine(route, fitBounds)
                showDirectionPanels(route.distanceMeters, route.durationSeconds)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Unable to compute driving route", error)
                val currentDestination = activeRoutePosition
                if (currentDestination != null &&
                    SphericalUtil.computeDistanceBetween(currentDestination, destination) <=
                    ROUTE_RESPONSE_STALE_DISTANCE_METERS
                ) {
                    // Routes API can be unavailable while a project is being configured or
                    // when the device is offline. Keep the friend direction usable by drawing
                    // a direct fallback line and opening Google Maps for turn-by-turn navigation.
                    // This is deliberately labelled as an estimate so it is never mistaken for
                    // a road-following route.
                    val fallbackDistance = SphericalUtil
                        .computeDistanceBetween(origin, destination)
                        .toInt()
                        .coerceAtLeast(1)
                    val fallbackDuration = (fallbackDistance / ESTIMATED_DRIVING_METERS_PER_SECOND)
                        .toLong()
                        .coerceAtLeast(60L)
                    drawFallbackRoute(origin, destination, fitBounds)
                    showApproximateDirectionPanels(fallbackDistance, fallbackDuration)
                }
            }
        }
    }

    private fun drawFallbackRoute(origin: LatLng, destination: LatLng, fitBounds: Boolean) {
        drawRouteLine(
            DrivingRoute(
                points = listOf(origin, destination),
                distanceMeters = SphericalUtil.computeDistanceBetween(origin, destination)
                    .toInt()
                    .coerceAtLeast(1),
                durationSeconds = 0L,
            ),
            fitBounds,
        )
    }

    private fun showApproximateDirectionPanels(distanceMeters: Int, durationSeconds: Long) =
        with(binding) {
            val distanceText = formatDistance(distanceMeters)
            val durationText = formatDuration(durationSeconds)
            directionBottomPanel.tvRouteTime.text =
                getString(R.string.route_duration_and_distance, durationText, distanceText)
            directionBottomPanel.tvTraffic.text = getString(R.string.route_approximate_description)
            directionBottomPanel.btnStartNavigation.isEnabled = true
            directionBottomPanel.btnStartNavigation.alpha = 1f
            updateRouteOptionLabels(durationText, distanceText)
        }

    private fun isValidRoutePoint(point: LatLng): Boolean =
        point.latitude.isFinite() &&
            point.longitude.isFinite() &&
            point.latitude in -90.0..90.0 &&
            point.longitude in -180.0..180.0 &&
            !(point.latitude == 0.0 && point.longitude == 0.0)

    private fun drawRouteLine(route: DrivingRoute, fitBounds: Boolean) {
        val map = googleMap ?: return
        routeOutline?.remove()
        routeLine?.remove()

        routeOutline = map.addPolyline(
            PolylineOptions()
                .addAll(route.points)
                .color(Color.argb(70, 0, 104, 96))
                .width(15f)
                .jointType(JointType.ROUND)
                .startCap(RoundCap())
                .endCap(RoundCap())
                .geodesic(false)
                .zIndex(3f),
        )
        routeLine = map.addPolyline(
            PolylineOptions()
                .addAll(route.points)
                .color(ROUTE_END_COLOR)
                .width(10f)
                .jointType(JointType.ROUND)
                .startCap(RoundCap())
                .endCap(RoundCap())
                .geodesic(false)
                .addSpan(
                    StyleSpan(
                        StrokeStyle.gradientBuilder(ROUTE_START_COLOR, ROUTE_END_COLOR).build(),
                    ),
                )
                .zIndex(4f),
        )

        if (fitBounds) {
            val boundsBuilder = LatLngBounds.builder()
            route.points.forEach(boundsBuilder::include)
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 180))
        }
    }

    private fun showDirectionLoading() = with(binding) {
        (activity as? MainActivity)?.showScreenBanner(GpsAdPlacement.BANNER_DIRECTION)
        routeOutline?.remove()
        routeOutline = null
        routeLine?.remove()
        routeLine = null
        directionTopPanel.tvCurrentLocation.text = getString(R.string.route_my_location)
        directionTopPanel.tvDestination.text =
            activeRouteName ?: getString(R.string.route_selected_location)
        directionBottomPanel.tvRouteTime.text = getString(R.string.route_calculating)
        directionBottomPanel.tvTraffic.text = getString(R.string.route_calculating_description)
        directionBottomPanel.btnStartNavigation.isEnabled = false
        directionBottomPanel.btnStartNavigation.alpha = 0.55f
        updateRouteOptionLabels(getString(R.string.route_calculating_short), "")
        directionTopPanel.root.visibility = View.VISIBLE
        directionBottomPanel.root.visibility = View.VISIBLE

        layoutTools.visibility = View.GONE
        cardSearch.visibility = View.GONE
        txtTitle.text = getString(R.string.directions)
    }

    private fun showDirectionPanels(distanceMeters: Int, durationSeconds: Long) = with(binding) {
        val distanceText = formatDistance(distanceMeters)
        val durationText = formatDuration(durationSeconds)
        directionBottomPanel.tvRouteTime.text =
            getString(R.string.route_duration_and_distance, durationText, distanceText)
        directionBottomPanel.tvTraffic.text = getString(R.string.route_fastest_driving)
        directionBottomPanel.btnStartNavigation.isEnabled = true
        directionBottomPanel.btnStartNavigation.alpha = 1f
        updateRouteOptionLabels(durationText, distanceText)
    }

    private fun showRouteError() = with(binding) {
        routeOutline?.remove()
        routeOutline = null
        routeLine?.remove()
        routeLine = null
        directionBottomPanel.tvRouteTime.text = getString(R.string.route_not_found)
        directionBottomPanel.tvTraffic.text = getString(R.string.route_not_found_description)
        directionBottomPanel.btnStartNavigation.isEnabled = true
        directionBottomPanel.btnStartNavigation.alpha = 1f
        updateRouteOptionLabels("--", "")
        Toast.makeText(requireContext(), R.string.route_not_found_description, Toast.LENGTH_LONG)
            .show()
    }

    private fun updateRouteOptionLabels(durationText: String, distanceText: String) {
        val routeOptions = binding.directionTopPanel.layoutRoutes
        for (index in 0 until routeOptions.childCount) {
            routeOptions.getChildAt(index).apply {
                findViewById<TextView>(R.id.tvDuration)?.text = durationText
                findViewById<TextView>(R.id.tvDistance)?.text = distanceText
            }
        }
    }

    private fun formatDistance(distanceMeters: Int): String {
        if (distanceMeters < 1_000) {
            return getString(R.string.route_distance_meters, distanceMeters)
        }
        val distanceKm = distanceMeters / 1_000.0
        return if (distanceKm < 10.0) {
            getString(R.string.route_distance_kilometers_decimal, distanceKm)
        } else {
            getString(R.string.route_distance_kilometers, kotlin.math.round(distanceKm).toInt())
        }
    }

    private fun formatDuration(durationSeconds: Long): String {
        val minutes = kotlin.math.max(1, kotlin.math.ceil(durationSeconds / 60.0).toInt())
        return if (minutes < 60) {
            getString(R.string.route_minutes, minutes)
        } else {
            val hours = minutes / 60
            val remainingMinutes = minutes % 60
            if (remainingMinutes == 0) {
                getString(R.string.route_hours, hours)
            } else {
                getString(R.string.route_hours_minutes, hours, remainingMinutes)
            }
        }
    }

    private fun clearRoute() = with(binding) {
        routeJob?.cancel()
        routeJob = null
        (activity as? MainActivity)?.showScreenBanner(GpsAdPlacement.BANNER_REALTIME_TRACKER)
        routeOutline?.remove()
        routeOutline = null
        routeLine?.remove()
        routeLine = null
        selectedDestinationMarker?.remove()
        selectedDestinationMarker = null
        activeRouteName = null
        activeRoutePosition = null
        activeRouteFriendId = null
        selectedFriendMarkerId = null
        applyMarkerSelection()
        lastRouteOrigin = null
        lastRouteDestination = null
        lastRouteRequestAt = 0L
        directionTopPanel.root.visibility = View.GONE
        directionBottomPanel.root.visibility = View.GONE
        layoutTools.visibility = View.VISIBLE
        cardSearch.visibility = View.VISIBLE
        txtTitle.text = getString(R.string.realtime_tracker)
    }

    private fun startExternalNavigation() {
        val destination = activeRoutePosition ?: return
        ResumeAdGuard.suppressNextResumeAd()
        val navigationUri = Uri.parse(
            "google.navigation:q=${destination.latitude},${destination.longitude}&mode=d"
        )
        val googleMapsIntent = Intent(Intent.ACTION_VIEW, navigationUri).apply {
            setPackage("com.google.android.apps.maps")
        }
        try {
            startActivity(googleMapsIntent)
        } catch (_: Exception) {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("geo:${destination.latitude},${destination.longitude}")
                )
            )
        }
    }

    companion object {
        private const val TAG = "LocationFragment"
        private const val ROUTE_REFRESH_INTERVAL_MS = 8_000L
        private const val ROUTE_REFRESH_DISTANCE_METERS = 40.0
        private const val ROUTE_RESPONSE_STALE_DISTANCE_METERS = 50.0
        private const val ESTIMATED_DRIVING_METERS_PER_SECOND = 8.33
        private val ROUTE_START_COLOR = Color.rgb(62, 218, 105)
        private val ROUTE_END_COLOR = Color.rgb(65, 218, 221)

        fun newInstance() = LocationFragment()

        private data class DataPackage(
            val self: LatLng?,
            val friends: List<com.nhn.gps.location.phone.tracker.data.model.FriendLocation>,
            val avatar: String,
            val name: String,
            val friendsLoaded: Boolean
        )
    }
}
