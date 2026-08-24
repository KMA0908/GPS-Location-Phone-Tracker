package com.nhn.gps.location.phone.tracker.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.data.local.AppPreferences
import com.nhn.gps.location.phone.tracker.databinding.FragmentSettingsLanguageBinding
import com.nhn.gps.location.phone.tracker.ui.language.SettingsLanguageAdapter
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
        super.onViewCreated(view, savedInstanceState)
        binding.tvTitle.text = LanguageHelper.getLocalizedString(
            requireContext(),
            R.string.text_language,
            LanguageHelper.currentLanguageCode(requireContext()),
        )
        val items = LanguageHelper.getSettingsLanguageList(requireContext())
        val selected = LanguageHelper.languageIndexFromCode(LanguageHelper.currentLanguageCode(requireContext()))
        selectedCode = items.getOrNull(selected)?.languageCode ?: LanguageHelper.normalizeSupportedLanguageCode("en")
        adapter = SettingsLanguageAdapter(items) { index ->
            selectedCode = items[index].languageCode
            adapter.select(index)
        }
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@SettingsLanguageFragment.adapter
            itemAnimator = null
            setHasFixedSize(true)
            post { scrollToPosition(selected.coerceAtLeast(0)) }
        }
        adapter.select(selected)
        binding.btnConfirm.setOnClickListener { applyLanguage() }
    }

    private fun applyLanguage() {
        val languageCode = selectedCode
        val currentLanguageCode = LanguageHelper.currentLanguageCode(requireContext())
        if (languageCode == currentLanguageCode) {
            parentFragmentManager.popBackStack()
            return
        }

        val hostActivity = activity ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            preferences.saveSelectedLanguage(languageCode)
            LanguageHelper.setAppLanguage(languageCode)
        }
    }

    override fun onDestroyView() {
        binding.recyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object { fun newInstance() = SettingsLanguageFragment() }
}
