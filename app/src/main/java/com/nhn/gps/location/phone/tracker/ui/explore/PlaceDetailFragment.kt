package com.nhn.gps.location.phone.tracker.ui.explore

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.data.model.FamousPlaceModel
import com.nhn.gps.location.phone.tracker.databinding.FragmentPlaceDetailBinding
import com.nhn.gps.location.phone.tracker.ui.location.LocationViewModel
import com.nhn.gps.location.phone.tracker.ui.main.MainViewModel
import com.nhn.gps.location.phone.tracker.util.animateFavoriteChange
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

@AndroidEntryPoint
class PlaceDetailFragment : BaseFragment<FragmentPlaceDetailBinding, PlaceDetailViewModel>() {

    override val viewModel: PlaceDetailViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private val locationViewModel: LocationViewModel by activityViewModels()
    private var lastRenderedFavorite: Boolean? = null

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentPlaceDetailBinding = FragmentPlaceDetailBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) = with(binding) {
        setupHeader()
        setupActions()
        setupNearbyListeners()

        btnExploreNow.setOnClickListener {
            navigationManager.navigateTo(com.nhn.gps.location.phone.tracker.navigation.AppDestination.Explore)
        }
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    mainViewModel.selectedPlaceId.collectLatest { id ->
                        id?.let { viewModel.loadPlaceDetail(it) }
                    }
                }
                launch {
                    locationViewModel.selfLocation.collectLatest { location ->
                        viewModel.updateUserLocation(location)
                    }
                }
                launch {
                    viewModel.uiState.collectLatest { state ->
                        handleUiState(state)
                    }
                }
            }
        }
    }

    private fun handleUiState(state: PlaceDetailUiState) = with(binding) {
        progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

        state.place?.let { setupContent(it, state.distanceMeters) }

        renderFavorite(state.isFavorite)

        state.error?.let {
            // Show error
        }
    }

    private fun renderFavorite(isFavorite: Boolean) = with(binding) {
        val headerIcon = if (isFavorite) R.drawable.ic_love_fill else R.drawable.ic_love
        val saveIcon = if (isFavorite) R.drawable.ic_love_red_famous else R.drawable.ic_love
        val saveText = if (isFavorite) R.string.saved_place else R.string.save_place

        val shouldAnimate = lastRenderedFavorite != null && lastRenderedFavorite != isFavorite

        btnFavorite.animateFavoriteChange(headerIcon, shouldAnimate)
        btnSave.ivActionIcon.animateFavoriteChange(saveIcon, shouldAnimate)
        btnSave.tvActionLabel.setText(saveText)

        val contentDesc = getString(if (isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites)
        btnFavorite.contentDescription = contentDesc
        btnSave.root.contentDescription = contentDesc

        lastRenderedFavorite = isFavorite
    }

    private fun setupHeader() = with(binding) {
        btnBack.setOnClickListener { handleToolbarBack() }
        btnFavorite.setOnClickListener { onFavoriteClicked() }
        btnSave.root.setOnClickListener { onFavoriteClicked() }
        btnShare.setOnClickListener { sharePlace() }
    }

    private fun sharePlace() {
        val place = viewModel.uiState.value.place ?: return
        val shareText = "Explore ${place.name} on GPS Location Phone Tracker!\n" +
                "Location: https://www.google.com/maps/search/?api=1&query=${place.latitude},${place.longitude}"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(intent, "Share place"))
    }

    private fun onFavoriteClicked() {
        viewModel.toggleFavorite()
    }

    private fun setupContent(place: FamousPlaceModel, distanceMeters: Double?) = with(binding) {
        tvPlaceName.text = place.name
        tvPlaceLocation.text = place.address ?: place.location.takeIf { it.isNotBlank() } ?: getString(R.string.location_unavailable)
        
        tvDistance.text = if (distanceMeters != null) {
            if (distanceMeters < 1000) {
                getString(R.string.route_distance_meters, distanceMeters.toInt())
            } else {
                getString(R.string.route_distance_kilometers_decimal, distanceMeters / 1000.0)
            }
        } else {
            getString(R.string.distance_unavailable)
        }

        tvHeaderRating.text = if (place.rating > 0) {
            val reviews = getString(R.string.reviews_count, place.reviewCountLong ?: place.reviewCount.toLong())
            getString(R.string.rating_with_reviews, place.rating, reviews)
        } else {
            getString(R.string.not_rated)
        }

        // Resolve description from res key or fallback to address/location
        val resId = place.descriptionResKey?.let {
            resources.getIdentifier(it, "string", requireContext().packageName)
        } ?: 0

        tvDescription.text = if (resId != 0) {
            getString(resId)
        } else {
            place.address ?: place.location.takeIf { it.isNotBlank() } ?: ""
        }

        // Hero Attribution (not applicable for local assets)
        tvHeroAttribution.visibility = View.GONE

        ivHero.loadFamousPlaceImage(place)

        // Update visit info cards
        with(cardVisitTime) {
            tvInfoLabel.text = getString(R.string.visit_time)
            tvInfoValue.text = place.estimatedVisitMinutes?.let { 
                getString(R.string.visit_minutes, it) 
            } ?: getString(R.string.info_unavailable)
            
            val isOpen = place.isOpen
            tvOpenStatus.visibility = if (isOpen != null) View.VISIBLE else View.GONE
            tvOpenStatus.text = if (isOpen == true) getString(R.string.open_now) else getString(R.string.closed)
            tvOpenStatus.setTextColor(resources.getColor(if (isOpen == true) R.color.color_2e7d32 else R.color.bg_dialog_confirm, null))
        }

        with(cardHeight) {
            tvInfoLabel.text = getString(R.string.height)
            tvInfoValue.text = place.elevationMeters?.let { 
                String.format(Locale.getDefault(), "%.0f m", it) 
            } ?: getString(R.string.elevation_unavailable)
        }

        with(cardRating) {
            tvInfoLabel.text = getString(R.string.rating)
            tvInfoValue.text = if (place.rating > 0) {
                String.format(Locale.getDefault(), "%.1f", place.rating)
            } else {
                getString(R.string.not_rated)
            }
        }

        // Setup Intents
        btnDirections.root.setOnClickListener {
            mainViewModel.openRouteOnMap(
                destinationName = place.name,
                latitude = place.latitude,
                longitude = place.longitude,
            )
        }

        btnStreetView.root.setOnClickListener {
            val gmmIntentUri = Uri.parse("google.streetview:cbll=${place.latitude},${place.longitude}")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")
            startActivity(mapIntent)
        }

        btnCreateZone.root.setOnClickListener {
            if (isValidCoordinate(place.latitude, place.longitude)) {
                navigationManager.navigateTo(
                    com.nhn.gps.location.phone.tracker.navigation.AppDestination.CreateZone(
                        initialLatitude = place.latitude,
                        initialLongitude = place.longitude,
                        initialAddress = place.address ?: place.location,
                        initialPlaceName = place.name
                    )
                )
            } else {
                Toast.makeText(requireContext(), R.string.place_location_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isValidCoordinate(lat: Double, lng: Double): Boolean {
        return lat in -90.0..90.0 && lng in -180.0..180.0 && lat != 0.0 && lng != 0.0
    }

    override fun onDestroyView() {
        lastRenderedFavorite = null
        super.onDestroyView()
    }

    private fun setupActions() = with(binding) {
        btnDirections.tvActionLabel.text = getString(R.string.directions)
        btnStreetView.tvActionLabel.text = getString(R.string.street_view)
        btnSave.tvActionLabel.text = getString(R.string.save_place)
        btnCreateZone.tvActionLabel.text = getString(R.string.create_zone)
    }

    private fun setupNearbyListeners() = with(binding) {
        nearbyRestaurants.tvNearbyName.text = getString(R.string.category_romantic) // Use existing or add more
        nearbyRestaurants.tvNearbyCount.text = ""
        nearbyRestaurants.root.setOnClickListener { openNearbyInGoogleMaps("restaurants") }

        nearbyHotels.tvNearbyName.text = getString(R.string.category_holiday)
        nearbyHotels.tvNearbyCount.text = ""
        nearbyHotels.root.setOnClickListener { openNearbyInGoogleMaps("hotels") }

        nearbyCafes.tvNearbyName.text = getString(R.string.category_clubs)
        nearbyCafes.tvNearbyCount.text = ""
        nearbyCafes.root.setOnClickListener { openNearbyInGoogleMaps("cafes") }

        nearbyAtms.tvNearbyName.text = "ATMs"
        nearbyAtms.tvNearbyCount.text = ""
        nearbyAtms.root.setOnClickListener { openNearbyInGoogleMaps("ATMs") }
    }

    private fun openNearbyInGoogleMaps(category: String) {
        val place = viewModel.uiState.value.place ?: return
        val lat = place.latitude
        val lng = place.longitude
        val name = place.name

        if (!isValidCoordinate(lat, lng)) {
            Toast.makeText(requireContext(), R.string.place_location_unavailable, Toast.LENGTH_SHORT).show()
            return
        }

        val query = "$category near $name"
        val encodedQuery = Uri.encode(query)

        // Use Locale.US to ensure dot decimal separator
        val geoUri = Uri.parse(String.format(Locale.US, "geo:%f,%f?q=%s", lat, lng, encodedQuery))
        val intent = Intent(Intent.ACTION_VIEW, geoUri).apply {
            setPackage("com.google.android.apps.maps")
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback 1: Try without setPackage (for other map apps)
            try {
                val genericIntent = Intent(Intent.ACTION_VIEW, geoUri)
                startActivity(genericIntent)
            } catch (e2: Exception) {
                // Fallback 2: Google Maps Web
                try {
                    val webUri = Uri.parse(String.format(Locale.US, "https://www.google.com/maps/search/?api=1&query=%s", encodedQuery))
                    startActivity(Intent(Intent.ACTION_VIEW, webUri))
                } catch (e3: Exception) {
                    Toast.makeText(requireContext(), R.string.unable_to_open_maps, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        fun newInstance() = PlaceDetailFragment()
    }
}
