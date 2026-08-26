package com.nhn.gps.location.phone.tracker.ui.zone

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.view.animation.AnimationUtils
import androidx.core.widget.doAfterTextChanged
import com.nhn.gps.location.phone.tracker.R
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.data.model.ZoneAlert
import com.nhn.gps.location.phone.tracker.data.model.ZoneStatus
import com.nhn.gps.location.phone.tracker.databinding.FragmentNotificationsLocalBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
@AndroidEntryPoint
class NotificationsFragment : BaseFragment<FragmentNotificationsLocalBinding, NotificationsViewModel>() {
    override val viewModel: NotificationsViewModel by viewModels()
    private lateinit var adapter: ZoneAlertAdapter

    override fun createBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentNotificationsLocalBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) = with(binding) {
        adapter = ZoneAlertAdapter(
            onClick = { alert ->
                AlertDetailState.selectedAlert = alert
                navigationManager.navigateTo(com.nhn.gps.location.phone.tracker.navigation.AppDestination.AlertDetail)
            },
            onDelete = ::deleteAlert,
        )
        recyclerNotifications.adapter = adapter
        btnBack.setOnClickListener { handleToolbarBack() }
        btnClear.setOnClickListener { confirmClear() }
        
        editSearch.doAfterTextChanged {
            viewModel.updateSearchQuery(it?.toString().orEmpty())
        }

        editSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                v.clearFocus()
                true
            } else false
        }

        btnStatus.setOnClickListener { showStatusPicker() }
        btnDays.setOnClickListener { showDateRangePicker() }

        ivRefresh.setOnClickListener {
            viewModel.refresh()
            it.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.rotate))
        }

        ivFilter.setOnClickListener { showAdvancedFilter() }
        ivAdvancedFilter.setOnClickListener { viewModel.toggleNameSort() }
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.filteredAlerts.collectLatest { render(it) }
                }
                launch {
                    viewModel.filterState.collectLatest { updateFilterChips(it) }
                }
                launch {
                    viewModel.isRefreshing.collectLatest { refreshing ->
                        if (!refreshing) binding.ivRefresh.clearAnimation()
                    }
                }
            }
        }
    }

    private fun render(list: List<ZoneAlert>) {
        if (!isAdded) return
        
        if (list.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.recyclerNotifications.visibility = View.GONE
            // Set empty state text based on search/filter
            if (viewModel.filterState.value.let { it.searchQuery.isNotBlank() || it.status != null || it.dateRange != DateRange.ALL }) {
                binding.tvEmptyTitle.text = getString(R.string.no_matching_notifications)
            } else {
                binding.tvEmptyTitle.text = getString(R.string.no_notifications)
            }
            return
        }

        binding.emptyState.visibility = View.GONE
        binding.recyclerNotifications.visibility = View.VISIBLE

        val grouped = list.sortedByDescending { it.time }.groupBy { alert ->
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = alert.time
            val today = Calendar.getInstance()
            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            
            when {
                isSameDay(calendar, today) -> getString(R.string.today)
                isSameDay(calendar, yesterday) -> getString(R.string.yesterday)
                else -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(alert.time))
            }
        }

        val listWithHeaders = mutableListOf<Any>()
        grouped.forEach { (header, alerts) ->
            listWithHeaders.add(header)
            listWithHeaders.addAll(alerts)
        }
        adapter.submitList(listWithHeaders)
    }

    private fun updateFilterChips(state: NotificationFilterState) = with(binding) {
        btnStatus.isSelected = state.status != null
        btnStatus.text = when(state.status) {
            ZoneStatus.SAFE -> getString(R.string.safe)
            ZoneStatus.DANGEROUS -> getString(R.string.dangerous)
            else -> getString(R.string.status_filter)
        }

        btnDays.isSelected = state.dateRange != DateRange.ALL
        btnDays.text = when(state.dateRange) {
            DateRange.TODAY -> getString(R.string.today)
            DateRange.LAST_7_DAYS -> getString(R.string.last_7_days)
            DateRange.LAST_30_DAYS -> getString(R.string.last_30_days)
            else -> getString(R.string.days_filter)
        }

        ivAdvancedFilter.isSelected = state.sortType != SortType.TIME_DESC
        ivAdvancedFilter.setImageResource(if (state.sortType == SortType.NAME_DESC) R.drawable.ic_sort_za else R.drawable.ic_sort_az)

        ivFilter.isSelected = state.status != null || state.dateRange != DateRange.ALL
    }

    private fun showStatusPicker() {
        val items = arrayOf(getString(R.string.all), getString(R.string.safe), getString(R.string.dangerous))
        val current = when(viewModel.filterState.value.status) {
            null -> 0
            ZoneStatus.SAFE -> 1
            ZoneStatus.DANGEROUS -> 2
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.status_filter)
            .setSingleChoiceItems(items, current) { dialog, which ->
                viewModel.setStatusFilter(when(which) {
                    1 -> ZoneStatus.SAFE
                    2 -> ZoneStatus.DANGEROUS
                    else -> null
                })
                dialog.dismiss()
            }
            .show()
    }

    private fun showDateRangePicker() {
        val items = arrayOf(getString(R.string.all_time), getString(R.string.today), getString(R.string.last_7_days), getString(R.string.last_30_days))
        val current = viewModel.filterState.value.dateRange.ordinal

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.days_filter)
            .setSingleChoiceItems(items, current) { dialog, which ->
                viewModel.setDateRangeFilter(DateRange.entries[which])
                dialog.dismiss()
            }
            .show()
    }

    private fun showAdvancedFilter() {
        AlertFilterBottomSheet.newInstance().show(childFragmentManager, "AlertFilterBottomSheet")
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun deleteAlert(alert: ZoneAlert) {
        viewModel.deleteAlert(alert.id)
    }

    private fun confirmClear() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_confirm_clear_alerts, null)
        val dialogBinding = com.nhn.gps.location.phone.tracker.databinding.DialogConfirmClearAlertsBinding
            .bind(dialogView)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogBinding.btnClose.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnClear.setOnClickListener {
            viewModel.clearAlerts()
            dialog.dismiss()
        }
        dialog.show()
    }

    companion object { fun newInstance() = NotificationsFragment() }
}
