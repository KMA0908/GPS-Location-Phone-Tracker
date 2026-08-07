package com.nhn.gps.location.phone.tracker.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.nhn.gps.location.phone.tracker.data.local.AppPreferences
import com.nhn.gps.location.phone.tracker.databinding.FragmentSettingsLanguageBinding
import com.nhn.gps.location.phone.tracker.ui.language.SettingsLanguageAdapter
import com.nhn.gps.location.phone.tracker.ui.main.MainActivity
import com.nhn.gps.location.phone.tracker.util.LanguageHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsLanguageFragment : Fragment() {
    @Inject lateinit var preferences: AppPreferences
    private var _binding: FragmentSettingsLanguageBinding? = null
    private val binding get() = requireNotNull(_binding)
    private lateinit var adapter: SettingsLanguageAdapter
    private var selectedCode = "en"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        FragmentSettingsLanguageBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        val items = LanguageHelper.languages()
        val selected = LanguageHelper.languageIndexFromCode(LanguageHelper.currentLanguageCode(requireContext()))
        selectedCode = items[selected].languageCode
        adapter = SettingsLanguageAdapter(items) { index ->
            selectedCode = items[index].languageCode
            adapter.select(index)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        adapter.select(selected)
        binding.btnConfirm.setOnClickListener { applyLanguage() }
    }

    private fun applyLanguage() {
        viewLifecycleOwner.lifecycleScope.launch {
            preferences.saveSelectedLanguage(selectedCode)
            LanguageHelper.setAppLanguage(requireContext().applicationContext, selectedCode)
            startActivity(Intent(requireContext(), MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("TARGET_DESTINATION", "home")
            })
            requireActivity().finish()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object { fun newInstance() = SettingsLanguageFragment() }
}
