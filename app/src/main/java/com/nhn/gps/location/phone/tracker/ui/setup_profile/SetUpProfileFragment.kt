package com.nhn.gps.location.phone.tracker.ui.setup_profile

import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.base.UiMessage
import com.nhn.gps.location.phone.tracker.databinding.FragmentSetUpProfileBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SetUpProfileFragment : BaseFragment<FragmentSetUpProfileBinding, SetUpProfileViewModel>() {

    override val viewModel: SetUpProfileViewModel by viewModels()

    private var selectedImageUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedImageUri = it
            viewModel.onAvatarChanged(it)
            previewAvatar(it)
        }
    }

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentSetUpProfileBinding = FragmentSetUpProfileBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) = with(binding) {
        imgBack.setOnClickListener { handleToolbarBack() }

        imgProfile.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        cardName.edtValue.addTextChangedListener {
            viewModel.onNameChanged(it?.toString() ?: "")
        }

        cardPhone.edtValue.addTextChangedListener {
            val phone = it?.toString()?.trim() ?: ""
            viewModel.onPhoneChanged(phone)
            if (phone.isNotEmpty() && !viewModel.isValidVietnamPhone(phone)) {
                cardPhone.edtValue.error = getString(R.string.invalid_phone)
            } else {
                cardPhone.edtValue.error = null
            }
        }

        btnSave.setOnClickListener {
            val phone = cardPhone.edtValue.text?.toString()?.trim() ?: ""
            if (viewModel.isValidVietnamPhone(phone)) {
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
                    viewModel.isSaveEnabled.collect { isEnabled ->
                        updateSaveButtonState(isEnabled)
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

    private fun previewAvatar(uri: Uri) {
        Glide.with(this)
            .load(uri)
            .circleCrop()
            .into(binding.imgProfile)
    }

    private fun render(state: SetUpProfileUiState) = withBinding {
        when (state) {
            is SetUpProfileUiState.Loading -> {
                btnSave.isEnabled = false
            }
            is SetUpProfileUiState.PhoneAlreadyExists -> {
                showPhoneExistsDialog()
                viewModel.resetState()
            }
            is SetUpProfileUiState.Success -> {
                Toast.makeText(requireContext(), "Glad to see you again!", Toast.LENGTH_SHORT).show()
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

    private fun showPhoneExistsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.exit_title) // Reusing existing or should use specific string
            .setMessage("This phone number already exists.")
            .setPositiveButton(R.string.ok) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
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
