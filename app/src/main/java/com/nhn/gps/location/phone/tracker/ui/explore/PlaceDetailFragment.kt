package com.nhn.gps.location.phone.tracker.ui.explore

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.databinding.FragmentPlaceDetailBinding
import com.nhn.gps.location.phone.tracker.ui.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

import com.nhn.gps.location.phone.tracker.R

@AndroidEntryPoint
class PlaceDetailFragment : BaseFragment<FragmentPlaceDetailBinding, MainViewModel>() {

    override val viewModel: MainViewModel by viewModels({ requireActivity() })

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentPlaceDetailBinding = FragmentPlaceDetailBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) = with(binding) {
        setupHeader()
        setupContent()
        setupActions()
        setupNearby()
        
        btnExploreNow.setOnClickListener {
            // Action
        }
    }

    private fun setupHeader() = with(binding) {
        btnBack.setOnClickListener { handleToolbarBack() }
        tvHeaderRating.text = "4.7 (493k reviews)"
    }

    private fun setupContent() = with(binding) {
        tvPlaceName.text = "Eiffel Tower"
        tvPlaceLocation.text = "Paris, France"
        tvDistance.text = "120 km"
        tvDescription.text = "The Eiffel Tower is a wrought-iron lattice tower on the Champ de Mars in Paris, France. It was named after the engineer Gustave Eiffel, whose company designed and built the tower."

        with(cardVisitTime) {
            ivInfoIcon.setImageResource(R.drawable.ic_last_seen)
            tvInfoValue.text = "2h"
            tvInfoLabel.text = "Visit time"
        }

        with(cardHeight) {
            ivInfoIcon.setImageResource(R.drawable.ic_polygon)
            tvInfoValue.text = "324"
            tvInfoLabel.text = "Height"
        }

        with(cardRating) {
            ivInfoIcon.setImageResource(R.drawable.ic_famous_home)
            tvInfoValue.text = "4.9"
            tvInfoLabel.text = "Rating"
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
            tvNearbyCount.text = "128 nearby"
        }
        with(nearbyHotels) {
            ivNearbyIcon.setImageResource(R.drawable.ic_home_zone)
            tvNearbyName.text = "Hotels"
            tvNearbyCount.text = "45 nearby"
        }
        with(nearbyCafes) {
            ivNearbyIcon.setImageResource(R.drawable.ic_bag_zone)
            tvNearbyName.text = "Cafes"
            tvNearbyCount.text = "86 nearby"
        }
        with(nearbyAtms) {
            ivNearbyIcon.setImageResource(R.drawable.ic_qr_code)
            tvNearbyName.text = "ATMs"
            tvNearbyCount.text = "32 nearby"
        }
    }

    companion object {
        fun newInstance() = PlaceDetailFragment()
    }
}
