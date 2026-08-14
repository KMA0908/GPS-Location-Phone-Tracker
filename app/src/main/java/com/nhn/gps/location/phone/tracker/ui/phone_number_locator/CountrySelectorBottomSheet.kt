package com.nhn.gps.location.phone.tracker.ui.phone_number_locator

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nhn.gps.location.phone.tracker.data.model.Country
import com.nhn.gps.location.phone.tracker.databinding.BottomSheetCountryBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CountrySelectorBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCountryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CountryViewModel by viewModels()

    private var selectedIso: String? = null

    private val countryAdapter by lazy {
        CountryAdapter { country ->
            selectCountry(country)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedIso = arguments?.getString(ARG_SELECTED_ISO)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCountryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSearch()
        setupAlphabetIndex()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        binding.rvCountries.apply {
            adapter = countryAdapter
        }
        countryAdapter.setSelectedCountry(selectedIso)
    }

    private fun setupSearch() {
        binding.edtSearch.doAfterTextChanged { text ->
            viewModel.onSearchQueryChanged(text?.toString().orEmpty())
        }
    }

    private fun setupAlphabetIndex() {
        val alphabet = ('A'..'Z').toList()
        binding.layoutIndex.removeAllViews()
        alphabet.forEach { letter ->
            val textView = TextView(context).apply {
                text = letter.toString()
                textSize = 10f
                setTextColor(Color.parseColor("#727272"))
                gravity = Gravity.CENTER
                setPadding(0, 2, 0, 2)
                setOnClickListener {
                    scrollToLetter(letter)
                }
            }
            binding.layoutIndex.addView(textView)
        }
    }

    private fun scrollToLetter(letter: Char) {
        val countries = countryAdapter.currentList
        val index = countries.indexOfFirst { it.name.trim().startsWith(letter, ignoreCase = true) }
        if (index != -1) {
            (binding.rvCountries.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(index, 0)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.filteredCountries.collectLatest { countries ->
                    countryAdapter.submitList(countries)
                }
            }
        }
    }

    private fun selectCountry(country: Country) {
        countryAdapter.setSelectedCountry(country.iso)
        setFragmentResult(REQUEST_KEY, bundleOf(EXTRA_COUNTRY to country))
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "CountrySelectorBottomSheet"
        const val REQUEST_KEY = "REQUEST_KEY_COUNTRY"
        const val EXTRA_COUNTRY = "EXTRA_COUNTRY"
        private const val ARG_SELECTED_ISO = "ARG_SELECTED_ISO"
        
        fun newInstance(selectedIso: String? = null): CountrySelectorBottomSheet {
            return CountrySelectorBottomSheet().apply {
                arguments = bundleOf(ARG_SELECTED_ISO to selectedIso)
            }
        }
    }
}
