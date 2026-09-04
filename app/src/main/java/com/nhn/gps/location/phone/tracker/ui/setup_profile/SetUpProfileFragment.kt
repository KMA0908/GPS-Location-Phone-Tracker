package com.nhn.gps.location.phone.tracker.ui.setup_profile

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.BundleCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.base.UiMessage
import com.nhn.gps.location.phone.tracker.databinding.FragmentSetUpProfileBinding
import com.nhn.gps.location.phone.tracker.data.model.Country
import com.nhn.gps.location.phone.tracker.ui.phone_number_locator.CountrySelectorBottomSheet
import com.nhn.gps.location.phone.tracker.util.loadAvatar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SetUpProfileFragment : BaseFragment<FragmentSetUpProfileBinding, SetUpProfileViewModel>() {

    override val viewModel: SetUpProfileViewModel by viewModels()

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentSetUpProfileBinding = FragmentSetUpProfileBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) = with(binding) {
        imgBack.setOnClickListener { handleToolbarBack() }

        imgProfile.setOnClickListener {
            AvatarSelectorBottomSheet.newInstance(viewModel.avatarKey.value)
                .show(parentFragmentManager, AvatarSelectorBottomSheet.TAG)
        }

        cardPhone.txtDialCode.setOnClickListener {
            CountrySelectorBottomSheet.newInstance(viewModel.selectedCountry.value.iso)
                .show(parentFragmentManager, CountrySelectorBottomSheet.TAG)
        }

        parentFragmentManager.setFragmentResultListener(
            CountrySelectorBottomSheet.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, bundle ->
            BundleCompat.getParcelable(
                bundle,
                CountrySelectorBottomSheet.EXTRA_COUNTRY,
                Country::class.java,
            )?.let(viewModel::onCountryChanged)
        }

        parentFragmentManager.setFragmentResultListener(
            AvatarSelectorBottomSheet.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val avatarKey = bundle.getString(AvatarSelectorBottomSheet.RESULT_AVATAR_KEY)
            if (avatarKey != null) {
                viewModel.onAvatarChanged(avatarKey)
            }
        }

        cardName.edtValue.addTextChangedListener {
            viewModel.onNameChanged(it?.toString() ?: "")
        }

        cardPhone.edtValue.addTextChangedListener {
            val phone = it?.toString()?.trim() ?: ""
            viewModel.onPhoneChanged(phone)
            if (phone.isNotEmpty() && !viewModel.isValidPhone(phone)) {
                cardPhone.edtValue.error = getString(R.string.invalid_phone)
            } else {
                cardPhone.edtValue.error = null
            }
        }

        btnSave.setOnClickListener {
            val phone = cardPhone.edtValue.text?.toString()?.trim() ?: ""
            if (phone.isBlank() || viewModel.isValidPhone(phone)) {
                viewModel.onSaveClicked()
            } else {
                cardPhone.edtValue.error = getString(R.string.invalid_phone)
            }
        }
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.selectedCountry.collectLatest { country ->
                        binding.cardPhone.txtDialCode.text =
                            getString(R.string.phone_country_prefix, country.emoji, country.dialCode)
                    }
                }
                launch {
                    viewModel.isSaveEnabled.collect { isEnabled ->
                        updateSaveButtonState(isEnabled)
                    }
                }
                launch {
                    viewModel.avatarKey.collect { avatarKey ->
                        binding.imgProfile.loadAvatar(avatarKey, null)
                    }
                }
                launch {
                    viewModel.uiState.collectLatest { state ->
                        render(state)
                    }
                }
                launch {
                    viewModel.messages.collect { message ->
                        if (message is UiMessage.Error) {
                            Toast.makeText(requireContext(), message.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun render(state: SetUpProfileUiState) = withBinding {
        when (state) {
            is SetUpProfileUiState.Loading -> {
                btnSave.isEnabled = false
            }
            is SetUpProfileUiState.Success -> {
                Toast.makeText(requireContext(), R.string.profile_updated, Toast.LENGTH_SHORT).show()
                // Navigation is handled by ViewModel/NavigationManager
            }
            is SetUpProfileUiState.Error -> {
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                viewModel.resetState()
            }
            else -> {
                btnSave.isEnabled = viewModel.isSaveEnabled.value
            }
        }
    }

    private fun updateSaveButtonState(isEnabled: Boolean) = with(binding) {
        btnSave.isEnabled = isEnabled
        if (isEnabled) {
            btnSave.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.bg_switch_permission)
            )
            btnSave.setTextColor(Color.WHITE)
            btnSave.iconTint = ColorStateList.valueOf(Color.WHITE)
        } else {
            btnSave.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1A181D18"))
            btnSave.setTextColor(Color.parseColor("#B5B5B5"))
            btnSave.iconTint = ColorStateList.valueOf(Color.parseColor("#B5B5B5"))
        }
    }

    companion object {
        fun newInstance() = SetUpProfileFragment()
    }
}
