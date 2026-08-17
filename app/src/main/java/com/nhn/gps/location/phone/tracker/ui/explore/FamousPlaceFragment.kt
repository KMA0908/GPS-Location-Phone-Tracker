package com.nhn.gps.location.phone.tracker.ui.explore

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.data.model.FamousPlaceModel
import com.nhn.gps.location.phone.tracker.databinding.FragmentFamousPlaceListBinding
import com.nhn.gps.location.phone.tracker.databinding.ItemCategoryFilterBinding
import com.nhn.gps.location.phone.tracker.ui.main.MainViewModel
import com.nhn.gps.location.phone.tracker.util.animateFavoriteChange
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

import java.util.Locale

@AndroidEntryPoint
class FamousPlaceFragment : BaseFragment<FragmentFamousPlaceListBinding, FamousPlaceViewModel>() {

    override val viewModel: FamousPlaceViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels({ requireActivity() })

    private val trendingAdapter by lazy {
        FamousPlaceAdapter(
            onClick = { viewModel.onPlaceClicked(it) },
            onFavoriteClick = { placeId -> viewModel.toggleFavorite(placeId) },
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
    
    private val categories = listOf(
        "All", "Romantic", "Theme Parks", "Mountains", "Nature", 
        "Dangerous", "Mysterious", "Surf", "Ghost Towns", 
        "Film Locations", "Extreme Weather", "Family", "Cities", 
        "Clubs", "Nightlife"
    )

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentFamousPlaceListBinding = FragmentFamousPlaceListBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) = with(binding) {
        setupHeader()
        setupSearch()
        setupCategories()
        setupTrendingList()
        setupBottomNav()
        setupScrollListener()
        
        btnSeeMap.setOnClickListener {
            navigationManager.navigateTo(com.nhn.gps.location.phone.tracker.navigation.AppDestination.Explore)
        }
    }

    private fun setupScrollListener() = with(binding) {
        scrollView.setOnScrollChangeListener { v: androidx.core.widget.NestedScrollView, _, scrollY, _, _ ->
            val child = v.getChildAt(0)
            if (child != null) {
                val childHeight = child.measuredHeight
                val scrollHeight = v.measuredHeight
                
                // If scrolled near bottom (within 200 pixels)
                if (childHeight > 0 && scrollY + scrollHeight >= childHeight - 200) {
                    viewModel.loadMore()
                }
            }
        }
    }

    private fun setupHeader() = with(binding.header) {
        tvTitle.text = "Famous place"
        imgRight.setImageResource(R.drawable.ic_love)
        btnBack.setOnClickListener { navigationManager.navigateBack() }
    }

    private fun setupSearch() = with(binding) {
        val etSearch = root.findViewById<android.widget.EditText>(R.id.etSearch)
        etSearch?.addTextChangedListener {
            viewModel.onSearchQueryChanged(it.toString())
        }
    }

    private fun setupCategories() = with(binding.rvCategories) {
        layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        adapter = CategoryAdapter(categories) { category ->
            viewModel.onCategorySelected(category)
        }
    }

    private fun setupFeaturedCard(place: FamousPlaceModel?) = with(binding.featuredCard) {
        if (place == null) {
            root.visibility = android.view.View.GONE
            return@with
        }
        root.visibility = android.view.View.VISIBLE
        
        val photoFileName = place.previewPhotos.firstOrNull()
        if (photoFileName != null) {
            com.bumptech.glide.Glide.with(ivFeatured)
                .load("file:///android_asset/famous_places_images/$photoFileName")
                .placeholder(R.drawable.ic_paris)
                .error(R.drawable.ic_paris)
                .centerCrop()
                .into(ivFeatured)
        } else {
            ivFeatured.setImageResource(R.drawable.ic_paris)
        }

        tvFeaturedName.text = place.name
        tvFeaturedLocation.text = place.location
        tvFeaturedRating.text = String.format(Locale.getDefault(), "%.1f", place.rating)
        
        // Update favorite icon for featured card
        val favIcon = if (place.isFavorite) R.drawable.ic_love_fill else R.drawable.ic_love_white
        val oldTag = btnFavorite.tag
        if (oldTag != favIcon) {
            btnFavorite.animateFavoriteChange(favIcon, oldTag != null)
            btnFavorite.tag = favIcon
        }
        
        btnFavorite.setOnClickListener {
            viewModel.toggleFavorite(place.id)
        }

        root.setOnClickListener {
            viewModel.onPlaceClicked(place)
        }
    }

    private fun setupTrendingList() = with(binding.rvTrending) {
        layoutManager = LinearLayoutManager(context)
        adapter = trendingAdapter
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
                launch {
                    navigationManager.currentDestination.collectLatest { destination ->
                        val selectedId = when (destination) {
                            is com.nhn.gps.location.phone.tracker.navigation.AppDestination.Home -> R.id.navHome
                            is com.nhn.gps.location.phone.tracker.navigation.AppDestination.FamousPlace -> R.id.navMap
                            is com.nhn.gps.location.phone.tracker.navigation.AppDestination.MyZones -> R.id.navShield
                            else -> null
                        }
                        selectedId?.let { updateSelectedItem(it) }
                    }
                }
            }
        }
    }

    private fun updateSelectedItem(selectedId: Int) = with(binding.bottomNavigationCustom) {
        val navItems = mapOf(
            R.id.navHome to imgHome,
            R.id.navMap to imgMap,
            R.id.navShield to imgShield,
            R.id.navProfile to imgProfile
        )

        navItems.forEach { (id, imageView) ->
            if (id == selectedId) {
                imageView.setBackgroundResource(R.drawable.bg_bottom_nav_selected)
            } else {
                imageView.background = null
            }
        }
    }

    private fun handleUiState(state: FamousPlaceUiState) = with(binding) {
        setupFeaturedCard(state.featuredPlace)
        trendingAdapter.submitList(state.trendingPlaces)
        rvCategories.adapter?.notifyDataSetChanged()
        
        progressBar.visibility = if (state.isLoading) android.view.View.VISIBLE else android.view.View.GONE

        state.error?.let {
            android.widget.Toast.makeText(requireContext(), it, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleEffect(effect: FamousPlaceEffect) {
        when (effect) {
            is FamousPlaceEffect.OpenPlaceDetail -> {
                mainViewModel.setSelectedPlaceId(effect.placeId)
                navigationManager.navigateTo(com.nhn.gps.location.phone.tracker.navigation.AppDestination.PlaceDetail)
            }
        }
    }

    private fun setupBottomNav() = with(binding.bottomNavigationCustom) {
        navHome.setOnClickListener {
            navigationManager.navigateTo(com.nhn.gps.location.phone.tracker.navigation.AppDestination.Home)
        }
        navMap.setOnClickListener {
            // Already here (FamousPlace tab)
        }
        navLocation.setOnClickListener {
            mainViewModel.openMap()
        }
        navShield.setOnClickListener {
            navigationManager.navigateTo(com.nhn.gps.location.phone.tracker.navigation.AppDestination.MyZones)
        }
        navProfile.setOnClickListener {
            mainViewModel.openSettings()
        }
    }

    inner class CategoryAdapter(
        private val items: List<String>,
        private val onItemClick: (String) -> Unit
    ) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemCategoryFilterBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(ItemCategoryFilterBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val isSelected = item == viewModel.uiState.value.selectedCategory
            val context = holder.itemView.context
            
            holder.binding.tvCategoryName.text = item
            if (isSelected) {
                holder.binding.cardCategory.setCardBackgroundColor(
                    ContextCompat.getColor(context, R.color.bg_switch_permission)
                )
                holder.binding.tvCategoryName.setTextColor(Color.WHITE)
                holder.binding.cardCategory.strokeWidth = 0
            } else {
                holder.binding.cardCategory.setCardBackgroundColor(Color.WHITE)
                holder.binding.tvCategoryName.setTextColor(
                    ContextCompat.getColor(context, R.color.text_secondary)
                )
                holder.binding.cardCategory.strokeWidth = 1
            }
            
            holder.binding.root.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount() = items.size
    }

    companion object {
        fun newInstance() = FamousPlaceFragment()
    }
}
