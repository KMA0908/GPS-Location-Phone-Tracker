package com.nhn.gps.location.phone.tracker.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.databinding.FragmentHomeBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeFragment : BaseFragment<FragmentHomeBinding, MainViewModel>() {

    override val viewModel: MainViewModel by viewModels({ requireActivity() })

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentHomeBinding = FragmentHomeBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) = with(binding) {
        navHome.setOnClickListener { updateSelectedItem(it.id) }
        navMap.setOnClickListener { updateSelectedItem(it.id) }
        navShield.setOnClickListener { updateSelectedItem(it.id) }
        navProfile.setOnClickListener { updateSelectedItem(it.id) }
        
        navLocation.setOnClickListener {
            viewModel.openMap()
        }

        phone.root.setOnClickListener {
            viewModel.openPhoneLocator()
        }

        // Initial selection
        updateSelectedItem(R.id.navHome)
    }

    private fun updateSelectedItem(selectedId: Int) = with(binding) {
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

    companion object {
        fun newInstance() = HomeFragment()
    }
}
