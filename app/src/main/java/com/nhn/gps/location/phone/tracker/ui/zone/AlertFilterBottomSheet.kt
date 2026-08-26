package com.nhn.gps.location.phone.tracker.ui.zone

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.data.model.ZoneStatus
import com.nhn.gps.location.phone.tracker.databinding.LayoutAlertFilterBottomSheetBinding

class AlertFilterBottomSheet : BottomSheetDialogFragment() {

    private var _binding: LayoutAlertFilterBottomSheetBinding? = null
    private val binding get() = _binding!!

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    private val viewModel: NotificationsViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LayoutAlertFilterBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupState()
        setupListeners()
    }

    private fun setupState() {
        val state = viewModel.filterState.value
        
        // Sort
        when (state.sortType) {
            SortType.TIME_DESC -> binding.chipNewest.isChecked = true
            SortType.NAME_ASC -> binding.chipSortAZ.isChecked = true
            SortType.NAME_DESC -> binding.chipSortZA.isChecked = true
        }

        // Status
        when (state.status) {
            null -> binding.chipStatusAll.isChecked = true
            ZoneStatus.SAFE -> binding.chipStatusSafe.isChecked = true
            ZoneStatus.DANGEROUS -> binding.chipStatusDangerous.isChecked = true
        }

        // Time
        when (state.dateRange) {
            DateRange.ALL -> binding.chipTimeAll.isChecked = true
            DateRange.TODAY -> binding.chipTimeToday.isChecked = true
            DateRange.LAST_7_DAYS -> binding.chipTime7Days.isChecked = true
            DateRange.LAST_30_DAYS -> binding.chipTime30Days.isChecked = true
        }
    }

    private fun setupListeners() {
        binding.btnApply.setOnClickListener {
            val sortType = when {
                binding.chipSortAZ.isChecked -> SortType.NAME_ASC
                binding.chipSortZA.isChecked -> SortType.NAME_DESC
                else -> SortType.TIME_DESC
            }
            
            val status = when {
                binding.chipStatusSafe.isChecked -> ZoneStatus.SAFE
                binding.chipStatusDangerous.isChecked -> ZoneStatus.DANGEROUS
                else -> null
            }

            val dateRange = when {
                binding.chipTimeToday.isChecked -> DateRange.TODAY
                binding.chipTime7Days.isChecked -> DateRange.LAST_7_DAYS
                binding.chipTime30Days.isChecked -> DateRange.LAST_30_DAYS
                else -> DateRange.ALL
            }

            viewModel.applyAdvancedFilter(status, dateRange, sortType)
            dismiss()
        }

        binding.btnReset.setOnClickListener {
            viewModel.resetFilters()
            dismiss()
        }
    }

    override fun getTheme(): Int = R.style.CustomBottomSheetDialogTheme

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = AlertFilterBottomSheet()
    }
}
