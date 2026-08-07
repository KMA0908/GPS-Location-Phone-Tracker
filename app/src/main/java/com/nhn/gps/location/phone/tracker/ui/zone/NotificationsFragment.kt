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
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.data.model.ZoneAlert
import com.nhn.gps.location.phone.tracker.data.model.ZoneStatus
import com.nhn.gps.location.phone.tracker.data.repository.ZoneRepository
import com.nhn.gps.location.phone.tracker.databinding.FragmentNotificationsLocalBinding
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
class NotificationsFragment : BaseFragment<FragmentNotificationsLocalBinding, MainViewModel>() {
    override val viewModel: MainViewModel by viewModels({ requireActivity() })
    @Inject lateinit var zoneRepository: ZoneRepository
    private lateinit var adapter: ZoneAlertAdapter
    private var current: List<ZoneAlert> = emptyList()
    private var filter: String = "All"
    private var searchQuery: String = ""

    override fun createBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentNotificationsLocalBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) = with(binding) {
        adapter = ZoneAlertAdapter { alert ->
            AlertDetailState.selectedAlert = alert
            navigationManager.navigateTo(com.nhn.gps.location.phone.tracker.navigation.AppDestination.AlertDetail)
        }
        recyclerNotifications.adapter = adapter
        btnBack.setOnClickListener { handleToolbarBack() }
        
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

        // Cập nhật click listeners cho các chip lọc mới
        val filterChips = listOf(btnZones, btnFriends, btnStatus, btnDays)
        filterChips.forEach { chip ->
            chip.setOnClickListener { 
                filter = chip.text.toString().replace(" ⌵", "")
                // Toggle selection UI (optional, keeping it simple as requested)
                render() 
            }
        }
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                zoneRepository.alerts.collectLatest { current = it; render() }
            }
        }
    }

    private fun render() {
        if (!isAdded) return
        
        // Basic filtering based on current chips (logic adapted to existing alerts)
        var list = when (filter) {
            "Dangerous" -> current.filter { it.status == ZoneStatus.DANGEROUS }
            else -> current
        }

        if (searchQuery.isNotBlank()) {
            list = list.filter {
                it.zoneName.contains(searchQuery, true) || it.userName.contains(searchQuery, true)
            }
        }

        if (list.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.recyclerNotifications.visibility = View.GONE
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

    companion object { fun newInstance() = NotificationsFragment() }
}
