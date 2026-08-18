package com.nhn.gps.location.phone.tracker.ui.explore

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.databinding.FragmentFamousPlaceListBinding
import com.nhn.gps.location.phone.tracker.databinding.ItemFamousCategoryBinding
import com.nhn.gps.location.phone.tracker.navigation.AppDestination
import com.nhn.gps.location.phone.tracker.ui.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FamousPlaceFragment : BaseFragment<FragmentFamousPlaceListBinding, FamousPlaceViewModel>() {

    override val viewModel: FamousPlaceViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels({ requireActivity() })

    private val categoryAdapter = FamousCategoryAdapter { category ->
        // Keep the app's original flow: choosing a category only changes the
        // content. Earth is opened explicitly from the "See map" action.
        viewModel.onCategorySelected(category.filterName)
        mainViewModel.setSelectedFamousCategoryId(category.id)
    }

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentFamousPlaceListBinding =
        FragmentFamousPlaceListBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) = with(binding) {
        btnBack.setOnClickListener { handleToolbarBack() }
        btnSeeMap.setOnClickListener {
            navigationManager.navigateTo(AppDestination.Explore)
        }
        rvCategories.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = categoryAdapter
            itemAnimator = null
        }
        categoryAdapter.submit(CATEGORIES)
    }

    override fun observeData() = Unit

    override fun onDestroyView() {
        binding.rvCategories.adapter = null
        super.onDestroyView()
    }

    data class FamousCategory(
        val id: Int,
        @StringRes val nameRes: Int,
        val filterName: String,
        @DrawableRes val photoRes: Int,
        @DrawableRes val iconRes: Int,
    )

    private class FamousCategoryAdapter(
        private val onClick: (FamousCategory) -> Unit,
    ) : RecyclerView.Adapter<FamousCategoryAdapter.CategoryViewHolder>() {

        private val items = mutableListOf<FamousCategory>()

        fun submit(categories: List<FamousCategory>) {
            items.clear()
            items.addAll(categories)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder =
            CategoryViewHolder(
                ItemFamousCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            )

        override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) =
            holder.bind(items[position])

        override fun getItemCount(): Int = items.size

        inner class CategoryViewHolder(
            private val binding: ItemFamousCategoryBinding,
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(category: FamousCategory) = with(binding) {
                tvName.setText(category.nameRes)
                ivPhoto.setImageResource(category.photoRes)
                ivIcon.setImageResource(category.iconRes)
                root.setOnClickListener { onClick(category) }
            }
        }
    }

    companion object {
        private val CATEGORIES = listOf(
            FamousCategory(1, R.string.category_romantic, "Romantic", R.drawable.place_category_1, R.drawable.place_type_icon_14),
            FamousCategory(2, R.string.category_amusement, "Theme Parks", R.drawable.place_category_2, R.drawable.place_type_icon_1),
            FamousCategory(3, R.string.category_mountain, "Mountains", R.drawable.place_category_3, R.drawable.place_type_icon_2),
            FamousCategory(4, R.string.category_nature, "Nature", R.drawable.place_category_4, R.drawable.place_type_icon_3),
            FamousCategory(5, R.string.category_dangerous, "Dangerous", R.drawable.place_category_5, R.drawable.place_type_icon_4),
            FamousCategory(6, R.string.category_mysterious, "Mysterious", R.drawable.place_category_6, R.drawable.place_type_icon_5),
            FamousCategory(7, R.string.category_surf, "Surf", R.drawable.place_category_7, R.drawable.place_type_icon_6),
            FamousCategory(8, R.string.category_ghost_towns, "Ghost Towns", R.drawable.place_category_8, R.drawable.place_type_icon_7),
            FamousCategory(9, R.string.category_filming, "Film Locations", R.drawable.place_category_9, R.drawable.place_type_icon_8),
            FamousCategory(10, R.string.category_weather, "Extreme Weather", R.drawable.place_category_10, R.drawable.place_type_icon_9),
            FamousCategory(11, R.string.category_holiday, "Family", R.drawable.place_category_11, R.drawable.place_type_icon_10),
            FamousCategory(12, R.string.category_destinations, "Cities", R.drawable.place_category_12, R.drawable.place_type_icon_11),
            FamousCategory(13, R.string.category_clubs, "Clubs", R.drawable.place_category_13, R.drawable.place_type_icon_12),
            FamousCategory(14, R.string.category_night, "Nightlife", R.drawable.place_category_14, R.drawable.place_type_icon_13),
        )

        fun newInstance() = FamousPlaceFragment()
    }
}
