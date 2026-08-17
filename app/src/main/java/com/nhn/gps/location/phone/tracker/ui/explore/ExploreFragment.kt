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
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.databinding.FragmentExploreBinding
import com.nhn.gps.location.phone.tracker.navigation.AppDestination
import com.nhn.gps.location.phone.tracker.ui.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

import androidx.recyclerview.widget.LinearLayoutManager
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.data.model.FamousPlaceModel
import android.view.inputmethod.EditorInfo
import androidx.core.widget.addTextChangedListener
import com.google.android.material.bottomsheet.BottomSheetBehavior

@AndroidEntryPoint
class ExploreFragment : BaseFragment<FragmentExploreBinding, ExploreViewModel>() {

    override val viewModel: ExploreViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels({ requireActivity() })
    
    private val markerMap = mutableMapOf<String, GlobeMarker>()
    private var bottomSheetBehavior: BottomSheetBehavior<android.widget.LinearLayout>? = null
    
    private val cardAdapter by lazy { 
        ExploreCardAdapter(
            onClick = { place -> viewModel.selectPlace(place.id) },
            onBindPhoto = { item, imageView ->
                val photoFileName = item.previewPhotos.firstOrNull()
                if (photoFileName != null) {
                    com.bumptech.glide.Glide.with(imageView)
                        .load("file:///android_asset/famous_places_images/$photoFileName")
                        .placeholder(R.drawable.ic_paris)
                        .error(R.drawable.ic_paris)
                        .centerCrop()
                        .into(imageView)
                } else {
                    imageView.setImageResource(R.drawable.ic_paris)
                }
            }
        )
    }

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentExploreBinding = FragmentExploreBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) = with(binding) {
        setupRecyclerView()
        setupGlobe()
        setupSearch()

        btnBack.setOnClickListener { handleToolbarBack() }

        btnRandom.setOnClickListener {
            viewModel.selectRandomPlace()
        }

        btnReset.setOnClickListener {
            globeView.resetView()
            viewModel.clearSelection()
            hidePlaceSheet()
        }

        ivClear.setOnClickListener { etSearch.text?.clear() }
        btnCloseSheet.setOnClickListener {
            viewModel.clearSelection()
            hidePlaceSheet()
        }
        btnDirections.setOnClickListener { openSelectedPlaceRoute() }
        btnStreetView.setOnClickListener { openSelectedPlaceInMaps(streetView = true) }
        btnCreateZone.setOnClickListener {
            navigationManager.navigateTo(AppDestination.CreateZone())
        }

        bottomSheet.visibility = View.VISIBLE
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet).apply {
            isHideable = true
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_HIDDEN
        }

        viewModel.setCategoryFilter(mainViewModel.selectedFamousCategoryId.value)
    }

    private fun setupSearch() = with(binding.etSearch) {
        addTextChangedListener {
            viewModel.onSearchQueryChanged(it.toString())
            binding.ivClear.visibility = if (it.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        
        setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                clearFocus()
                val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(windowToken, 0)
                true
            } else {
                false
            }
        }
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collectLatest { state ->
                        handleUiState(state)
                    }
                }
                launch {
                    viewModel.effect.collectLatest { effect ->
                        handleEffect(effect)
                    }
                }
            }
        }
    }

    private fun handleUiState(state: ExploreUiState) = with(binding) {
        val displayPlaces = if (state.searchedPlace != null) {
            listOf(state.searchedPlace) + state.places
        } else {
            state.places
        }
        cardAdapter.submitList(displayPlaces)

        val selectedPlace = displayPlaces.firstOrNull { it.id == state.selectedPlaceId }
        if (selectedPlace != null) {
            tvPlacesTitle.text = selectedPlace.name
            tvPlaceDescription.text = selectedPlace.descriptionText()
            showPlaceSheet()
        } else {
            hidePlaceSheet()
        }
        
        // Update markers on globe
        if (globeView.isSupported) {
            val currentPlaceIds = mutableSetOf<String>()
            
            // Handle Search Marker
            state.searchedPlace?.let { place ->
                currentPlaceIds.add(place.id)
                markerMap[place.id] = GlobeMarker(
                    id = place.id,
                    latitude = place.latitude,
                    longitude = place.longitude,
                    imageRes = place.imageRes,
                    isSelected = place.id == state.selectedPlaceId
                )
            }
            
            // Handle Famous Place Markers (Max 4 as enforced by ViewModel)
            state.places.forEach { place ->
                currentPlaceIds.add(place.id)
                // Only update if selected state or coordinates changed, otherwise reuse
                val existing = markerMap[place.id]
                if (existing == null || existing.isSelected != (place.id == state.selectedPlaceId) || 
                    existing.latitude != place.latitude || existing.longitude != place.longitude) {
                    markerMap[place.id] = GlobeMarker(
                        id = place.id,
                        latitude = place.latitude,
                        longitude = place.longitude,
                        imageRes = place.imageRes,
                        isSelected = place.id == state.selectedPlaceId
                    )
                }
            }

            // Remove markers that are no longer in the state
            val iterator = markerMap.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (!currentPlaceIds.contains(entry.key)) {
                    iterator.remove()
                }
            }

            globeView.updateMarkers(markerMap.values.toList())
        }

        // Show progress bar only when loading from scratch
        progressBar.visibility = if (state.isLoading && state.places.isEmpty()) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
        
        state.error?.let {
            // Toast or show error UI
        }
    }

    private fun handleEffect(effect: ExploreEffect) = with(binding) {
        when (effect) {
            is ExploreEffect.AnimateGlobe -> {
                if (globeView.isSupported) {
                    globeView.animateTo(effect.latitude, effect.longitude)
                }
            }
            is ExploreEffect.ScrollCarousel -> {
                rvExploreCards.smoothScrollToPosition(effect.position)
            }
            is ExploreEffect.OpenPlaceDetail -> {
                mainViewModel.setSelectedPlaceId(effect.placeId)
                navigationManager.navigateTo(com.nhn.gps.location.phone.tracker.navigation.AppDestination.PlaceDetail)
            }
        }
    }

    private fun setupGlobe() = with(binding) {
        if (globeView.isSupported) {
            globeView.visibility = android.view.View.VISIBLE
            ivBackground.visibility = android.view.View.GONE
            
            globeView.onCameraChanged = { state ->
                viewModel.onCameraChanged(state)
            }

            globeView.onMarkerClicked = { placeId ->
                viewModel.selectPlace(placeId)
            }
            
            // Initial load
            globeView.post {
                globeView.getCameraState()?.let { state ->
                    viewModel.onCameraChanged(state)
                }
            }
        } else {
            globeView.visibility = android.view.View.GONE
            ivBackground.visibility = android.view.View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        withBinding {
            if (globeView.isSupported) {
                globeView.onResume()
            }
        }
    }

    override fun onPause() {
        withBinding {
            if (globeView.isSupported) {
                globeView.onPause()
            }
        }
        super.onPause()
    }

    override fun onDestroyView() {
        bottomSheetBehavior = null
        binding.rvExploreCards.adapter = null
        markerMap.clear()
        super.onDestroyView()
    }

    private fun showPlaceSheet() {
        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun hidePlaceSheet() {
        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
    }

    private fun FamousPlaceModel.descriptionText(): String {
        val resourceId = descriptionResKey
            ?.let { resources.getIdentifier(it, "string", requireContext().packageName) }
            ?.takeIf { it != 0 }
        return resourceId?.let(resources::getString)
            ?: getString(R.string.famous_place_description_fallback, name)
    }

    private fun openSelectedPlaceInMaps(streetView: Boolean) {
        val state = viewModel.uiState.value
        val place = (listOfNotNull(state.searchedPlace) + state.places)
            .firstOrNull { it.id == state.selectedPlaceId }
            ?: return
        val uri = if (streetView) {
            Uri.parse("google.streetview:cbll=${place.latitude},${place.longitude}")
        } else {
            Uri.parse("geo:${place.latitude},${place.longitude}?q=${Uri.encode(place.name)}")
        }
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }

    private fun openSelectedPlaceRoute() {
        val state = viewModel.uiState.value
        val place = (listOfNotNull(state.searchedPlace) + state.places)
            .firstOrNull { it.id == state.selectedPlaceId }
            ?: return
        mainViewModel.openRouteOnMap(
            destinationName = place.name,
            latitude = place.latitude,
            longitude = place.longitude,
        )
    }

    private fun setupRecyclerView() = with(binding.rvExploreCards) {
        layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        adapter = cardAdapter
    }

    companion object {
        fun newInstance() = ExploreFragment()
    }
}
