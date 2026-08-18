package com.nhn.gps.location.phone.tracker.ui.explore

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.data.model.FamousPlaceModel
import com.nhn.gps.location.phone.tracker.databinding.FragmentPlaceDetailBinding
import com.nhn.gps.location.phone.tracker.ui.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

@AndroidEntryPoint
class PlaceDetailFragment : BaseFragment<FragmentPlaceDetailBinding, PlaceDetailViewModel>() {

    override val viewModel: PlaceDetailViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels({ requireActivity() })

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentPlaceDetailBinding = FragmentPlaceDetailBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) = with(binding) {
        setupHeader()
        setupActions()
        setupNearby()
        
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
        
        state.error?.let {
            // Show error
        }
    }

    private fun setupHeader() = with(binding) {
        btnBack.setOnClickListener { handleToolbarBack() }
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
            ivInfoIcon.setImageResource(R.drawable.ic_last_seen)
            val status = if (place.isOpen == true) "Open" else if (place.isOpen == false) "Closed" else "Unknown"
            tvInfoValue.text = status
            tvInfoLabel.text = "Status"
            tvOpenStatus.text = if (place.isOpen == true) "Open now" else status
        }

        with(cardHeight) {
            ivInfoIcon.setImageResource(R.drawable.ic_polygon)
            tvInfoValue.text = place.category
            tvInfoLabel.text = "Type"
        }

        with(cardRating) {
            ivInfoIcon.setImageResource(R.drawable.ic_famous_home)
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
            navigationManager.navigateTo(com.nhn.gps.location.phone.tracker.navigation.AppDestination.CreateZone)
        }
    }

    private fun setupActions() = with(binding) {
        with(btnDirections) {
            ivActionIcon.setImageResource(R.drawable.ic_location_direction)
            tvActionLabel.text = "Directions"
        }
        with(btnStreetView) {
            ivActionIcon.setImageResource(R.drawable.ic_street_home)
            tvActionLabel.text = "Street view"
        }
        with(btnSave) {
            ivActionIcon.setImageResource(R.drawable.ic_famous_home)
            tvActionLabel.text = "Save"
        }
        with(btnCreateZone) {
            ivActionIcon.setImageResource(R.drawable.ic_create_zone)
            tvActionLabel.text = "Create zone"
        }
    }

    private fun setupNearby() = with(binding) {
        with(nearbyRestaurants) {
            ivNearbyIcon.setImageResource(R.drawable.ic_location_home)
            tvNearbyName.text = "Restaurants"
            tvNearbyCount.text = ""
        }
        with(nearbyHotels) {
            ivNearbyIcon.setImageResource(R.drawable.ic_home_zone)
            tvNearbyName.text = "Hotels"
            tvNearbyCount.text = ""
        }
        with(nearbyCafes) {
            ivNearbyIcon.setImageResource(R.drawable.ic_bag_zone)
            tvNearbyName.text = "Cafes"
            tvNearbyCount.text = ""
        }
        with(nearbyAtms) {
            ivNearbyIcon.setImageResource(R.drawable.ic_qr_code)
            tvNearbyName.text = "ATMs"
            tvNearbyCount.text = ""
        }
    }

    companion object {
        fun newInstance() = PlaceDetailFragment()
    }
}
