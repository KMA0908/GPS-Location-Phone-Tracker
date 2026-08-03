package com.nhn.gps.location.phone.tracker.ui.zone

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.data.model.ZoneAlert
import com.nhn.gps.location.phone.tracker.data.repository.ZoneRepository
import com.nhn.gps.location.phone.tracker.databinding.FragmentNotificationsLocalBinding
import com.nhn.gps.location.phone.tracker.ui.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationsFragment : BaseFragment<FragmentNotificationsLocalBinding, MainViewModel>() {
    override val viewModel: MainViewModel by viewModels({ requireActivity() })
    @Inject lateinit var zoneRepository: ZoneRepository
    private lateinit var adapter: ZoneAlertAdapter
    private var current: List<ZoneAlert> = emptyList()
    private var filter: String = "All"

    override fun createBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentNotificationsLocalBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) = with(binding) {
        adapter = ZoneAlertAdapter { alert ->
            // The alert row is intentionally shared with Zone alerts so both centers stay consistent.
            val action = if (alert.isEnter) "entered" else "left"
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Notification")
                .setMessage("${alert.userName} $action ${alert.zoneName}")
                .setPositiveButton("OK", null).show()
        }
        recyclerNotifications.adapter = adapter
        btnBack.setOnClickListener { handleToolbarBack() }
        listOf(btnAll, btnZones, btnDangerous).forEach { chip ->
            chip.setOnClickListener { filter = chip.text.toString(); render() }
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
        val list = when (filter) {
            "Dangerous" -> current.filter { it.status.code == 1 }
            "Zones" -> current
            else -> current
        }
        adapter.submitList(list)
        binding.emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerNotifications.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
    }

    companion object { fun newInstance() = NotificationsFragment() }
}
