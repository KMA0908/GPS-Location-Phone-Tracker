package com.nhn.gps.location.phone.tracker.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.databinding.DialogTurnOffLocationSharingBinding
import com.nhn.gps.location.phone.tracker.databinding.FragmentSettingsBinding
import com.nhn.gps.location.phone.tracker.databinding.ItemSettingsMenuBinding
import com.nhn.gps.location.phone.tracker.navigation.AppDestination
import com.nhn.gps.location.phone.tracker.ui.main.MainViewModel
import com.nhn.gps.location.phone.tracker.util.LanguageHelper
import com.nhn.gps.location.phone.tracker.util.loadAvatar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : BaseFragment<FragmentSettingsBinding, MainViewModel>() {
    override val viewModel: MainViewModel by activityViewModels()

    override fun createBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentSettingsBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?): Unit {
        with(binding) {
            btnBack.setOnClickListener { viewModel.navigateBack() }

            cardProfile.setOnClickListener {
                navigationManager.navigateTo(AppDestination.EditProfile)
            }

            switchLocationSharing.setOnCheckedChangeListener { _, isChecked ->
                if (!isChecked && viewModel.isLocationSharingEnabled.value) {
                    // Prevent immediate switch off, show dialog first
                    switchLocationSharing.isChecked = true
                    showTurnOffLocationDialog()
                } else if (isChecked && !viewModel.isLocationSharingEnabled.value) {
                    viewModel.setLocationSharingEnabled(true)
                }
            }

            setupMenuItems()
        }
    }

    private fun setupMenuItems() = with(binding) {
        itemLanguage.apply {
            ivIcon.setImageResource(R.drawable.ic_language)
            tvTitle.text = getString(R.string.language_selected)
            tvValue.visibility = View.VISIBLE
            tvValue.text = LanguageHelper.languageDisplayName(
                requireContext(),
                LanguageHelper.currentLanguageCode(requireContext()),
            )
            root.setOnClickListener { viewModel.openSettingsLanguage() }
        }

        itemNotification.apply {
            ivIcon.setImageResource(R.drawable.ic_notification_home)
            tvTitle.text = getString(R.string.notification_title)
            ivChevron.visibility = View.GONE
            swMenu.visibility = View.VISIBLE
            
            swMenu.setOnCheckedChangeListener { _, isChecked ->
                if (!isChecked && viewModel.isNotificationEnabled.value) {
                    swMenu.isChecked = true
                    showTurnOffNotificationDialog()
                } else if (isChecked && !viewModel.isNotificationEnabled.value) {
                    viewModel.setNotificationEnabled(true)
                }
            }
            root.setOnClickListener { swMenu.toggle() }
        }

        itemRate.apply {
            ivIcon.setImageResource(R.drawable.ic_rate_setting)
            tvTitle.text = getString(R.string.rate_us)
            root.setOnClickListener { openStore() }
        }

        itemShare.apply {
            ivIcon.setImageResource(R.drawable.ic_share_setting)
            tvTitle.text = getString(R.string.share_app)
            root.setOnClickListener { shareApp() }
        }

        itemPrivacy.apply {
            ivIcon.setImageResource(R.drawable.ic_policy_setting)
            tvTitle.text = getString(R.string.privacy_policy)
            root.setOnClickListener { openUrl(PRIVACY_POLICY_URL) }
        }
    }

    private fun showTurnOffNotificationDialog() {
        val dialogBinding = DialogTurnOffLocationSharingBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()

        dialogBinding.ivIcon.setImageResource(R.drawable.ic_notification_home) // Use notification icon
        dialogBinding.tvTitle.text = getString(R.string.turn_off_notification_title)
        dialogBinding.tvMsg.text = getString(R.string.turn_off_notification_msg)
        
        dialogBinding.btnClose.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnKeepOn.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnTurnOff.setOnClickListener {
            viewModel.setNotificationEnabled(false)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showTurnOffLocationDialog() {
        val dialogBinding = DialogTurnOffLocationSharingBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()

        dialogBinding.btnClose.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnKeepOn.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnTurnOff.setOnClickListener {
            viewModel.setLocationSharingEnabled(false)
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.userName.collectLatest { name ->
                        binding.tvName.text = name.ifBlank { "Guest" }
                    }
                }
                launch {
                    viewModel.userPhone.collectLatest { phone ->
                        binding.tvPhone.text = phone.ifBlank { "+84 000000" }
                    }
                }
                launch {
                    viewModel.selectedLanguage.collectLatest { lang ->
                        binding.itemLanguage.tvValue.visibility = View.VISIBLE
                        binding.itemLanguage.tvValue.text =
                            LanguageHelper.languageDisplayName(requireContext(), lang)
                    }
                }
                launch {
                    combine(
                        viewModel.userAvatar,
                        viewModel.userAvatarKey,
                        viewModel.localAvatarPath
                    ) { avatar, key, localPath ->
                        Triple(avatar, key, localPath)
                    }.collectLatest { (avatar, key, localPath) ->
                        binding.imgAvatar.loadAvatar(
                            key,
                            avatar,
                            localPath,
                            fallbackRes = R.drawable.ic_avt
                        )
                    }
                }
                launch {
                    viewModel.isLocationSharingEnabled.collectLatest { enabled ->
                        binding.switchLocationSharing.isChecked = enabled
                    }
                }
                launch {
                    viewModel.isNotificationEnabled.collectLatest { enabled ->
                        binding.itemNotification.swMenu.isChecked = enabled
                    }
                }
            }
        }
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
