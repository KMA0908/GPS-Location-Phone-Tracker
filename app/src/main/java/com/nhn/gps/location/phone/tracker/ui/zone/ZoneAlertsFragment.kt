package com.nhn.gps.location.phone.tracker.ui.zone

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.data.model.ZoneAlert
import com.nhn.gps.location.phone.tracker.data.model.ZoneStatus
import com.nhn.gps.location.phone.tracker.data.repository.ZoneRepository
import com.nhn.gps.location.phone.tracker.databinding.FragmentZoneAlertsLocalBinding
import com.nhn.gps.location.phone.tracker.ui.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class ZoneAlertsFragment : BaseFragment<FragmentZoneAlertsLocalBinding, MainViewModel>() {
    override val viewModel: MainViewModel by viewModels({ requireActivity() })
    @Inject lateinit var zoneRepository: ZoneRepository
    private lateinit var adapter: ZoneAlertAdapter
    private var current: List<ZoneAlert> = emptyList()
    private var filter = Filter.ALL
    private var searchQuery = ""

    private enum class Filter { ALL, ENTERED, LEFT, DANGEROUS }

    override fun createBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentZoneAlertsLocalBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) = with(binding) {
        adapter = ZoneAlertAdapter(::showDetails)
        recyclerAlerts.adapter = adapter
        btnBack.setOnClickListener { handleToolbarBack() }
        btnClear.setOnClickListener { confirmClear() }
        btnAll.setOnClickListener { setFilter(Filter.ALL) }
        btnEntered.setOnClickListener { setFilter(Filter.ENTERED) }
        btnLeft.setOnClickListener { setFilter(Filter.LEFT) }
        btnDangerous.setOnClickListener { setFilter(Filter.DANGEROUS) }
        
        editSearch.doAfterTextChanged { 
            searchQuery = it?.toString().orEmpty()
            render()
        }

        editSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                v.clearFocus()
                true
            } else false
        }
        
        setFilter(Filter.ALL)
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                zoneRepository.alerts.collectLatest { current = it; render() }
            }
        }
    }

    private fun setFilter(value: Filter) {
        filter = value
        with(binding) {
            listOf(btnAll, btnEntered, btnLeft, btnDangerous).forEach { it.isSelected = false }
            when (value) {
                Filter.ALL -> btnAll.isSelected = true
                Filter.ENTERED -> btnEntered.isSelected = true
                Filter.LEFT -> btnLeft.isSelected = true
                Filter.DANGEROUS -> btnDangerous.isSelected = true
            }
        }
        render()
    }

    private fun render() {
        if (!isAdded) return
        var filtered = when (filter) {
            Filter.ALL -> current
            Filter.ENTERED -> current.filter { it.isEnter }
            Filter.LEFT -> current.filterNot { it.isEnter }
            Filter.DANGEROUS -> current.filter { it.status == ZoneStatus.DANGEROUS }
        }
        
        if (searchQuery.isNotBlank()) {
            filtered = filtered.filter { 
                it.zoneName.contains(searchQuery, true) || it.userName.contains(searchQuery, true)
            }
        }

        if (filtered.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.recyclerAlerts.visibility = View.GONE
            return
        }

        binding.emptyState.visibility = View.GONE
        binding.recyclerAlerts.visibility = View.VISIBLE

        val grouped = filtered.sortedByDescending { it.time }.groupBy { alert ->
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = alert.time
            val today = Calendar.getInstance()
            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            
            when {
                isSameDay(calendar, today) -> "Today"
                isSameDay(calendar, yesterday) -> "Yesterday"
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

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun confirmClear() {
        if (current.isEmpty()) return
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_confirm_clear_alerts, null)
        val dialogBinding = com.nhn.gps.location.phone.tracker.databinding.DialogConfirmClearAlertsBinding.bind(dialogView)
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()
            
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.btnClose.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnClear.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch { zoneRepository.clearAlerts() }
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun showDetails(alert: ZoneAlert) {
        AlertDetailState.selectedAlert = alert
        navigationManager.navigateTo(com.nhn.gps.location.phone.tracker.navigation.AppDestination.AlertDetail)
    }

    companion object { fun newInstance() = ZoneAlertsFragment() }
}
