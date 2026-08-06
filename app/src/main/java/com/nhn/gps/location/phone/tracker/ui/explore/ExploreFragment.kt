package com.nhn.gps.location.phone.tracker.ui.explore

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.databinding.FragmentExploreBinding
import com.nhn.gps.location.phone.tracker.ui.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

import androidx.recyclerview.widget.LinearLayoutManager
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.data.model.FamousPlaceModel

@AndroidEntryPoint
class ExploreFragment : BaseFragment<FragmentExploreBinding, MainViewModel>() {

    override val viewModel: MainViewModel by viewModels({ requireActivity() })
    private val cardAdapter by lazy { 
        ExploreCardAdapter { 
            navigationManager.navigateTo(com.nhn.gps.location.phone.tracker.navigation.AppDestination.PlaceDetail)
        } 
    }

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentExploreBinding = FragmentExploreBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) = with(binding) {
        setupRecyclerView()
        
        btnExploreNow.setOnClickListener {
            navigationManager.navigateTo(com.nhn.gps.location.phone.tracker.navigation.AppDestination.PlaceDetail)
        }
    }

    private fun setupRecyclerView() = with(binding.rvExploreCards) {
        layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        adapter = cardAdapter
        cardAdapter.submitList(getMockData())
    }

    private fun getMockData() = listOf(
        FamousPlaceModel("1", "Eiffel Tower", "Paris, France", R.drawable.ic_paris, 4.8f, 1200, 2.5, "Famous"),
        FamousPlaceModel("2", "Taj Mahal", "Agra, India", R.drawable.ic_paris, 4.7f, 950, 5000.0, "Famous"),
        FamousPlaceModel("3", "Colosseum", "Rome, Italy", R.drawable.ic_paris, 4.9f, 800, 1200.0, "Famous"),
        FamousPlaceModel("4", "Statue of Liberty", "New York, USA", R.drawable.ic_paris, 4.6f, 1500, 6000.0, "Famous")
    )

    companion object {
        fun newInstance() = ExploreFragment()
    }
}
