package com.nhn.gps.location.phone.tracker.ui.explore

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.data.model.FamousPlaceModel
import com.nhn.gps.location.phone.tracker.databinding.FragmentPlaceDetailBinding
import com.nhn.gps.location.phone.tracker.ui.main.MainViewModel
import com.nhn.gps.location.phone.tracker.util.animateFavoriteChange
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

@AndroidEntryPoint
class PlaceDetailFragment : BaseFragment<FragmentPlaceDetailBinding, PlaceDetailViewModel>() {

    override val viewModel: PlaceDetailViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels({ requireActivity() })
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
                    viewModel.uiState.collectLatest { state ->
                        handleUiState(state)
                    }
                }
            }
        }
    }

    private fun handleUiState(state: PlaceDetailUiState) = with(binding) {
        progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

        state.place?.let { setupContent(it) }

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
    }

    private fun onFavoriteClicked() {
        viewModel.toggleFavorite()
    }

    private fun setupContent(place: FamousPlaceModel) = with(binding) {
        tvPlaceName.text = place.name
        tvPlaceLocation.text = place.location
        tvDistance.text = String.format(Locale.getDefault(), "%.1f km", place.distanceKm)
        tvHeaderRating.text = String.format(Locale.getDefault(), "%.1f (%d reviews)", place.rating, place.reviewCount)

        // Resolve description from res key or fallback to address/location
        val resId = place.descriptionResKey?.let {
            resources.getIdentifier(it, "string", requireContext().packageName)
        } ?: 0

        tvDescription.text = if (resId != 0) {
            getString(resId)
        } else {
            place.address ?: place.location
        }

        // Hero Attribution (not applicable for local assets)
        tvHeroAttribution.visibility = View.GONE

        ivHero.loadFamousPlaceImage(place)

        // Update visit info cards
        with(cardVisitTime) {
            val status = if (place.isOpen == true) "Open" else if (place.isOpen == false) "Closed" else "Unknown"
            tvInfoValue.text = status
            tvInfoLabel.text = "Status"
            tvOpenStatus.text = if (place.isOpen == true) "Open now" else status
        }

        with(cardHeight) {
            tvInfoValue.text = place.category
            tvInfoLabel.text = "Type"
        }

        with(cardRating) {
            tvInfoValue.text = String.format(Locale.getDefault(), "%.1f", place.rating)
            tvInfoLabel.text = "Rating"
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
        btnDirections.tvActionLabel.text = "Directions"
        btnStreetView.tvActionLabel.text = "Street view"
        btnSave.tvActionLabel.text = "Save"
        btnCreateZone.tvActionLabel.text = "Create zone"
    }

    private fun setupNearbyListeners() = with(binding) {
        nearbyRestaurants.tvNearbyName.text = "Restaurants"
        nearbyRestaurants.tvNearbyCount.text = ""
        nearbyRestaurants.root.setOnClickListener { openNearbyInGoogleMaps("restaurants") }

        nearbyHotels.tvNearbyName.text = "Hotels"
        nearbyHotels.tvNearbyCount.text = ""
        nearbyHotels.root.setOnClickListener { openNearbyInGoogleMaps("hotels") }

        nearbyCafes.tvNearbyName.text = "Cafes"
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
