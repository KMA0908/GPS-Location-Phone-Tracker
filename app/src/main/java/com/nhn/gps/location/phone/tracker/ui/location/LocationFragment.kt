package com.nhn.gps.location.phone.tracker.ui.location

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
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
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.card.MaterialCardView
import com.google.maps.android.PolyUtil
import com.google.maps.android.SphericalUtil
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.ads.GpsAdPlacement
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.data.local.AppPreferences
import com.nhn.gps.location.phone.tracker.data.model.FriendLocation
import com.nhn.gps.location.phone.tracker.data.repository.GoogleRoute
import com.nhn.gps.location.phone.tracker.data.repository.GoogleRoutesInvalidResponseException
import com.nhn.gps.location.phone.tracker.data.repository.GoogleRoutesNoRouteException
import com.nhn.gps.location.phone.tracker.data.repository.GoogleRoutesRepository
import com.nhn.gps.location.phone.tracker.data.repository.GoogleRoutesException
import com.nhn.gps.location.phone.tracker.databinding.FragmentLocationBinding
import com.nhn.gps.location.phone.tracker.navigation.AppDestination
import com.nhn.gps.location.phone.tracker.ui.friend.FriendAdapter
import com.nhn.gps.location.phone.tracker.ui.main.MainActivity
import com.nhn.gps.location.phone.tracker.ui.main.MainViewModel
import com.nhn.gps.location.phone.tracker.ui.permission.LocationPermissionBottomSheet
import com.nhn.gps.location.phone.tracker.util.MapMarkerHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import javax.inject.Inject
import kotlin.math.pow

@AndroidEntryPoint
class LocationFragment : BaseFragment<FragmentLocationBinding, LocationViewModel>(),
    OnMapReadyCallback {

    override val viewModel: LocationViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by viewModels({ requireActivity() })

    @Inject
    lateinit var compassManager: CompassManager

    @Inject
    lateinit var routesRepository: GoogleRoutesRepository

    @Inject
    lateinit var twoWheelerCoverageResolver: TwoWheelerCoverageResolver

    @Inject
    lateinit var appPreferences: AppPreferences

    private var googleMap: GoogleMap? = null

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
    private var searchBottomSheetCallback: BottomSheetBehavior.BottomSheetCallback? = null

    private enum class PendingSheet { NONE, FRIEND, SEARCH }
    private var pendingSheet = PendingSheet.NONE

    private var isCompassEnabled = false
    private var hasAutoZoomed = false
    private var lastDataPackage: DataPackage? = null
    private var pendingDestination: AppDestination? = null
    private var activeRouteName: String? = null
    private var activeRoutePosition: LatLng? = null
    private var activeRouteFriendId: String? = null
    private var hideUnavailableModesForActiveRoute = false
    private var selectedFriendMarkerId: String? = null
    private var pendingMapRouteRequest: MapRouteRequest? = null
    private val routeJobs = mutableMapOf<DirectionTravelMode, Job>()
    private var twoWheelerCoverageJob: Job? = null
    private val routeModeStates = PerModeResultStore<DirectionTravelMode, RouteModeState>()
    private val routeRequestGeneration = RouteRequestGeneration()
    private var lastRouteOrigin: LatLng? = null
    private var lastRouteDestination: LatLng? = null
    private var lastRouteRequestAt = 0L
    private var travelModeViewsConfigured = false
    private var isInAppNavigationActive = false
    private var navigationEnabledCompass = false
    private var navigationArrivalAnnounced = false
    private var lastNavigationCameraUpdateAt = 0L
    private var navigationCameraZoom: Float? = null
    private var navigationCameraGestureInProgress = false
    private var lastDisplayedNavigationSelfLocation: LatLng? = null
    private val lastDisplayedNavigationFriendLocations = mutableMapOf<String, LatLng>()
    private var isTwoWheelerOptionVisible = true
    private var consecutiveOffRouteUpdates = 0
    private var mapDisplayOptions = MapDisplayOptions()

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentLocationBinding = FragmentLocationBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) = with(binding) {
        setupMapTypeResultListener()
        initializeGoogleMap()

        cardBack.setOnClickListener {
            if (isInAppNavigationActive) {
                stopInAppNavigation()
            } else if (activeRoutePosition != null) {
                clearRoute()
            } else {
                handleToolbarBack()
            }
        }

        itemLocation.root.setOnClickListener {
            centerCameraOnSelf()
        }

        itemLayerStack.root.setOnClickListener {
            showMapTypeBottomSheet()
        }

        itemCompass.root.setOnClickListener {
            toggleCompass()
        }

        cardImgFriend.setOnClickListener {
            if (::bottomSheetBehavior.isInitialized) {
                val behavior = bottomSheetBehavior
                if (behavior.state == BottomSheetBehavior.STATE_EXPANDED ||
                    behavior.state == BottomSheetBehavior.STATE_HALF_EXPANDED
                ) {
                    behavior.state = BottomSheetBehavior.STATE_HIDDEN
                } else {
                    if (::friendSearchBottomSheetBehavior.isInitialized &&
                        friendSearchBottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN
                    ) {
                        pendingSheet = PendingSheet.FRIEND
                        friendSearchBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                    } else {
                        behavior.state = BottomSheetBehavior.STATE_EXPANDED
                    }
                }
            }
        }

        search.setOnClickListener {
            if (::friendSearchBottomSheetBehavior.isInitialized) {
                val behavior = friendSearchBottomSheetBehavior
                if (behavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                    if (::bottomSheetBehavior.isInitialized &&
                        bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN
                    ) {
                        pendingSheet = PendingSheet.SEARCH
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                    } else {
                        openFriendSearchSheet()
                    }
                }
            }
        }
        renderBottomActionState(isFriendSheetVisible = false, isSearchActive = false)

        setupFriendBottomSheet()
        setupFriendSearchBottomSheet()
        setupDirectionPanel()
    }

    private fun setupFriendSearchBottomSheet() = withBinding {
        friendSearchBottomSheetBehavior =
            BottomSheetBehavior.from(friendSearchBottomSheetLayout.friendSearchBottomSheet).apply {
                isHideable = true
                skipCollapsed = true
                isFitToContents = false
                expandedOffset = 80
                state = BottomSheetBehavior.STATE_HIDDEN
            }
        searchBottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                renderBottomActionState(
                    isFriendSheetVisible = false,
                    isSearchActive = newState != BottomSheetBehavior.STATE_HIDDEN
                )
                if (newState == BottomSheetBehavior.STATE_COLLAPSED || newState == BottomSheetBehavior.STATE_HIDDEN) {
                    hideKeyboardAndClearSearchFocus()
                }
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED,
                    BottomSheetBehavior.STATE_HALF_EXPANDED,
                    BottomSheetBehavior.STATE_DRAGGING,
                    BottomSheetBehavior.STATE_SETTLING -> {
                        updateCardSearchPosition(bottomSheet)
                    }

                    BottomSheetBehavior.STATE_HIDDEN,
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        updateCardSearchPosition(activeVisibleSheet())
                        if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                            binding.friendSearchScrim.visibility = View.GONE
                            viewModel.closeFriendSearch()
                            if (pendingSheet == PendingSheet.FRIEND) {
                                pendingSheet = PendingSheet.NONE
                                if (::bottomSheetBehavior.isInitialized) {
                                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                                }
                            }
                        }
                    }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                updateCardSearchPosition(bottomSheet)
            }
        }
        friendSearchBottomSheetBehavior.addBottomSheetCallback(searchBottomSheetCallback!!)
        friendSearchAdapter =
            FriendAdapter(showMoreButton = false, onItemClick = ::onSearchFriendClick)
        friendSearchHistoryAdapter = FriendSearchHistoryAdapter(::onSearchFriendClick) {
            viewModel.removeFriendSearchHistory(it.id)
        }
        friendSearchBottomSheetLayout.rvSearchFriends.layoutManager =
            LinearLayoutManager(requireContext())
        friendSearchBottomSheetLayout.rvSearchFriends.adapter = friendSearchAdapter
        friendSearchBottomSheetLayout.rvSearchHistory.layoutManager =
            LinearLayoutManager(requireContext())
        friendSearchBottomSheetLayout.rvSearchHistory.adapter = friendSearchHistoryAdapter
        friendSearchBottomSheetLayout.edtFriendSearch.doAfterTextChanged {
            viewModel.updateFriendSearchInput(
                it?.toString().orEmpty()
            )
            friendSearchBottomSheetLayout.btnClearFriendSearch.isVisible = !it.isNullOrEmpty()
        }
        friendSearchBottomSheetLayout.edtFriendSearch.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER) {
                viewModel.submitFriendSearch(); true
            } else false
        }
        friendSearchBottomSheetLayout.btnClearFriendSearch.setOnClickListener { viewModel.clearFriendSearch() }
        friendSearchBottomSheetLayout.btnClearAllHistory.setOnClickListener { viewModel.clearFriendSearchHistory() }
        friendSearchScrim.setOnClickListener { closeFriendSearchSheet() }
    }

    private fun openFriendSearchSheet() {
        viewModel.openFriendSearch()
        binding.friendSearchScrim.visibility = View.VISIBLE
        binding.friendSearchBottomSheetLayout.friendSearchBottomSheet.post {
            friendSearchBottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            binding.cardSearch.bringToFront()
            updateCardSearchPosition(binding.friendSearchBottomSheetLayout.friendSearchBottomSheet)
            binding.friendSearchBottomSheetLayout.edtFriendSearch.requestFocus()
            val controller = androidx.core.view.WindowCompat.getInsetsController(
                requireActivity().window,
                binding.friendSearchBottomSheetLayout.edtFriendSearch
            )
            controller.show(androidx.core.view.WindowInsetsCompat.Type.ime())
        }
    }

    private fun closeFriendSearchSheet() {
        if (!::friendSearchBottomSheetBehavior.isInitialized) return
        hideKeyboardAndClearSearchFocus()
        viewModel.closeFriendSearch()
        friendSearchBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    private fun hideKeyboardAndClearSearchFocus() {
        if (view == null) return
        binding.friendSearchBottomSheetLayout.edtFriendSearch.clearFocus()
        val controller = androidx.core.view.WindowCompat.getInsetsController(
            requireActivity().window,
            binding.friendSearchBottomSheetLayout.edtFriendSearch
        )
        controller.hide(androidx.core.view.WindowInsetsCompat.Type.ime())
    }

    private fun renderBottomActionState(
        isFriendSheetVisible: Boolean,
        isSearchActive: Boolean
    ) = withBinding {
        val selectedColor = resources.getColor(R.color.bg_botton_friend, null)
        val defaultContentColor = resources.getColor(R.color.text_primary, null)
        val selectedContentColor = Color.WHITE

        cardImgFriend.setCardBackgroundColor(
            if (isFriendSheetVisible) selectedColor else Color.TRANSPARENT
        )
        imgFriend.imageTintList = ColorStateList.valueOf(
            if (isFriendSheetVisible) selectedContentColor else defaultContentColor
        )

        val searchBgColor = if (isSearchActive) selectedColor else Color.TRANSPARENT
        val searchContentColor = if (isSearchActive) selectedContentColor else defaultContentColor

        search.setCardBackgroundColor(searchBgColor)
        searchLabel.setTextColor(searchContentColor)
        imgChat.imageTintList = ColorStateList.valueOf(searchContentColor)
    }

    private fun activeVisibleSheet(): View? = when {
        ::friendSearchBottomSheetBehavior.isInitialized && friendSearchBottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN -> binding.friendSearchBottomSheetLayout.friendSearchBottomSheet
        ::bottomSheetBehavior.isInitialized && bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN -> binding.friendBottomSheetLayout.friendBottomSheet
        else -> null
    }

    private fun updateCardSearchPosition(activeSheet: View?) {
        if (!isAdded || view == null) return
        val card = binding.cardSearch
        if (binding.directionTopPanel.root.isVisible || binding.directionBottomPanel.root.isVisible) {
            card.visibility = View.GONE
            return
        }
        card.visibility = View.VISIBLE
        if (activeSheet == null || activeSheet.visibility != View.VISIBLE) {
            card.translationY = 0f
        } else {
            val spacing = resources.getDimension(R.dimen.d_12)
            val target = activeSheet.top.toFloat() - spacing - card.bottom.toFloat()
            card.translationY = target.coerceAtMost(0f)
        }
    }

    private fun onSearchFriendClick(friend: FriendLocation) {
        viewModel.recordFriendSearch(friend.id)
        googleMap?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(
                    friend.latitude,
                    friend.longitude
                ), DEFAULT_ZOOM
            )
        )
    }

    private fun initializeGoogleMap() {
        Log.d(TAG, "initializeGoogleMap: Searching for map fragment")
        val mapFragment = childFragmentManager.findFragmentById(R.id.mapFragment)
                as? SupportMapFragment ?: run {
            Log.e(TAG, "SupportMapFragment was not found in fragment_location")
            return
        }

        try {
            Log.d(TAG, "initializeGoogleMap: Initializing Maps SDK")
            MapsInitializer.initialize(
                requireContext(),
                MapsInitializer.Renderer.LATEST,
            ) { renderer ->
                Log.d(TAG, "initializeGoogleMap: Maps SDK initialized with renderer: $renderer")
                if (isAdded && !isRemoving) {
                    Log.d(TAG, "initializeGoogleMap: Requesting map async")
                    mapFragment.getMapAsync(this@LocationFragment)
                }
            }
        } catch (error: Exception) {
            // Fall back to the default renderer on devices with older Play services.
            Log.e(TAG, "Unable to initialize Google Maps renderer", error)
            Log.d(TAG, "initializeGoogleMap: Requesting map async (fallback)")
            mapFragment.getMapAsync(this@LocationFragment)
        }
    }

    private fun setupDirectionPanel() = with(binding) {
        directionBottomPanel.btnStartNavigation.setOnClickListener {
            toggleInAppNavigation()
        }
        configureTravelModeViews()
    }

    private fun configureTravelModeViews() {
        if (travelModeViewsConfigured) return
        val options = binding.directionTopPanel.layoutRoutes
        if (options.childCount < 3) return
        val modes = listOf(
            DirectionTravelMode.CAR to R.drawable.ic_car,
            DirectionTravelMode.MOTORCYCLE to R.drawable.ic_motorcycle,
            DirectionTravelMode.WALKING to R.drawable.ic_walking,
        )
        modes.forEachIndexed { index, (mode, icon) ->
            val item = options.getChildAt(index)
            item.findViewById<android.widget.ImageView>(R.id.ivMode)?.setImageResource(icon)
            item.setOnClickListener {
                Log.d(TAG, "route_mode_selected mode=${mode.name}")
                viewModel.selectTravelMode(mode)
                selectOrRequestRouteMode(mode)
            }
        }
        travelModeViewsConfigured = true
        renderSelectedTravelMode(viewModel.selectedTravelMode.value)
    }

    private fun renderSelectedTravelMode(selectedMode: DirectionTravelMode) {
        val options = binding.directionTopPanel.layoutRoutes
        val selectedColor = resources.getColor(R.color.color_e8f5e9, null)
        val defaultColor = resources.getColor(R.color.white, null)
        val modes = DirectionTravelMode.entries
        for (index in 0 until minOf(options.childCount, modes.size)) {
            val item = options.getChildAt(index) as? MaterialCardView ?: continue
            item.setCardBackgroundColor(
                if (modes[index] == selectedMode) selectedColor else defaultColor
            )
        }
    }

    private fun setupFriendBottomSheet() = with(binding) {
        bottomSheetBehavior = BottomSheetBehavior.from(friendBottomSheetLayout.friendBottomSheet).apply {
            isHideable = true
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_HIDDEN
        }

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
                    renderBottomActionState(
                        isFriendSheetVisible = newState != BottomSheetBehavior.STATE_HIDDEN,
                        isSearchActive = false
                    )
                    when (newState) {
                        BottomSheetBehavior.STATE_EXPANDED,
                        BottomSheetBehavior.STATE_HALF_EXPANDED,
                        BottomSheetBehavior.STATE_DRAGGING,
                        BottomSheetBehavior.STATE_SETTLING -> {
                            updateCardSearchPosition(bottomSheet)
                        }

                        BottomSheetBehavior.STATE_HIDDEN,
                        BottomSheetBehavior.STATE_COLLAPSED -> {
                            updateCardSearchPosition(activeVisibleSheet())
                            if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                                if (pendingSheet == PendingSheet.SEARCH) {
                                    pendingSheet = PendingSheet.NONE
                                    openFriendSearchSheet()
                                }
                                pendingDestination?.let {
                                    navigationManager.navigateTo(it)
                                    pendingDestination = null
                                }
                            }
                        }
                    }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                updateCardSearchPosition(bottomSheet)
            }
        }
        bottomSheetBehavior.addBottomSheetCallback(bottomSheetCallback!!)
        updateCardSearchPosition(null)
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
                                if (isInAppNavigationActive) {
                                    updateInAppNavigation(origin, destination)
                                } else {
                                    refreshRouteIfNeeded(origin, destination)
                                }
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
                            if (isInAppNavigationActive) {
                                viewModel.selfLocation.value?.let { location ->
                                    followNavigationCamera(location, bearing)
                                }
                            }
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
                launch {
                    viewModel.selectedTravelMode.collectLatest {
                        renderSelectedTravelMode(it)
                        renderSelectedRouteMode(it)
                    }
                }
                launch {
                    combine(
                        appPreferences.mapTypeFlow,
                        appPreferences.mapTrafficEnabledFlow,
                        appPreferences.mapBuildings3dEnabledFlow,
                    ) { mapType, trafficEnabled, buildings3dEnabled ->
                        MapDisplayOptions(
                            baseType = MapBaseType.fromPersistedValue(mapType),
                            trafficEnabled = trafficEnabled,
                            buildings3dEnabled = buildings3dEnabled,
                        )
                    }.collectLatest { options ->
                        mapDisplayOptions = options
                        applyMapDisplayOptions()
                    }
                }
        launch {
            combine(
                viewModel.displayedFriends,
                viewModel.appliedFriendSearchQuery
            ) { friends, query ->
                friends to query
            }.collectLatest { (friends, query) ->
                friendSearchAdapter.submitList(friends)
                val isQueryActive = query.isNotBlank()
                binding.friendSearchBottomSheetLayout.layoutNoFriendSearchResults.visibility =
                    if (isQueryActive && friends.isEmpty()) View.VISIBLE else View.GONE
                binding.friendSearchBottomSheetLayout.friendsHeader.visibility =
                    if (isQueryActive) View.GONE else View.VISIBLE
            }
        }
        launch {
            combine(
                viewModel.recentSearchedFriends,
                viewModel.friendSearchInput,
                viewModel.appliedFriendSearchQuery
            ) { history, input, query ->
                Triple(history, input, query)
            }.collectLatest { (history, input, query) ->
                friendSearchHistoryAdapter.submitList(history)
                renderSearchHistoryVisibility(history, input, query)
            }
        }

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

    private fun updateBottomSheetUi(friends: List<FriendLocation>) =
        with(binding.friendBottomSheetLayout) {
            tvFriendCount.text = getString(R.string.friends_count, friends.size)
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
            if (activeRoutePosition == null) {
                groupMapUI.visibility = View.VISIBLE
            } else {
                cardBack.visibility = View.VISIBLE
                txtTitle.visibility = View.VISIBLE
                layoutTools.visibility = View.GONE
                cardSearch.visibility = View.GONE
            }
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

    private fun shouldAcceptMovement(
        previous: LatLng?,
        current: LatLng,
        thresholdMeters: Double = 100.0
    ): Boolean {
        if (!isValidRoutePoint(current)) return false
        if (previous == null) return true
        val distance = SphericalUtil.computeDistanceBetween(previous, current)
        return distance >= thresholdMeters
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
            updateMarkerIcon(
                selfMarker!!,
                mainViewModel.userAvatarKey.value,
                avatarUrl,
                displayName
            )
        } else {
            selfMarker?.position = location
            if (selfAvatarUrl != avatarUrl || selfName != displayName) {
                updateMarkerIcon(
                    selfMarker!!,
                    mainViewModel.userAvatarKey.value,
                    avatarUrl,
                    displayName
                )
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

    private fun renderSearchHistoryVisibility(
        history: List<FriendLocation>,
        input: String,
        query: String
    ) {
        val visible = history.isNotEmpty() && input.isBlank() && query.isBlank()
        binding.friendSearchBottomSheetLayout.historyContainer.visibility =
            if (visible) View.VISIBLE else View.GONE
    }

    private fun updateMarkerIcon(
        marker: Marker,
        avatarKey: String?,
        avatarUrl: String?,
        label: String? = null
    ) {
        MapMarkerHelper.updateMarkerIcon(
            requireContext(),
            marker,
            avatarKey,
            avatarUrl,
            style = MapMarkerHelper.MarkerStyle.DEFAULT,
            label = label
        )
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
        } catch (_: Exception) {
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

    private fun setupMapTypeResultListener() {
        childFragmentManager.setFragmentResultListener(
            MapTypeBottomSheet.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, result ->
            val options = MapDisplayOptions(
                baseType = MapBaseType.fromPersistedValue(
                    result.getInt(
                        MapTypeBottomSheet.KEY_MAP_TYPE,
                        MapBaseType.NORMAL.persistedValue,
                    ),
                ),
                trafficEnabled = result.getBoolean(MapTypeBottomSheet.KEY_TRAFFIC_ENABLED),
                buildings3dEnabled = result.getBoolean(
                    MapTypeBottomSheet.KEY_BUILDINGS_3D_ENABLED,
                ),
            )
            mapDisplayOptions = options
            applyMapDisplayOptions()
            viewLifecycleOwner.lifecycleScope.launch {
                appPreferences.saveMapDisplayOptions(
                    mapType = options.baseType.persistedValue,
                    trafficEnabled = options.trafficEnabled,
                    buildings3dEnabled = options.buildings3dEnabled,
                )
            }
        }
    }

    private fun showMapTypeBottomSheet() {
        if (childFragmentManager.findFragmentByTag(MapTypeBottomSheet.TAG) != null) return
        MapTypeBottomSheet.newInstance(mapDisplayOptions).show(
            childFragmentManager,
            MapTypeBottomSheet.TAG,
        )
    }

    private fun applyMapDisplayOptions() {
        googleMap?.apply {
            mapType = mapDisplayOptions.baseType.googleMapType
            isTrafficEnabled = mapDisplayOptions.trafficEnabled
            isBuildingsEnabled = mapDisplayOptions.buildings3dEnabled
        }
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
            binding.itemCompass.root.setCardBackgroundColor(Color.WHITE)
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
        val height = (BASE_CONE_HEIGHT * 1.5.pow((zoom - 15).toDouble()))
            .coerceIn(20.0, 300.0).toFloat()
        val width = height * 0.6f
        directionOverlay?.setDimensions(width, height)
    }

    @SuppressLint("PotentialBehaviorOverride")
    override fun onMapReady(map: GoogleMap) {
        Log.d(TAG, "onMapReady: Map is ready")
        googleMap = map
        applyMapDisplayOptions()
        lastDataPackage?.let { data ->
            updateMarkersWithAvatars(data.self, data.friends, data.avatar, data.name)
        }
        tryAutoZoom()

        map.setOnMarkerClickListener { marker ->
            (marker.tag as? FriendLocation)?.let {
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

        map.setOnCameraMoveStartedListener { reason ->
            if (isInAppNavigationActive &&
                reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE
            ) {
                navigationCameraGestureInProgress = true
            }
        }

        map.setOnCameraIdleListener {
            if (isInAppNavigationActive && navigationCameraGestureInProgress) {
                navigationCameraZoom = map.cameraPosition.zoom
                navigationCameraGestureInProgress = false
                Log.d(TAG, "navigation_camera_zoom_selected zoom=${map.cameraPosition.zoom}")
            }
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

    @SuppressLint("PotentialBehaviorOverride")
    override fun onDestroyView() {
        searchBottomSheetCallback?.let {
            if (::friendSearchBottomSheetBehavior.isInitialized) {
                friendSearchBottomSheetBehavior.removeBottomSheetCallback(it)
            }
        }
        searchBottomSheetCallback = null
        routeJobs.values.forEach(Job::cancel)
        routeJobs.clear()
        twoWheelerCoverageJob?.cancel()
        twoWheelerCoverageJob = null
        routeModeStates.clear()
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
            setOnCameraMoveStartedListener(null)
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
        isInAppNavigationActive = false
        navigationEnabledCompass = false
        navigationArrivalAnnounced = false
        lastNavigationCameraUpdateAt = 0L
        navigationCameraZoom = null
        navigationCameraGestureInProgress = false
        isTwoWheelerOptionVisible = true
        consecutiveOffRouteUpdates = 0
        selfName = null

        friendMarkers.clear()
        friendAvatars.clear()
        friendNames.clear()
    }

    private fun tryStartPendingMapRoute() {
        val request = pendingMapRouteRequest ?: return
        Log.d(TAG, "map_route_request_received source=${if (request.friendId != null) "friend" else "famous_place"} requestId=${request.requestId}")
        if (googleMap == null) {
            Log.d(TAG, "map_route_waiting_for_map")
            return
        }
        if (viewModel.selfLocation.value == null) {
            Log.d(TAG, "map_route_waiting_for_origin")
            return
        }
        val started = showRouteTo(
            name = request.destinationName,
            destination = LatLng(request.latitude, request.longitude),
            friendId = request.friendId,
            hideUnavailableModes = request.source == "famous_place",
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
        hideUnavailableModes: Boolean = false,
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
        Log.d(TAG, "map_route_destination_validated lat=${destination.latitude} lng=${destination.longitude}")

        val distanceMeters = SphericalUtil.computeDistanceBetween(origin, destination)
        if (distanceMeters < 1.0) {
            Toast.makeText(
                requireContext(),
                getString(R.string.route_same_location),
                Toast.LENGTH_SHORT,
            ).show()
            return false
        }

        if (isInAppNavigationActive) {
            stopInAppNavigation(restoreRouteOverview = false)
        }
        activeRouteName = name
        activeRoutePosition = destination
        activeRouteFriendId = friendId
        hideUnavailableModesForActiveRoute = hideUnavailableModes
        viewModel.selectTravelMode(DirectionTravelMode.CAR)
        routeJobs.values.forEach(Job::cancel)
        routeJobs.clear()
        twoWheelerCoverageJob?.cancel()
        twoWheelerCoverageJob = null
        routeModeStates.clear()
        setTwoWheelerOptionVisible(false)
        consecutiveOffRouteUpdates = 0
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
        requestAllRouteModes(origin, destination, fitBounds = true)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        return true
    }

    private fun refreshRouteIfNeeded(origin: LatLng, destination: LatLng) {
        val previousOrigin = lastRouteOrigin ?: return
        val previousDestination = lastRouteDestination ?: return
        
        // Use consistent accepted locations if in navigation
        val acceptedOrigin = if (isInAppNavigationActive) lastDisplayedNavigationSelfLocation ?: origin else origin
        val acceptedDestination = if (isInAppNavigationActive) {
            activeRouteFriendId?.let { lastDisplayedNavigationFriendLocations[it] } ?: destination
        } else {
            destination
        }

        val originMoved = SphericalUtil.computeDistanceBetween(previousOrigin, acceptedOrigin)
        val destinationMoved = SphericalUtil.computeDistanceBetween(previousDestination, acceptedDestination)
        
        val selectedState = routeModeStates[viewModel.selectedTravelMode.value]
        val isOffRouteNow = isInAppNavigationActive &&
            selectedState is RouteModeState.Success &&
            !PolyUtil.isLocationOnPath(
                origin,
                selectedState.route.points,
                false,
                NAVIGATION_OFF_ROUTE_TOLERANCE_METERS,
            )
        consecutiveOffRouteUpdates = if (isOffRouteNow) {
            consecutiveOffRouteUpdates + 1
        } else {
            0
        }
        
        val confirmedOffRoute = consecutiveOffRouteUpdates >= NAVIGATION_OFF_ROUTE_CONFIRMATION_UPDATES
        
        val elapsed = android.os.SystemClock.elapsedRealtime() - lastRouteRequestAt
        val refreshInterval = if (isInAppNavigationActive) {
            NAVIGATION_ROUTE_REFRESH_INTERVAL_MS
        } else {
            ROUTE_REFRESH_INTERVAL_MS
        }
        if (elapsed < refreshInterval) return

        val shouldRefresh = if (isInAppNavigationActive) {
            confirmedOffRoute ||
                originMoved >= ROUTE_ENDPOINT_UPDATE_THRESHOLD_METERS ||
                destinationMoved >= ROUTE_ENDPOINT_UPDATE_THRESHOLD_METERS
        } else {
            originMoved >= ROUTE_REFRESH_DISTANCE_METERS ||
                destinationMoved >= ROUTE_DESTINATION_REFRESH_DISTANCE_METERS
        }

        if (shouldRefresh) {
            Log.d(TAG, "route_refresh_scheduled mode=${viewModel.selectedTravelMode.value} originChanged=${originMoved >= 100} destinationChanged=${destinationMoved >= 100} offRoute=$confirmedOffRoute")
            consecutiveOffRouteUpdates = 0
            val requestGeneration = routeRequestGeneration.next()
            val selectedMode = viewModel.selectedTravelMode.value
            
            // Invalidate other modes as they are now stale
            DirectionTravelMode.entries.forEach { mode ->
                if (mode != selectedMode) {
                    routeModeStates[mode] = RouteModeState.Idle
                    renderRouteOptionLabel(mode)
                }
            }

            requestRouteMode(
                acceptedOrigin,
                acceptedDestination,
                selectedMode,
                fitBounds = false,
                requestGeneration = requestGeneration,
            )
        }
    }

    private fun requestAllRouteModes(
        origin: LatLng,
        destination: LatLng,
        fitBounds: Boolean,
    ) {
        val requestGeneration = routeRequestGeneration.next()
        val selectedMode = viewModel.selectedTravelMode.value
        requestRouteMode(
            origin = origin,
            destination = destination,
            mode = selectedMode,
            fitBounds = fitBounds,
            requestGeneration = requestGeneration,
        )
        twoWheelerCoverageJob?.cancel()
        twoWheelerCoverageJob = viewLifecycleOwner.lifecycleScope.launch {
            val coverageDeferred = async {
                twoWheelerCoverageResolver.resolve(origin, destination)
            }
            withTimeoutOrNull(SELECTED_ROUTE_HEAD_START_MS) {
                routeJobs[selectedMode]?.join()
            }
            val currentDestination = activeRoutePosition ?: return@launch
            if (!routeRequestGeneration.isCurrent(requestGeneration) ||
                SphericalUtil.computeDistanceBetween(currentDestination, destination) >
                ROUTE_RESPONSE_STALE_DISTANCE_METERS
            ) {
                Log.d(TAG, "two_wheeler_coverage_ignored_stale")
                return@launch
            }

            DirectionTravelMode.entries
                .filter { it != selectedMode && it != DirectionTravelMode.MOTORCYCLE }
                .forEach { mode ->
                    requestRouteMode(
                        origin = origin,
                        destination = destination,
                        mode = mode,
                        fitBounds = false,
                        requestGeneration = requestGeneration,
                    )
                }

            val coverage = coverageDeferred.await()
            val shouldShow = coverage.isSupported != false
            Log.d(
                TAG,
                "two_wheeler_coverage origin=${coverage.originCountryCode ?: "unknown"} " +
                    "destination=${coverage.destinationCountryCode ?: "unknown"} " +
                    "supported=${coverage.isSupported ?: "unknown"}",
            )
            setTwoWheelerOptionVisible(shouldShow)
            when {
                !shouldShow -> {
                    routeModeStates[DirectionTravelMode.MOTORCYCLE] = RouteModeState.Idle
                    renderRouteOptionLabel(DirectionTravelMode.MOTORCYCLE)
                }

                selectedMode != DirectionTravelMode.MOTORCYCLE -> {
                    requestRouteMode(
                        origin = origin,
                        destination = destination,
                        mode = DirectionTravelMode.MOTORCYCLE,
                        fitBounds = false,
                        requestGeneration = requestGeneration,
                    )
                }
            }
        }
    }

    private fun requestRouteMode(
        origin: LatLng,
        destination: LatLng,
        mode: DirectionTravelMode,
        fitBounds: Boolean,
        requestGeneration: Long = routeRequestGeneration.current(),
    ) {
        if (activeRoutePosition == null) return
        if (mode == DirectionTravelMode.MOTORCYCLE && !isTwoWheelerOptionVisible) return
        lastRouteOrigin = origin
        lastRouteDestination = destination
        lastRouteRequestAt = android.os.SystemClock.elapsedRealtime()
        routeJobs.remove(mode)?.cancel()
        routeModeStates[mode] = RouteModeState.Loading
        renderRouteOptionLabel(mode)
        if (viewModel.selectedTravelMode.value == mode) renderSelectedRouteMode(mode)
        routeJobs[mode] = viewLifecycleOwner.lifecycleScope.launch {
            try {
                Log.d(TAG, "route_request_started mode=${mode.name} generation=$requestGeneration")
                val route = routesRepository.computeRoute(origin, destination, mode.routeApiMode)
                if (hideUnavailableModesForActiveRoute &&
                    (route.distanceMeters <= 0 || route.durationSeconds <= 0L || route.points.size < 2)
                ) {
                    throw GoogleRoutesInvalidResponseException(
                        "Famous-place route did not contain usable distance, duration, and geometry",
                    )
                }

                val currentDestination = activeRoutePosition ?: return@launch
                val acceptedOrigin = if (isInAppNavigationActive) lastDisplayedNavigationSelfLocation ?: origin else origin
                val acceptedDestination = if (isInAppNavigationActive) {
                    activeRouteFriendId?.let { lastDisplayedNavigationFriendLocations[it] } ?: currentDestination
                } else {
                    currentDestination
                }

                val originStale = SphericalUtil.computeDistanceBetween(origin, acceptedOrigin) >= ROUTE_ENDPOINT_UPDATE_THRESHOLD_METERS
                val destinationStale = SphericalUtil.computeDistanceBetween(destination, acceptedDestination) >= ROUTE_ENDPOINT_UPDATE_THRESHOLD_METERS

                if (!routeRequestGeneration.isCurrent(requestGeneration) || originStale || destinationStale) {
                    Log.d(TAG, "route_response_ignored_stale mode=${mode.name} originStale=$originStale destinationStale=$destinationStale")
                    return@launch
                }
                routeModeStates[mode] = RouteModeState.Success(route, origin, destination)
                Log.d(
                    TAG,
                    "route_refresh_success mode=${mode.name} distanceMeters=${route.distanceMeters} durationSeconds=${route.durationSeconds}",
                )
                renderRouteOptionLabel(mode)
                selectFirstAvailableModeIfNeeded()
                if (viewModel.selectedTravelMode.value == mode) {
                    consecutiveOffRouteUpdates = 0
                    drawRouteLine(route, fitBounds)
                    showDirectionPanels(mode, route.distanceMeters, route.durationSeconds)
                    Log.d(TAG, "route_line_rendered mode=${mode.name} generation=$requestGeneration")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (!routeRequestGeneration.isCurrent(requestGeneration)) {
                    Log.d(TAG, "route_response_ignored_stale mode=${mode.name}")
                    return@launch
                }
                val failureMessage = routeFailureMessage(error)
                routeModeStates[mode] = RouteModeState.Failure(failureMessage)
                val routesError = error as? GoogleRoutesException
                Log.e(
                    TAG,
                    "route_request_failed mode=${mode.name} " +
                        "httpCode=${routesError?.httpCode ?: "n/a"} " +
                        "backendStatus=${routesError?.backendStatus ?: "n/a"} " +
                        "backendMessage=${sanitizeRouteLogMessage(routesError?.backendMessage)} " +
                        "errorType=${error.javaClass.simpleName} " +
                        "routeBackend=firebase_function",
                    error,
                )
                val currentDestination = activeRoutePosition
                if (currentDestination != null &&
                    SphericalUtil.computeDistanceBetween(currentDestination, destination) <=
                    ROUTE_RESPONSE_STALE_DISTANCE_METERS
                ) {
                    renderRouteOptionLabel(mode)
                    selectFirstAvailableModeIfNeeded()
                    if (viewModel.selectedTravelMode.value == mode) {
                        showRouteUnavailable(failureMessage)
                        Log.d(TAG, "route_ui_rendered mode=${mode.name} state=FAILURE")
                    }
                }
            }
        }
    }

    private fun selectOrRequestRouteMode(mode: DirectionTravelMode) {
        if (mode == DirectionTravelMode.MOTORCYCLE && !isTwoWheelerOptionVisible) return
        val origin = viewModel.selfLocation.value?.takeIf(::isValidRoutePoint) ?: return
        val destination = activeRoutePosition?.takeIf(::isValidRoutePoint) ?: return
        val state = routeModeStates[mode]
        if (state is RouteModeState.Success &&
            SphericalUtil.computeDistanceBetween(state.origin, origin) < ROUTE_REFRESH_DISTANCE_METERS &&
            SphericalUtil.computeDistanceBetween(state.destination, destination) < ROUTE_RESPONSE_STALE_DISTANCE_METERS
        ) {
            renderSelectedRouteMode(mode)
        } else if (state !is RouteModeState.Loading) {
            requestRouteMode(origin, destination, mode, fitBounds = false)
        }
    }

    private fun isValidRoutePoint(point: LatLng): Boolean =
        point.latitude.isFinite() &&
                point.longitude.isFinite() &&
                point.latitude in -90.0..90.0 &&
                point.longitude in -180.0..180.0 &&
                !(point.latitude == 0.0 && point.longitude == 0.0)

    private fun drawRouteLine(route: GoogleRoute, fitBounds: Boolean) {
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
        DirectionTravelMode.entries.forEach { mode ->
            routeModeStates[mode] = if (
                mode == DirectionTravelMode.MOTORCYCLE && !isTwoWheelerOptionVisible
            ) {
                RouteModeState.Idle
            } else {
                RouteModeState.Loading
            }
        }
        renderAllRouteOptionLabels()
        directionTopPanel.root.visibility = View.VISIBLE
        directionBottomPanel.root.visibility = View.VISIBLE

        layoutTools.visibility = View.GONE
        cardSearch.visibility = View.GONE
        txtTitle.text = getString(R.string.directions)
    }

    private fun showDirectionPanels(
        mode: DirectionTravelMode,
        distanceMeters: Int,
        durationSeconds: Long,
    ) = with(binding) {
        if (navigationArrivalAnnounced) {
            directionBottomPanel.tvRouteTime.text = getString(R.string.route_arrived)
            directionBottomPanel.tvTraffic.text = getString(R.string.route_arrived_description)
            renderNavigationButton()
            return@with
        }
        val distanceText = formatDistance(distanceMeters)
        val durationText = formatDuration(durationSeconds)
        directionBottomPanel.tvRouteTime.text =
            getString(R.string.route_duration_and_distance, durationText, distanceText)
        directionBottomPanel.tvTraffic.text = getString(
            when (mode) {
                DirectionTravelMode.CAR -> R.string.route_fastest_driving
                DirectionTravelMode.MOTORCYCLE -> R.string.route_motorcycle_description
                DirectionTravelMode.WALKING -> R.string.route_walking_description
            }
        )
        renderNavigationButton()
    }

    private fun renderAllRouteOptionLabels() {
        DirectionTravelMode.entries.forEach(::renderRouteOptionLabel)
    }

    private fun setTwoWheelerOptionVisible(bool: Boolean) {
        isTwoWheelerOptionVisible = bool
        val motorcycleOption = binding.directionTopPanel.layoutRoutes
            .getChildAt(DirectionTravelMode.MOTORCYCLE.ordinal)
        motorcycleOption?.visibility = if (bool) View.VISIBLE else View.GONE

        if (!bool && viewModel.selectedTravelMode.value == DirectionTravelMode.MOTORCYCLE) {
            viewModel.selectTravelMode(DirectionTravelMode.CAR)
            renderSelectedTravelMode(DirectionTravelMode.CAR)
            renderSelectedRouteMode(DirectionTravelMode.CAR)
        }
    }

    private fun renderRouteOptionLabel(mode: DirectionTravelMode) {
        val routeOptions = binding.directionTopPanel.layoutRoutes
        val item = routeOptions.getChildAt(mode.ordinal) ?: return
        when (val state = routeModeStates[mode] ?: RouteModeState.Idle) {
            RouteModeState.Idle -> {
                item.visibility = if (
                    mode == DirectionTravelMode.MOTORCYCLE && !isTwoWheelerOptionVisible
                ) View.GONE else View.VISIBLE
                item.findViewById<TextView>(R.id.tvDuration)?.text = getString(R.string.route_tap_to_calculate)
                item.findViewById<TextView>(R.id.tvDistance)?.text = ""
            }
            RouteModeState.Loading -> {
                item.visibility = if (
                    mode == DirectionTravelMode.MOTORCYCLE && !isTwoWheelerOptionVisible
                ) View.GONE else View.VISIBLE
                item.findViewById<TextView>(R.id.tvDuration)?.text = getString(R.string.route_calculating_short)
                item.findViewById<TextView>(R.id.tvDistance)?.text = ""
            }
            is RouteModeState.Success -> {
                item.visibility = if (
                    mode == DirectionTravelMode.MOTORCYCLE && !isTwoWheelerOptionVisible
                ) View.GONE else View.VISIBLE
                item.findViewById<TextView>(R.id.tvDuration)?.text = formatDuration(state.route.durationSeconds)
                item.findViewById<TextView>(R.id.tvDistance)?.text = formatDistance(state.route.distanceMeters)
            }
            is RouteModeState.Failure -> {
                item.visibility = if (hideUnavailableModesForActiveRoute) View.GONE else View.VISIBLE
                item.findViewById<TextView>(R.id.tvDuration)?.text = getString(R.string.route_unavailable_short)
                item.findViewById<TextView>(R.id.tvDistance)?.text = ""
            }
        }
    }

    private fun selectFirstAvailableModeIfNeeded() {
        if (!hideUnavailableModesForActiveRoute) return
        val selectedMode = viewModel.selectedTravelMode.value
        if (routeModeStates[selectedMode] !is RouteModeState.Failure) return
        val fallbackMode = DirectionTravelMode.entries.firstOrNull { mode ->
            routeModeStates[mode] is RouteModeState.Success
        } ?: return
        viewModel.selectTravelMode(fallbackMode)
        renderSelectedTravelMode(fallbackMode)
        renderSelectedRouteMode(fallbackMode)
    }

    private fun renderSelectedRouteMode(mode: DirectionTravelMode) {
        when (val state = routeModeStates[mode] ?: return) {
            RouteModeState.Idle -> selectOrRequestRouteMode(mode)
            RouteModeState.Loading -> showSelectedModeLoading()
            is RouteModeState.Success -> {
                drawRouteLine(state.route, fitBounds = false)
                showDirectionPanels(mode, state.route.distanceMeters, state.route.durationSeconds)
            }
            is RouteModeState.Failure -> showRouteUnavailable(state.messageRes)
        }
    }

    private fun showSelectedModeLoading() = with(binding) {
        routeOutline?.remove()
        routeOutline = null
        routeLine?.remove()
        routeLine = null
        directionBottomPanel.tvRouteTime.text = getString(R.string.route_calculating)
        directionBottomPanel.tvTraffic.text = getString(R.string.route_calculating_description)
        directionBottomPanel.btnStartNavigation.isEnabled = false
        directionBottomPanel.btnStartNavigation.alpha = 0.55f
    }

    private fun showRouteUnavailable(
        @StringRes descriptionRes: Int = R.string.route_unavailable_description,
    ) = with(binding) {
        routeOutline?.remove()
        routeOutline = null
        routeLine?.remove()
        routeLine = null
        directionBottomPanel.tvRouteTime.text = getString(R.string.route_unavailable_short)
        directionBottomPanel.tvTraffic.text = getString(descriptionRes)
        directionBottomPanel.btnStartNavigation.isEnabled = false
        directionBottomPanel.btnStartNavigation.alpha = 0.55f
    }

    @StringRes
    private fun routeFailureMessage(error: Exception): Int {
        if (error is UnknownHostException || error is SocketTimeoutException) {
            return R.string.route_network_error
        }
        if (error is GoogleRoutesNoRouteException) {
            return R.string.route_unavailable_description
        }
        if (error is GoogleRoutesInvalidResponseException) {
            return R.string.route_invalid_response
        }
        val routesError = error as? GoogleRoutesException
            ?: return R.string.route_not_found_description
        val backendText = listOfNotNull(
            routesError.backendStatus,
            routesError.backendMessage,
        ).joinToString(" ").lowercase(Locale.US)
        return when {
            routesError.httpCode == 400 || "invalid_argument" in backendText ->
                R.string.route_invalid_request

            routesError.httpCode == 429 ||
                "resource_exhausted" in backendText ||
                "quota" in backendText -> R.string.route_quota_exceeded

            "billing" in backendText -> R.string.route_billing_required

            "not enabled" in backendText ||
                "has not been used" in backendText ||
                "access_not_configured" in backendText -> R.string.route_api_not_configured

            routesError.httpCode == 403 || "permission_denied" in backendText ->
                R.string.route_app_not_authorized

            routesError.httpCode in 500..599 -> R.string.route_service_unavailable
            else -> R.string.route_not_found_description
        }
    }

    private fun sanitizeRouteLogMessage(message: String?): String {
        if (message.isNullOrBlank()) return "n/a"
        return message
            .replace(Regex("AIza[0-9A-Za-z_-]+"), "[redacted]")
            .replace(Regex("(?i)(key=)[^&\\s]+"), "$1[redacted]")
            .take(MAX_ROUTE_LOG_MESSAGE_LENGTH)
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
        stopInAppNavigation(restoreRouteOverview = false, showStoppedMessage = false)
        routeJobs.values.forEach(Job::cancel)
        routeJobs.clear()
        twoWheelerCoverageJob?.cancel()
        twoWheelerCoverageJob = null
        routeModeStates.clear()
        setTwoWheelerOptionVisible(true)
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
        hideUnavailableModesForActiveRoute = false
        selectedFriendMarkerId = null
        applyMarkerSelection()
        lastRouteOrigin = null
        lastRouteDestination = null
        lastRouteRequestAt = 0L
        consecutiveOffRouteUpdates = 0
        directionBottomPanel.btnStartNavigation.setText(R.string.route_start_navigation)
        directionTopPanel.root.visibility = View.GONE
        directionBottomPanel.root.visibility = View.GONE
        layoutTools.visibility = View.VISIBLE
        cardSearch.visibility = View.VISIBLE
        txtTitle.text = getString(R.string.realtime_tracker)
    }

    private fun toggleInAppNavigation() {
        if (isInAppNavigationActive) {
            stopInAppNavigation()
            return
        }

        val destination = activeRoutePosition ?: return
        val mode = viewModel.selectedTravelMode.value
        val origin = viewModel.selfLocation.value?.takeIf { isValidRoutePoint(it) }
        val state = routeModeStates[mode]
        if (origin == null) {
            Toast.makeText(requireContext(), R.string.route_waiting_for_location, Toast.LENGTH_SHORT)
                .show()
            return
        }
        if (state !is RouteModeState.Success) {
            Toast.makeText(requireContext(), R.string.route_navigation_route_required, Toast.LENGTH_SHORT)
                .show()
            if (state !is RouteModeState.Loading) {
                requestRouteMode(origin, destination, mode, fitBounds = false)
            }
            return
        }

        isInAppNavigationActive = true
        navigationArrivalAnnounced = false
        lastNavigationCameraUpdateAt = 0L
        navigationCameraZoom = NAVIGATION_DEFAULT_ZOOM
        navigationCameraGestureInProgress = false
        consecutiveOffRouteUpdates = 0
        lastDisplayedNavigationSelfLocation = origin
        lastDisplayedNavigationFriendLocations.clear()
        if (!isCompassEnabled) {
            navigationEnabledCompass = true
            toggleCompass()
        }
        binding.txtTitle.text = getString(R.string.route_navigation_title)
        binding.directionBottomPanel.tvTraffic.text =
            getString(R.string.route_navigation_active_description)
        renderNavigationButton()
        followNavigationCamera(origin, compassManager.bearing.value, force = true)
        Toast.makeText(requireContext(), R.string.route_navigation_started, Toast.LENGTH_SHORT).show()
        Log.d(TAG, "in_app_navigation_started mode=${mode.name}")
    }

    private fun updateInAppNavigation(origin: LatLng, destination: LatLng) {
        if (!isInAppNavigationActive) return
        val distanceToDestination = SphericalUtil.computeDistanceBetween(origin, destination)
        if (navigationArrivalAnnounced &&
            distanceToDestination > NAVIGATION_ARRIVAL_RESET_DISTANCE_METERS
        ) {
            navigationArrivalAnnounced = false
            val selectedState = routeModeStates[viewModel.selectedTravelMode.value]
            if (selectedState is RouteModeState.Success) {
                showDirectionPanels(
                    viewModel.selectedTravelMode.value,
                    selectedState.route.distanceMeters,
                    selectedState.route.durationSeconds,
                )
            }
        }
        if (distanceToDestination <= NAVIGATION_ARRIVAL_DISTANCE_METERS) {
            if (!navigationArrivalAnnounced) {
                navigationArrivalAnnounced = true
                binding.directionBottomPanel.tvRouteTime.text = getString(R.string.route_arrived)
                binding.directionBottomPanel.tvTraffic.text =
                    getString(R.string.route_arrived_description)
                renderNavigationButton()
                Toast.makeText(requireContext(), R.string.route_arrived, Toast.LENGTH_LONG).show()
                Log.d(TAG, "in_app_navigation_arrived")
            }
            followNavigationCamera(origin, compassManager.bearing.value)
            return
        }

        refreshRouteIfNeeded(origin, destination)
        
        val lastCameraLoc = lastDisplayedNavigationSelfLocation
        if (lastCameraLoc != null) {
            followNavigationCamera(lastCameraLoc, compassManager.bearing.value)
        }
    }

    private fun followNavigationCamera(
        location: LatLng,
        bearing: Float,
        force: Boolean = false,
    ) {
        val map = googleMap ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && now - lastNavigationCameraUpdateAt < NAVIGATION_CAMERA_INTERVAL_MS) return
        if (!force && navigationCameraGestureInProgress) return
        lastNavigationCameraUpdateAt = now
        val cameraPosition = CameraPosition.Builder()
            .target(location)
            .zoom(navigationCameraZoom ?: NAVIGATION_DEFAULT_ZOOM)
            .tilt(NAVIGATION_TILT)
            .bearing(((bearing % 360f) + 360f) % 360f)
            .build()
        map.animateCamera(
            CameraUpdateFactory.newCameraPosition(cameraPosition),
            NAVIGATION_CAMERA_ANIMATION_MS,
            null,
        )
    }

    private fun stopInAppNavigation(
        restoreRouteOverview: Boolean = true,
        showStoppedMessage: Boolean = true,
    ) {
        if (!isInAppNavigationActive) return
        isInAppNavigationActive = false
        navigationArrivalAnnounced = false
        lastNavigationCameraUpdateAt = 0L
        navigationCameraZoom = null
        navigationCameraGestureInProgress = false
        consecutiveOffRouteUpdates = 0
        lastDisplayedNavigationSelfLocation = null
        lastDisplayedNavigationFriendLocations.clear()
        if (navigationEnabledCompass && isCompassEnabled) {
            toggleCompass()
        }
        navigationEnabledCompass = false
        binding.txtTitle.text = getString(R.string.directions)
        renderNavigationButton()
        val selectedState = routeModeStates[viewModel.selectedTravelMode.value]
        if (selectedState is RouteModeState.Success) {
            showDirectionPanels(
                viewModel.selectedTravelMode.value,
                selectedState.route.distanceMeters,
                selectedState.route.durationSeconds,
            )
            if (restoreRouteOverview) {
                drawRouteLine(selectedState.route, fitBounds = true)
            }
        }
        if (showStoppedMessage && isAdded) {
            Toast.makeText(requireContext(), R.string.route_navigation_stopped, Toast.LENGTH_SHORT).show()
        }
        Log.d(TAG, "in_app_navigation_stopped")
    }

    private fun renderNavigationButton() = with(binding.directionBottomPanel.btnStartNavigation) {
        isEnabled = true
        alpha = 1f
        setText(
            if (isInAppNavigationActive) {
                R.string.route_end_navigation
            } else {
                R.string.route_start_navigation
            }
        )
    }

    private fun updateMarkersWithAvatars(
        self: LatLng?,
        friends: List<FriendLocation>,
        selfAvatar: String,
        selfName: String
    ) {
        if (googleMap == null) return

        self?.let { latLng ->
            val shouldUpdate = if (isInAppNavigationActive) {
                val previous = lastDisplayedNavigationSelfLocation
                if (shouldAcceptMovement(previous, latLng)) {
                    lastDisplayedNavigationSelfLocation = latLng
                    Log.d(TAG, "route_endpoint_update_accepted type=origin movedMeters=${previous?.let { SphericalUtil.computeDistanceBetween(it, latLng) } ?: 0.0}")
                    true
                } else {
                    false
                }
            } else {
                lastDisplayedNavigationSelfLocation = null
                true
            }
            if (shouldUpdate) {
                showSelfMarker(latLng, selfAvatar, selfName)
            } else {
                Log.v(TAG, "route_endpoint_update_ignored_below_threshold type=origin")
            }
        }

        showFriendMarkers(friends)
    }

    private fun showFriendMarkers(friends: List<FriendLocation>) {
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
                friendNames.remove(entry.key)
                lastDisplayedNavigationFriendLocations.remove(entry.key)
            }
        }

        validFriends.forEach { friend ->
            val existingMarker = friendMarkers[friend.id]
            val position = LatLng(friend.latitude, friend.longitude)

            val shouldUpdate = if (isInAppNavigationActive) {
                val previous = lastDisplayedNavigationFriendLocations[friend.id]
                if (shouldAcceptMovement(previous, position)) {
                    lastDisplayedNavigationFriendLocations[friend.id] = position
                    true
                } else {
                    // Update avatar/name anyway if they changed, even if location didn't move 100m
                    if (existingMarker != null && (friendAvatars[friend.id] != friend.avatarSignature() || friendNames[friend.id] != friend.name)) {
                        updateMarkerIcon(existingMarker, friend.avatarKey, friend.avatarUrl, friend.name.ifBlank { "Friend" })
                    }
                    false
                }
            } else {
                lastDisplayedNavigationFriendLocations.remove(friend.id)
                true
            }

            if (shouldUpdate) {
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
                    existingMarker.rotation = 0f
                    existingMarker.isFlat = false
                    val displayName = friend.name.ifBlank { "Friend" }
                    if (friendAvatars[friend.id] != friend.avatarSignature() || friendNames[friend.id] != displayName) {
                        updateMarkerIcon(
                            existingMarker,
                            friend.avatarKey,
                            friend.avatarUrl,
                            displayName
                        )
                    }
                    friendNames[friend.id] = displayName
                }

                if (activeRouteFriendId == friend.id) {
                    Log.d(TAG, "route_endpoint_update_accepted type=destination movedMeters=${lastDisplayedNavigationFriendLocations[friend.id]?.let { SphericalUtil.computeDistanceBetween(it, position) } ?: 0.0}")
                    activeRoutePosition = position
                    selectedDestinationMarker?.position = position
                }
            } else {
                Log.v(TAG, "route_endpoint_update_ignored_below_threshold type=destination")
            }
            friendAvatars[friend.id] = friend.avatarSignature()
        }
        applyMarkerSelection()
    }

    private fun FriendLocation.avatarSignature(): String = "$avatarKey|$avatarUrl"

    private sealed interface RouteModeState {
        data object Idle : RouteModeState
        data object Loading : RouteModeState
        data class Success(
            val route: GoogleRoute,
            val origin: LatLng,
            val destination: LatLng,
        ) : RouteModeState
        data class Failure(@param:StringRes val messageRes: Int) : RouteModeState
    }

    companion object {
        private const val TAG = "LocationFragment"
        private const val DEFAULT_ZOOM = 15f
        private const val BASE_CONE_HEIGHT = 500.0 // Chiều dài cơ sở tại zoom 15
        private const val SELECTED_ROUTE_HEAD_START_MS = 700L
        private const val ROUTE_REFRESH_INTERVAL_MS = 30_000L
        private const val ROUTE_REFRESH_DISTANCE_METERS = 200.0
        private const val ROUTE_DESTINATION_REFRESH_DISTANCE_METERS = 50.0
        private const val NAVIGATION_ROUTE_REFRESH_INTERVAL_MS = 15_000L
        private const val NAVIGATION_OFF_ROUTE_TOLERANCE_METERS = 60.0
        private const val NAVIGATION_OFF_ROUTE_CONFIRMATION_UPDATES = 2
        private const val NAVIGATION_ARRIVAL_DISTANCE_METERS = 30.0
        private const val NAVIGATION_ARRIVAL_RESET_DISTANCE_METERS = 60.0
        private const val ROUTE_ENDPOINT_UPDATE_THRESHOLD_METERS = 100.0
        private const val NAVIGATION_CAMERA_INTERVAL_MS = 750L
        private const val NAVIGATION_CAMERA_ANIMATION_MS = 650
        private const val NAVIGATION_DEFAULT_ZOOM = 16f
        private const val NAVIGATION_TILT = 50f
        private const val ROUTE_RESPONSE_STALE_DISTANCE_METERS = 50.0
        private const val MAX_ROUTE_LOG_MESSAGE_LENGTH = 500
        private val ROUTE_START_COLOR = Color.rgb(62, 218, 105)
        private val ROUTE_END_COLOR = Color.rgb(65, 218, 221)

        fun newInstance() = LocationFragment()

        private data class DataPackage(
            val self: LatLng?,
            val friends: List<FriendLocation>,
            val avatar: String,
            val name: String,
            val friendsLoaded: Boolean
        )
    }
}
