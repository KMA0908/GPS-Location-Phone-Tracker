package com.nhn.gps.location.phone.tracker.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.databinding.FragmentSettingsBinding
import com.nhn.gps.location.phone.tracker.ui.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsFragment : BaseFragment<FragmentSettingsBinding, MainViewModel>() {
    override val viewModel: MainViewModel by viewModels({ requireActivity() })

    override fun createBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentSettingsBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) = with(binding) {
        btnBack.setOnClickListener { viewModel.navigateBack() }
        rowLanguage.setOnClickListener { viewModel.openSettingsLanguage() }
        rowShare.setOnClickListener { shareApp() }
        rowPrivacy.setOnClickListener { openUrl(PRIVACY_POLICY_URL) }
        rowTerms.setOnClickListener { openUrl(TERMS_URL) }
        rowRate.setOnClickListener { openStore() }
    }

    private fun shareApp() {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://play.google.com/store/apps/details?id=${requireContext().packageName}")
        }, null))
    }

    private fun openUrl(url: String) = startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    private fun openStore() {
        val id = requireContext().packageName
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id")))
        } catch (_: ActivityNotFoundException) {
            openUrl("https://play.google.com/store/apps/details?id=$id")
        }
    }

    companion object {
        private const val PRIVACY_POLICY_URL = "https://stech.io.vn/privacyPolicy.html"
        private const val TERMS_URL = "https://stech.io.vn/termofuse.html"
        fun newInstance() = SettingsFragment()
    }
}
