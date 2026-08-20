package com.nhn.gps.location.phone.tracker.ui.explore

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
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
import com.nhn.gps.location.phone.tracker.navigation.AppDestination
import com.nhn.gps.location.phone.tracker.ui.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FamousPlaceFragment : BaseFragment<FragmentFamousPlaceListBinding, FamousPlaceViewModel>() {

    override val viewModel: FamousPlaceViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels({ requireActivity() })

    private val categoryAdapter = FamousCategoryAdapter { category ->
        viewModel.onCategorySelected(category.filterName)
    }

    private val placesAdapter by lazy {
        FamousPlaceAdapter(
            onClick = { place -> viewModel.onPlaceClicked(place) },
            onFavoriteClick = { id -> viewModel.toggleFavorite(id) },
            onBindPhoto = { item, imageView ->
                imageView.loadFamousPlaceImage(item)
            }
        )
    }

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentFamousPlaceListBinding =
        FragmentFamousPlaceListBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) = with(binding) {
        header.btnBack.setOnClickListener { handleToolbarBack() }
        header.tvTitle.setText(R.string.famous_place)
        header.btnRight.setOnClickListener {
            // TODO: Header favorite chưa có flow riêng. Hiện tại có thể lọc danh sách yêu thích nếu cần.
        }

        etSearch.addTextChangedListener {
            viewModel.onSearchQueryChanged(it.toString())
        }

        btnSeeMap.setOnClickListener {
            navigationManager.navigateTo(AppDestination.Explore)
        }

        rvCategories.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = categoryAdapter
            itemAnimator = null
        }

        rvFamousPlaces.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = placesAdapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0) return
                    val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                    val totalItemCount = layoutManager.itemCount
                    val lastVisibleItem = layoutManager.findLastVisibleItemPosition()

                    if (
                        totalItemCount > 0 &&
                        lastVisibleItem != RecyclerView.NO_POSITION &&
                        lastVisibleItem >= totalItemCount - 1
                    ) {
                        viewModel.loadMore()
                    }
                }
            })
        }

        btnSeeAll.setOnClickListener {
            etSearch.text?.clear()
            viewModel.onCategorySelected("All")
        }

        categoryAdapter.submit(CATEGORIES)
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
                        when (effect) {
                            is FamousPlaceEffect.OpenPlaceDetail -> {
                                mainViewModel.setSelectedPlaceId(effect.placeId)
                                navigationManager.navigateTo(AppDestination.PlaceDetail)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleUiState(state: FamousPlaceUiState) = with(binding) {
        progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        
        categoryAdapter.setSelected(state.selectedCategory)
        
        featuredContainer.visibility = if (state.featuredPlace != null) View.VISIBLE else View.GONE
        state.featuredPlace?.let { renderFeaturedPlace(it) }

        trendingHeader.visibility = if (state.trendingPlaces.isNotEmpty()) View.VISIBLE else View.GONE
        placesAdapter.submitList(state.trendingPlaces) {
            if (state.isReset) {
                rvFamousPlaces.scrollToPosition(0)
            }
        }
    }

    private fun renderFeaturedPlace(place: FamousPlaceModel) = with(binding.featuredItem) {
        tvFeaturedName.text = place.name
        tvFeaturedLocation.text = place.location
        tvFeaturedRating.text = String.format(java.util.Locale.getDefault(), "%.1f", place.rating)
        tvKm.text = String.format(java.util.Locale.getDefault(), "%.1f km", place.distanceKm)
        
        ivFeatured.loadFamousPlaceImage(place)

        val iconRes = if (place.isFavorite) R.drawable.ic_love_fill else R.drawable.ic_love_white
        btnFavorite.setImageResource(iconRes)

        root.setOnClickListener { viewModel.onPlaceClicked(place) }
        btnFavorite.setOnClickListener { viewModel.toggleFavorite(place.id) }
    }

    override fun onDestroyView() {
        binding.rvCategories.adapter = null
        binding.rvFamousPlaces.adapter = null
        super.onDestroyView()
    }

    data class FamousCategory(
        val id: Int,
        @StringRes val nameRes: Int,
        val filterName: String,
    )

    private class FamousCategoryAdapter(
        private val onClick: (FamousCategory) -> Unit,
    ) : RecyclerView.Adapter<FamousCategoryAdapter.CategoryViewHolder>() {

        private val items = mutableListOf<FamousCategory>()
        private var selectedFilterName: String = "All"

        fun submit(categories: List<FamousCategory>) {
            items.clear()
            items.addAll(categories)
            notifyDataSetChanged()
        }

        fun setSelected(filterName: String) {
            if (selectedFilterName == filterName) return
            selectedFilterName = filterName
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder =
            CategoryViewHolder(
                ItemCategoryFilterBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            )

        override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) =
            holder.bind(items[position])

        override fun getItemCount(): Int = items.size

        inner class CategoryViewHolder(
            private val binding: ItemCategoryFilterBinding,
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(category: FamousCategory) = with(binding) {
                tvCategoryName.setText(category.nameRes)
                val isSelected = category.filterName == selectedFilterName
                
                val context = root.context
                val bgColor = if (isSelected) context.getColor(R.color.color_e8f5e9) else context.getColor(R.color.white)
                val strokeColor = if (isSelected) context.getColor(R.color.bg_switch_permission) else context.getColor(R.color.color_e0e0e0)
                val textColor = if (isSelected) context.getColor(R.color.bg_switch_permission) else context.getColor(R.color.text_secondary)

                cardCategory.setCardBackgroundColor(bgColor)
                cardCategory.strokeColor = strokeColor
                tvCategoryName.setTextColor(textColor)

                root.setOnClickListener { onClick(category) }
            }
        }
    }

    companion object {
        private val CATEGORIES = listOf(
            FamousCategory(0, R.string.all, "All"),
            FamousCategory(1, R.string.famous_place, "Romantic"), // Mapping "Famous" to "Romantic" as per initial data mapping logic if needed, or just "All"
            FamousCategory(7, R.string.category_surf, "Surf"), // Beach
            FamousCategory(12, R.string.category_destinations, "Cities"), // City
            FamousCategory(4, R.string.category_nature, "Nature"),
        )

        fun newInstance() = FamousPlaceFragment()
    }
}
