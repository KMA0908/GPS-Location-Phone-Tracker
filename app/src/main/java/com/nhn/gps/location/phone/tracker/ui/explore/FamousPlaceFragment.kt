package com.nhn.gps.location.phone.tracker.ui.explore

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.data.model.FamousPlaceModel
import com.nhn.gps.location.phone.tracker.databinding.FragmentFamousPlaceListBinding
import com.nhn.gps.location.phone.tracker.databinding.ItemCategoryFilterBinding
import com.nhn.gps.location.phone.tracker.ui.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FamousPlaceFragment : BaseFragment<FragmentFamousPlaceListBinding, MainViewModel>() {

    override val viewModel: MainViewModel by viewModels({ requireActivity() })
    private val trendingAdapter by lazy {
        FamousPlaceAdapter(
            onClick = { navigationManager.navigateTo(com.nhn.gps.location.phone.tracker.navigation.AppDestination.PlaceDetail) },
            onFavoriteClick = {}
        )
    }
    
    private var selectedCategoryIndex = 0
    private val categories = listOf("All", "Famous", "Beach", "City", "Nature")

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentFamousPlaceListBinding = FragmentFamousPlaceListBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) = with(binding) {
        setupHeader()
        setupCategories()
        setupFeaturedCard()
        setupTrendingList()
        setupBottomNav()
        
        btnSeeMap.setOnClickListener {
            navigationManager.navigateTo(com.nhn.gps.location.phone.tracker.navigation.AppDestination.Explore)
        }
    }

    private fun setupHeader() = with(binding.header) {
        tvTitle.text = "Famous place"
        imgRight.setImageResource(R.drawable.ic_famous_home)
        btnBack.setOnClickListener { navigationManager.navigateBack() }
    }

    private fun setupCategories() = with(binding.rvCategories) {
        layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        val categoryAdapter = CategoryAdapter(categories) { index ->
            selectedCategoryIndex = index
            adapter?.notifyDataSetChanged()
        }
        adapter = categoryAdapter
    }

    private fun setupFeaturedCard() = with(binding.featuredCard) {
        ivFeatured.setImageResource(R.drawable.ic_paris)
        tvFeaturedName.text = "Bali Island"
        tvFeaturedLocation.text = "Indonesia"
        tvFeaturedRating.text = "4.9"
        
        root.setOnClickListener {
            navigationManager.navigateTo(com.nhn.gps.location.phone.tracker.navigation.AppDestination.PlaceDetail)
        }
    }

    private fun setupTrendingList() = with(binding.rvTrending) {
        layoutManager = LinearLayoutManager(context)
        adapter = trendingAdapter
        trendingAdapter.submitList(getMockData())
    }

    private fun setupBottomNav() = with(binding.bottomNavigationCustom) {
        imgHome.background = null
        imgShield.setBackgroundResource(R.drawable.bg_bottom_nav_selected)
        
        navHome.setOnClickListener { navigationManager.navigateTo(com.nhn.gps.location.phone.tracker.navigation.AppDestination.Home) }
        navShield.setOnClickListener { /* Current */ }
    }

    private fun getMockData() = listOf(
        FamousPlaceModel("1", "Eiffel Tower", "Paris, France", R.drawable.ic_paris, 4.8f, 1200, 2.5, "Famous"),
        FamousPlaceModel("2", "Colosseum", "Rome, Italy", R.drawable.ic_paris, 4.7f, 950, 5.0, "Famous"),
        FamousPlaceModel("3", "Santorini", "Greece", R.drawable.ic_paris, 4.9f, 800, 10.2, "Beach")
    )

    inner class CategoryAdapter(
        private val items: List<String>,
        private val onItemClick: (Int) -> Unit
    ) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemCategoryFilterBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(ItemCategoryFilterBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val isSelected = position == selectedCategoryIndex
            
            holder.binding.tvCategoryName.text = item
            if (isSelected) {
                holder.binding.cardCategory.setCardBackgroundColor(Color.parseColor("#5B5CE2"))
                holder.binding.tvCategoryName.setTextColor(Color.WHITE)
                holder.binding.cardCategory.strokeWidth = 0
            } else {
                holder.binding.cardCategory.setCardBackgroundColor(Color.WHITE)
                holder.binding.tvCategoryName.setTextColor(Color.parseColor("#62626E"))
                holder.binding.cardCategory.strokeWidth = 1
            }
            
            holder.binding.root.setOnClickListener { onItemClick(position) }
        }

        override fun getItemCount() = items.size
    }

    companion object {
        fun newInstance() = FamousPlaceFragment()
    }
}
