package com.nhn.gps.location.phone.tracker.ui.location

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
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
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.card.MaterialCardView
import com.google.maps.android.SphericalUtil
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.databinding.FragmentLocationBinding
import com.nhn.gps.location.phone.tracker.navigation.AppDestination
import com.nhn.gps.location.phone.tracker.ui.friend.FriendAdapter
import com.nhn.gps.location.phone.tracker.ui.main.MainViewModel
import com.nhn.gps.location.phone.tracker.ui.permission.LocationPermissionBottomSheet
import com.nhn.gps.location.phone.tracker.util.MapMarkerHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LocationFragment : BaseFragment<FragmentLocationBinding, LocationViewModel>(),
    OnMapReadyCallback {

    private enum class LocationMode {
        NORMAL,
        DIRECTIONS
    }

    private enum class PendingFriendSheet {
        NONE,
        FRIENDS,
        SEARCH
    }

    override val viewModel: LocationViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by viewModels({ requireActivity() })

    @Inject
    lateinit var compassManager: CompassManager

    private var googleMap: GoogleMap? = null
    private val DEFAULT_ZOOM = 15f
    private val BASE_CONE_HEIGHT = 500.0 // Chiều dài cơ sở tại zoom 15

    private var selfMarker: Marker? = null
    private var directionsOriginMarker: Marker? = null
    private var selectedDestinationMarker: Marker? = null
    private var routeLine: Polyline? = null
    private var directionOverlay: GroundOverlay? = null
    private val friendMarkers = mutableMapOf<String, Marker>()
    private val friendAvatars = mutableMapOf<String, String>()
    private val friendNames = mutableMapOf<String, String>()
    private var selfAvatarUrl: String? = null
    private var selfName: String? = null

    private var locationMode = LocationMode.NORMAL
    private var hasLocationPermissionForUi = false

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<MaterialCardView>
    private lateinit var friendSearchBottomSheetBehavior: BottomSheetBehavior<MaterialCardView>
    private var bottomSheetCallback: BottomSheetBehavior.BottomSheetCallback? = null
    private var friendSearchBottomSheetCallback: BottomSheetBehavior.BottomSheetCallback? = null
    private lateinit var friendAdapter: FriendAdapter
    private lateinit var friendSearchAdapter: FriendAdapter
    private lateinit var friendSearchHistoryAdapter: FriendSearchHistoryAdapter

    private var isCompassEnabled = false
    private var hasAutoZoomed = false
    private var lastDataPackage: DataPackage? = null
    private var pendingDestination: AppDestination? = null
    private var activeRouteName: String? = null
    private var activeRoutePosition: LatLng? = null
    private var activeRouteFriendId: String? = null

    private var pendingFriendSheet = PendingFriendSheet.NONE

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentLocationBinding = FragmentLocationBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) = with(binding) {
        val mapFragment =
            childFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this@LocationFragment)

        setupTravelModeOptions()

        setupFriendBottomSheet()
        setupFriendSearchBottomSheet()
        setupDirectionPanel()

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.isFriendSearchActive.value) {
                    viewModel.closeFriendSearch()
                } else if (bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
                    hideFriendSheet()
                } else if (locationMode == LocationMode.DIRECTIONS) {
                    clearRoute()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        cardBack.setOnClickListener {
            if (locationMode == LocationMode.DIRECTIONS) {
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

        binding.search.setOnClickListener {
            if (locationMode == LocationMode.NORMAL) {
                val wasAlreadyActive = viewModel.isFriendSearchActive.value
                viewModel.openFriendSearch()
                if (wasAlreadyActive) {
                    handleFriendSearchState(true)
                }
            }
        }

        cardImgFriend.setOnClickListener {
            if (viewModel.isFriendSearchActive.value) {
                viewModel.closeFriendSearch()
                showFriendSheet()
            } else {
                if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                    hideFriendSheet()
                } else {
                    showFriendSheet()
                }
            }
        }
    }

    private fun setupDirectionPanel() = with(binding) {
        directionBottomPanel.btnStartNavigation.setOnClickListener {
            startExternalNavigation()
        }
    }

    private fun setupTravelModeOptions() = with(binding.directionTopPanel) {
        itemMotorcycle.imgMode.setImageResource(R.drawable.ic_motorcycle)
        itemWalking.imgMode.setImageResource(R.drawable.ic_walking)

        itemCar.root.contentDescription = getString(R.string.car)
        itemMotorcycle.root.contentDescription = getString(R.string.motorcycle)
        itemWalking.root.contentDescription = getString(R.string.walking)

        itemCar.root.setOnClickListener { viewModel.setSelectedTravelMode(TravelMode.CAR) }
        itemMotorcycle.root.setOnClickListener { viewModel.setSelectedTravelMode(TravelMode.MOTORCYCLE) }
        itemWalking.root.setOnClickListener { viewModel.setSelectedTravelMode(TravelMode.WALKING) }
    }

    private fun setupFriendBottomSheet() = with(binding) {
        bottomSheetBehavior = BottomSheetBehavior.from(friendBottomSheetLayout.friendBottomSheet)
        bottomSheetBehavior.isHideable = true
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        friendAdapter = FriendAdapter(
            showMoreButton = false,
            onItemClick = { _ ->
                hideFriendSheet()
                navigationManager.navigateTo(AppDestination.MyFriend)
            }
        )

        friendBottomSheetLayout.rvFriends.layoutManager = LinearLayoutManager(requireContext())
        friendBottomSheetLayout.rvFriends.adapter = friendAdapter

        friendBottomSheetLayout.layoutEmpty.btnAddFriendEmpty.setOnClickListener {
            if (isAdded) {
                pendingDestination = AppDestination.AddFriend
                hideFriendSheet()
            }
        }

        friendBottomSheetLayout.btnAddFriend.setOnClickListener {
            if (isAdded) {
                pendingDestination = AppDestination.AddFriend
                hideFriendSheet()
            }
        }

        friendBottomSheetLayout.tvViewAll.setOnClickListener {
            if (isAdded) {
                pendingDestination = AppDestination.MyFriend
                hideFriendSheet()
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
                            if (!viewModel.isFriendSearchActive.value) {
                                cardImgFriend.setCardBackgroundColor(
                                    resources.getColor(R.color.bg_botton_friend, null)
                                )
                            }
                        }

                        BottomSheetBehavior.STATE_HIDDEN,
                        BottomSheetBehavior.STATE_COLLAPSED -> {
                            if (!viewModel.isFriendSearchActive.value) {
                                cardImgFriend.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
                            }
                            
                            if (newState == BottomSheetBehavior.STATE_HIDDEN && pendingFriendSheet == PendingFriendSheet.SEARCH) {
                                pendingFriendSheet = PendingFriendSheet.NONE
                                showFriendSearchSheet()
                            } else if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                                updateCardSearchAfterSheetHidden()
                            }

                            pendingDestination?.let {
                                navigationManager.navigateTo(it)
                                pendingDestination = null
                            }
                        }
                    }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                if (viewModel.isFriendSearchActive.value) return
                updateCardSearchPosition(bottomSheet)
            }
        }
        bottomSheetBehavior.addBottomSheetCallback(bottomSheetCallback!!)
    }

    private fun setupFriendSearchBottomSheet() = with(binding) {
        friendSearchBottomSheetBehavior = BottomSheetBehavior.from(friendSearchBottomSheetLayout.friendSearchBottomSheet)
        friendSearchBottomSheetBehavior.apply {
            isHideable = true
            state = BottomSheetBehavior.STATE_HIDDEN
            skipCollapsed = true
            isFitToContents = false
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            friendSearchBottomSheetBehavior.expandedOffset = topInset + resources.getDimensionPixelSize(R.dimen.d_80)
            insets
        }

        friendSearchAdapter = FriendAdapter(
            showMoreButton = false,
            onItemClick = { friend ->
                if (friend.latitude != 0.0 && friend.longitude != 0.0) {
                    viewModel.recordFriendSearch(friend.id)
                    val position = LatLng(friend.latitude, friend.longitude)
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(position, DEFAULT_ZOOM))
                    hideSearchKeyboardAndClearFocus()
                    friendSearchBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                }
            }
        )

        friendSearchHistoryAdapter = FriendSearchHistoryAdapter(
            onFriendClick = { friend ->
                if (friend.latitude != 0.0 && friend.longitude != 0.0) {
                    viewModel.recordFriendSearch(friend.id)
                    val position = LatLng(friend.latitude, friend.longitude)
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(position, DEFAULT_ZOOM))
                    hideSearchKeyboardAndClearFocus()
                    friendSearchBottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                }
            },
            onRemoveClick = { friend ->
                viewModel.removeFriendSearchHistory(friend.id)
            }
        )

        friendSearchBottomSheetLayout.rvSearchFriends.layoutManager = LinearLayoutManager(requireContext())
        friendSearchBottomSheetLayout.rvSearchFriends.adapter = friendSearchAdapter

        friendSearchBottomSheetLayout.rvSearchHistory.layoutManager = LinearLayoutManager(requireContext())
        friendSearchBottomSheetLayout.rvSearchHistory.adapter = friendSearchHistoryAdapter

        friendSearchBottomSheetLayout.btnClearAllHistory.setOnClickListener {
            viewModel.clearFriendSearchHistory()
        }

        friendSearchBottomSheetLayout.edtFriendSearch.doAfterTextChanged { editable ->
            val input = editable?.toString().orEmpty()
            viewModel.updateFriendSearchInput(input)
            if (input.isBlank()) {
                viewModel.clearFriendSearch()
            }
        }

        friendSearchBottomSheetLayout.edtFriendSearch.setOnEditorActionListener { _, actionId, event ->
            val isSearchAction = actionId == EditorInfo.IME_ACTION_SEARCH
            val isEnterKey = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP
            if (isSearchAction || isEnterKey) {
                viewModel.submitFriendSearch()
                hideSearchKeyboardAndClearFocus()
                true
            } else {
                false
            }
        }

        friendSearchBottomSheetLayout.btnClearFriendSearch.setOnClickListener {
            friendSearchBottomSheetLayout.edtFriendSearch.text?.clear()
            viewModel.clearFriendSearch()
        }

        friendSearchBottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED,
                    BottomSheetBehavior.STATE_HALF_EXPANDED -> {
                        bottomSheet.post {
                            updateCardSearchPosition(bottomSheet)
                        }
                    }
                    BottomSheetBehavior.STATE_HIDDEN -> {
                        if (pendingFriendSheet == PendingFriendSheet.FRIENDS) {
                            pendingFriendSheet = PendingFriendSheet.NONE
                            showFriendSheet()
                        } else {
                            handleSearchSheetHiddenByUser()
                        }
                    }
                    else -> {}
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                if (!viewModel.isFriendSearchActive.value) return
                updateCardSearchPosition(bottomSheet)
            }
        }
        friendSearchBottomSheetBehavior.addBottomSheetCallback(friendSearchBottomSheetCallback!!)
        setupFriendSearchImeInsets()
    }

    private fun setupFriendSearchImeInsets() {
        val container = binding.friendSearchBottomSheetLayout.friendSearchBottomSheet
        val initialLeft = container.paddingLeft
        val initialTop = container.paddingTop
        val initialRight = container.paddingRight
        val initialBottom = container.paddingBottom
        
        ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val systemBarBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val imeExtraBottom = (imeBottom - systemBarBottom).coerceAtLeast(0)
            
            view.setPadding(
                initialLeft,
                initialTop,
                initialRight,
                initialBottom + imeExtraBottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(container)
    }

    private fun focusSearchInputAfterLayout() {
        val searchInput = binding.friendSearchBottomSheetLayout.edtFriendSearch
        searchInput.post {
            if (!isAdded || view == null) return@post
            if (!viewModel.isFriendSearchActive.value) return@post
            if (friendSearchBottomSheetBehavior.state != BottomSheetBehavior.STATE_EXPANDED) return@post

            if (!searchInput.hasFocus()) {
                searchInput.requestFocus()
            }
            val window = activity?.window ?: return@post
            WindowCompat.getInsetsController(window, searchInput).show(WindowInsetsCompat.Type.ime())
        }
    }

    private fun handleSearchSheetHiddenByUser() {
        hideSearchKeyboardAndClearFocus()
        if (viewModel.isFriendSearchActive.value) {
            viewModel.closeFriendSearch()
        }
    }

    private fun hideSearchKeyboardAndClearFocus() {
        val searchInput = binding.friendSearchBottomSheetLayout.edtFriendSearch
        searchInput.clearFocus()
        val window = activity?.window ?: return
        WindowCompat.getInsetsController(window, searchInput).hide(WindowInsetsCompat.Type.ime())
    }

    private fun updateCardSearchPosition(activeSheet: View?) = withBinding {
        if (locationMode != LocationMode.NORMAL) return@withBinding

        cardSearch.visibility = View.VISIBLE
        cardSearch.bringToFront()
        cardSearch.elevation = resources.getDimension(R.dimen.d_16)

        if (activeSheet == null || activeSheet.visibility != View.VISIBLE) {
            cardSearch.translationY = 0f
            return@withBinding
        }

        val margin = resources.getDimensionPixelSize(R.dimen.d_12).toFloat()
        val parentHeight = (root as ViewGroup).height
        val sheetTop = activeSheet.top.toFloat()

        if (sheetTop > 0 && sheetTop < parentHeight) {
            val targetTranslationY = -(parentHeight - sheetTop + margin)
            // defaultBottomMargin of 28dp as per layout
            val defaultBottomMargin = 28 * resources.displayMetrics.density
            cardSearch.translationY = (targetTranslationY + defaultBottomMargin).coerceAtMost(0f)
        } else if (sheetTop <= 0 && activeSheet.height > 0) {
             // Handle case where top is 0 or less but sheet is expanded
            val targetTranslationY = -(parentHeight - margin)
            val defaultBottomMargin = 28 * resources.displayMetrics.density
            cardSearch.translationY = (targetTranslationY + defaultBottomMargin).coerceAtMost(0f)
        } else {
            cardSearch.translationY = 0f
        }
        cardSearch.invalidate()
    }

    private fun updateCardSearchAfterSheetHidden() {
        if (viewModel.isFriendSearchActive.value) {
            updateCardSearchPosition(binding.friendSearchBottomSheetLayout.friendSearchBottomSheet)
        } else if (bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
            updateCardSearchPosition(binding.friendBottomSheetLayout.friendBottomSheet)
        } else {
            updateCardSearchPosition(null)
        }
    }

    private fun showFriendSheet() {
        if (friendSearchBottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
            pendingFriendSheet = PendingFriendSheet.FRIENDS
            hideFriendSearchSheet()
        } else {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            binding.friendBottomSheetLayout.friendBottomSheet.post {
                if (!isAdded || view == null) return@post
                updateCardSearchPosition(binding.friendBottomSheetLayout.friendBottomSheet)
            }
        }
    }

    private fun hideFriendSheet() {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        updateCardSearchAfterSheetHidden()
    }

    private fun showFriendSearchSheet() {
        if (locationMode != LocationMode.NORMAL) return
        
        if (bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
            pendingFriendSheet = PendingFriendSheet.SEARCH
            hideFriendSheet()
        } else {
            val searchSheet = binding.friendSearchBottomSheetLayout.friendSearchBottomSheet
            if (searchSheet.visibility != View.VISIBLE) {
                searchSheet.visibility = View.VISIBLE
            }

            if (friendSearchBottomSheetBehavior.state != BottomSheetBehavior.STATE_EXPANDED) {
                friendSearchBottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            } else {
                focusSearchInputAfterLayout()
            }

            searchSheet.post {
                if (!isAdded || view == null) return@post
                binding.cardSearch.visibility = View.VISIBLE
                binding.cardSearch.bringToFront()
                updateCardSearchPosition(searchSheet)
            }
        }
    }

    private fun hideFriendSearchSheet() {
        hideSearchKeyboardAndClearFocus()
        if (friendSearchBottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
            friendSearchBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
        updateCardSearchAfterSheetHidden()
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
                        activeRoutePosition?.let { destination ->
                            data.self?.let { origin ->
                                updateRouteLine(origin, destination, fitBounds = false)
                                updateDirectionsOriginMarker(origin)
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
                    combine(
                        viewModel.friendsLocations,
                        viewModel.displayedFriends,
                        viewModel.isFriendSearchActive,
                        viewModel.appliedFriendSearchQuery,
                        viewModel.recentSearchedFriends
                    ) { all, displayed, active, query, recent ->
                        BottomSheetData(all, displayed, active, query, recent)
                    }.collectLatest { data ->
                        updateBottomSheetUi(data.all, data.displayed, data.isActive, data.query, data.recent)
                    }
                }

                launch {
                    viewModel.friendsLocations.collectLatest { friends ->
                        friendAdapter.submitList(friends.take(2))
                    }
                }

                launch {
                    viewModel.displayedFriends.collectLatest { friends ->
                        friendSearchAdapter.submitList(friends)
                    }
                }

                launch {
                    viewModel.isFriendSearchActive.collectLatest { isActive ->
                        handleFriendSearchState(isActive)
                    }
                }

                launch {
                    viewModel.friendSearchInput.collectLatest { query ->
                        binding.friendSearchBottomSheetLayout.btnClearFriendSearch.visibility =
                            if (query.isEmpty()) View.GONE else View.VISIBLE
                    }
                }

                launch {
                    viewModel.recentSearchedFriends.collectLatest { friends ->
                        friendSearchHistoryAdapter.submitList(friends)
                    }
                }

                // Quan sát Travel Mode
                launch {
                    viewModel.selectedTravelMode.collectLatest { mode ->
                        renderSelectedTravelMode(mode)
                    }
                }
            }
        }
    }

    private fun renderSelectedTravelMode(mode: TravelMode) = with(binding.directionTopPanel) {
        val items = mapOf(
            TravelMode.CAR to itemCar,
            TravelMode.MOTORCYCLE to itemMotorcycle,
            TravelMode.WALKING to itemWalking
        )

        val selectedBg = ContextCompat.getColor(requireContext(), R.color.directions_mode_selected_background)
        val unselectedBg = ContextCompat.getColor(requireContext(), R.color.directions_mode_unselected_background)
        val selectedTint = ContextCompat.getColor(requireContext(), R.color.directions_mode_selected_icon_tint)
        val unselectedTint = ContextCompat.getColor(requireContext(), R.color.directions_mode_unselected_icon_tint)

        items.forEach { (type, itemBinding) ->
            val isSelected = type == mode
            itemBinding.root.setCardBackgroundColor(if (isSelected) selectedBg else unselectedBg)
            itemBinding.imgMode.imageTintList = android.content.res.ColorStateList.valueOf(
                if (isSelected) selectedTint else unselectedTint
            )
            itemBinding.root.isSelected = isSelected
        }
    }

    private fun handleFriendSearchState(isActive: Boolean) = with(binding) {
        renderBottomActionState(isActive)
        
        if (!isActive) {
            hideFriendSearchSheet()
        } else {
            showFriendSearchSheet()
        }
    }

    private fun renderBottomActionState(isSearchActive: Boolean) = with(binding) {
        val selectedColor = ContextCompat.getColor(requireContext(), R.color.bg_botton_friend)
        val transparent = Color.TRANSPARENT

        if (isSearchActive) {
            search.setCardBackgroundColor(selectedColor)
            cardImgFriend.setCardBackgroundColor(transparent)
        } else {
            search.setCardBackgroundColor(transparent)
            // Friend button color is usually managed by BottomSheet state, 
            // but we ensure it matches the inactive state here if search is off.
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                cardImgFriend.setCardBackgroundColor(transparent)
            }
        }
    }

    private fun updateBottomSheetUi(
        allFriends: List<com.nhn.gps.location.phone.tracker.data.model.FriendLocation>,
        displayedFriends: List<com.nhn.gps.location.phone.tracker.data.model.FriendLocation>,
        isSearchActive: Boolean,
        query: String,
        recentHistory: List<com.nhn.gps.location.phone.tracker.data.model.FriendLocation>
    ) {
        // Update Friend List Sheet
        with(binding.friendBottomSheetLayout) {
            tvFriendCount.text = "Friends (${allFriends.size})"
            val hasFriends = allFriends.isNotEmpty()
            if (!hasFriends) {
                layoutEmpty.root.visibility = View.VISIBLE
                rvFriends.visibility = View.GONE
                btnAddFriend.visibility = View.GONE
                tvViewAll.visibility = View.GONE
            } else {
                layoutEmpty.root.visibility = View.GONE
                rvFriends.visibility = View.VISIBLE
                btnAddFriend.visibility = View.VISIBLE
                tvViewAll.visibility = if (!isSearchActive && allFriends.size > 2) View.VISIBLE else View.GONE
            }
        }

        // Update Friend Search Sheet
        with(binding.friendSearchBottomSheetLayout) {
            val hasFriends = allFriends.isNotEmpty()
            val hasQuery = query.trim().isNotEmpty()
            val noResults = isSearchActive && hasQuery && displayedFriends.isEmpty()
            
            val showHistory = isSearchActive && !hasQuery && recentHistory.isNotEmpty()
            historyContainer.visibility = if (showHistory) View.VISIBLE else View.GONE

            if (!hasFriends) {
                layoutNoFriendSearchResults.visibility = View.GONE
                rvSearchFriends.visibility = View.GONE
            } else if (noResults) {
                layoutNoFriendSearchResults.visibility = View.VISIBLE
                rvSearchFriends.visibility = View.GONE
            } else {
                layoutNoFriendSearchResults.visibility = View.GONE
                rvSearchFriends.visibility = View.VISIBLE
            }
        }
    }

    private fun showKeyboard(view: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view?.windowToken, 0)
    }

    private fun updateUiForPermission(isGranted: Boolean) = with(binding) {
        hasLocationPermissionForUi = isGranted
        renderLocationMode()
        
        if (!isGranted) {
            if (childFragmentManager.findFragmentByTag(LocationPermissionBottomSheet.TAG) == null) {
                LocationPermissionBottomSheet.newInstance().show(
                    childFragmentManager, LocationPermissionBottomSheet.TAG
                )
            }
        }
    }

    private fun renderLocationMode() = with(binding) {
        val isNormal = hasLocationPermissionForUi && locationMode == LocationMode.NORMAL
        val isDirections = hasLocationPermissionForUi && locationMode == LocationMode.DIRECTIONS
        val hasPermission = hasLocationPermissionForUi

        // Basic header (cardBack, txtTitle) controlled by groupMapUI
        groupMapUI.visibility = if (hasPermission) View.VISIBLE else View.GONE
        
        viewDim.visibility = if (hasPermission) View.GONE else View.VISIBLE
        imgAccessLocation.visibility = if (hasPermission) View.GONE else View.VISIBLE

        // Directions specific UI
        directionTopPanel.root.visibility = if (isDirections) View.VISIBLE else View.GONE
        directionBottomPanel.root.visibility = if (isDirections) View.VISIBLE else View.GONE
        
        // Marker visibility
        selfMarker?.isVisible = shouldShowSelfMarker()
        directionsOriginMarker?.isVisible = isDirections
        selectedDestinationMarker?.isVisible = isDirections
        
        // Tool visibility
        layoutTools.visibility = if (isNormal) View.VISIBLE else View.GONE
        cardSearch.visibility = if (isNormal) View.VISIBLE else View.GONE
        
        // Compass overlay
        directionOverlay?.isVisible = shouldShowDirectionOverlay()

        if (!isNormal) {
            hideFriendSheet()
            hideFriendSearchSheet()
        }

        renderFriendMarkerVisibility()

        if (isDirections) {
            txtTitle.text = "Directions"
        } else {
            txtTitle.text = getString(R.string.realtime_tracker)
        }
    }

    private fun shouldShowSelfMarker(): Boolean {
        return hasLocationPermissionForUi && locationMode == LocationMode.NORMAL
    }

    private fun shouldShowDirectionOverlay(): Boolean {
        return hasLocationPermissionForUi && locationMode == LocationMode.NORMAL && isCompassEnabled
    }

    private fun shouldShowFriendAvatarMarker(friendId: String): Boolean {
        return !(locationMode == LocationMode.DIRECTIONS && activeRouteFriendId == friendId)
    }

    private fun renderFriendMarkerVisibility() {
        friendMarkers.forEach { (id, marker) ->
            marker.isVisible = shouldShowFriendAvatarMarker(id)
        }
    }

    private fun updateDirectionsOriginMarker(position: LatLng) {
        if (locationMode != LocationMode.DIRECTIONS || googleMap == null) {
            directionsOriginMarker?.isVisible = false
            return
        }

        if (directionsOriginMarker == null) {
            directionsOriginMarker = googleMap?.addMarker(
                MarkerOptions()
                    .position(position)
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_my_location))
                    .anchor(0.5f, 0.5f)
                    .zIndex(10f)
            )
        } else {
            directionsOriginMarker?.position = position
            directionsOriginMarker?.isVisible = true
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
        val isVisible = shouldShowSelfMarker()
        
        if (selfMarker == null) {
            selfMarker = map.addMarker(
                MarkerOptions()
                    .position(location)
                    .anchor(0.5f, 1f)
                    .flat(false)
                    .zIndex(10f)
                    .visible(isVisible)
            )
            updateMarkerIcon(selfMarker!!, mainViewModel.userAvatarKey.value, avatarUrl, displayName)
        } else {
            selfMarker?.position = location
            selfMarker?.isVisible = isVisible
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
        directionOverlay?.isVisible = shouldShowDirectionOverlay()
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
            val isVisible = shouldShowFriendAvatarMarker(friend.id)
            if (existingMarker == null) {
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(position)
                        .title(friend.name)
                        .snippet(friend.id)
                        .anchor(0.5f, 1f)
                        .flat(false)
                        .rotation(0f)
                        .visible(isVisible)
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
                existingMarker.isVisible = isVisible
                val displayName = friend.name.ifBlank { "Friend" }
                if (friendAvatars[friend.id] != friend.avatarUrl || friendNames[friend.id] != displayName) {
                    updateMarkerIcon(existingMarker, friend.avatarKey, friend.avatarUrl, displayName)
                }
                friendNames[friend.id] = displayName
            }
            friendAvatars[friend.id] = friend.avatarUrl
        }
    }

    private fun updateMarkerIcon(marker: Marker, avatarKey: String?, avatarUrl: String?, label: String? = null) {
        MapMarkerHelper.updateMarkerIcon(requireContext(), marker, avatarKey, avatarUrl, style = MapMarkerHelper.MarkerStyle.DEFAULT, label = label)
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
        tryAutoZoom()

        map.setOnMarkerClickListener { marker ->
            (marker.tag as? com.nhn.gps.location.phone.tracker.data.model.FriendLocation)?.let {
                showRouteTo(
                    it.name.ifBlank { "Friend location" },
                    LatLng(it.latitude, it.longitude),
                    friendId = it.id
                )
                true
            } ?: false
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
        viewModel.closeFriendSearch()
    }

    override fun onDestroyView() {
        bottomSheetCallback?.let {
            if (::bottomSheetBehavior.isInitialized) {
                bottomSheetBehavior.removeBottomSheetCallback(it)
            }
        }
        bottomSheetCallback = null

        friendSearchBottomSheetCallback?.let {
            if (::friendSearchBottomSheetBehavior.isInitialized) {
                friendSearchBottomSheetBehavior.removeBottomSheetCallback(it)
            }
        }
        friendSearchBottomSheetCallback = null
        
        // Remove map objects before clearing the map reference
        googleMap?.apply {
            setOnMarkerClickListener(null)
            setOnMapLongClickListener(null)
            setOnCameraIdleListener(null)
            clear()
        }
        
        viewModel.closeFriendSearch()
        
        super.onDestroyView()
        
        googleMap = null
        selfMarker = null
        directionsOriginMarker = null
        selectedDestinationMarker = null
        routeLine = null
        directionOverlay = null
        
        activeRouteName = null
        activeRoutePosition = null
        activeRouteFriendId = null
        selfName = null
        
        friendMarkers.clear()
        friendAvatars.clear()
        friendNames.clear()
    }

    private fun showRouteTo(name: String, destination: LatLng, friendId: String? = null) {
        val origin = viewModel.selfLocation.value
        if (origin == null) {
            Toast.makeText(requireContext(), "Đang lấy vị trí hiện tại…", Toast.LENGTH_SHORT).show()
            return
        }

        val distanceMeters = SphericalUtil.computeDistanceBetween(origin, destination)
        if (distanceMeters < 1.0) {
            Toast.makeText(requireContext(), "Điểm đến trùng với vị trí hiện tại", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.closeFriendSearch()

        activeRouteName = name
        activeRoutePosition = destination
        activeRouteFriendId = friendId
        locationMode = LocationMode.DIRECTIONS

        selectedDestinationMarker?.remove()
        selectedDestinationMarker = googleMap?.addMarker(
            MarkerOptions()
                .position(destination)
                .title(name)
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_marker_red))
                .anchor(0.5f, 1f)
                .zIndex(11f)
        )

        updateDirectionsOriginMarker(origin)
        updateRouteLine(origin, destination, fitBounds = true)
        showDirectionPanels(distanceMeters)
        renderLocationMode()
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    private fun updateRouteLine(origin: LatLng, destination: LatLng, fitBounds: Boolean) {
        val map = googleMap ?: return
        routeLine?.remove()

        routeLine = map.addPolyline(
            PolylineOptions()
                .add(origin, destination)
                .color(Color.rgb(35, 202, 184))
                .width(9f)
                .jointType(JointType.ROUND)
                .startCap(RoundCap())
                .endCap(RoundCap())
                .geodesic(false)
                .zIndex(4f)
        )

        if (fitBounds) {
            val bounds = LatLngBounds.builder()
                .include(origin)
                .include(destination)
                .build()
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 180))
        }
    }

    private fun showDirectionPanels(distanceMeters: Double) = with(binding) {
        val distanceKm = distanceMeters / 1000.0
        val minutes = kotlin.math.max(1, kotlin.math.ceil(distanceMeters / 350.0).toInt())
        val distanceText = if (distanceKm < 10) {
            String.format(java.util.Locale.getDefault(), "%.1f km", distanceKm)
        } else {
            String.format(java.util.Locale.getDefault(), "%.0f km", distanceKm)
        }
        val routeText = "$minutes min ($distanceText)"

        directionTopPanel.tvCurrentLocation.text = "My location"
        directionTopPanel.tvDestination.text = activeRouteName ?: "Selected location"
        directionBottomPanel.tvRouteTime.text = routeText
        directionBottomPanel.tvTraffic.text = "Fastest route, light traffic"
    }

    private fun clearRoute() = with(binding) {
        routeLine?.remove()
        routeLine = null
        
        directionsOriginMarker?.remove()
        directionsOriginMarker = null
        
        selectedDestinationMarker?.remove()
        selectedDestinationMarker = null
        
        activeRouteName = null
        activeRoutePosition = null
        activeRouteFriendId = null

        viewModel.closeFriendSearch()
        
        locationMode = LocationMode.NORMAL
        renderLocationMode()
    }

    private fun startExternalNavigation() {
        val destination = activeRoutePosition ?: return
        
        // Validate coordinates
        if (!destination.latitude.isFinite() || !destination.longitude.isFinite() ||
            destination.latitude < -90.0 || destination.latitude > 90.0 ||
            destination.longitude < -180.0 || destination.longitude > 180.0) {
            Toast.makeText(requireContext(), R.string.search_location_error, Toast.LENGTH_SHORT).show()
            return
        }

        val mode = viewModel.selectedTravelMode.value.toGoogleMapsMode()
        val navigationUri = Uri.parse(
            "google.navigation:q=${destination.latitude},${destination.longitude}&mode=$mode"
        )
        val googleMapsIntent = Intent(Intent.ACTION_VIEW, navigationUri).apply {
            setPackage("com.google.android.apps.maps")
        }
        try {
            startActivity(googleMapsIntent)
        } catch (_: Exception) {
            Toast.makeText(requireContext(), R.string.google_maps_not_installed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun TravelMode.toGoogleMapsMode(): String {
        return when (this) {
            TravelMode.CAR -> "d"
            TravelMode.MOTORCYCLE -> "l"
            TravelMode.WALKING -> "w"
        }
    }

    companion object {
        fun newInstance() = LocationFragment()

        private data class DataPackage(
            val self: LatLng?,
            val friends: List<com.nhn.gps.location.phone.tracker.data.model.FriendLocation>,
            val avatar: String,
            val name: String,
            val friendsLoaded: Boolean
        )

        private data class BottomSheetData(
            val all: List<com.nhn.gps.location.phone.tracker.data.model.FriendLocation>,
            val displayed: List<com.nhn.gps.location.phone.tracker.data.model.FriendLocation>,
            val isActive: Boolean,
            val query: String,
            val recent: List<com.nhn.gps.location.phone.tracker.data.model.FriendLocation>
        )
    }
}
