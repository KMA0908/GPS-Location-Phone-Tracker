package com.nhn.gps.location.phone.tracker.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.databinding.FragmentEditProfileBinding
import com.nhn.gps.location.phone.tracker.navigation.AppDestination
import com.nhn.gps.location.phone.tracker.ui.friend.FriendCodeDisplayMode
import com.nhn.gps.location.phone.tracker.ui.main.MainViewModel
import com.nhn.gps.location.phone.tracker.util.loadAvatar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EditProfileFragment : BaseFragment<FragmentEditProfileBinding, EditProfileViewModel>() {

    override val viewModel: EditProfileViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentEditProfileBinding = FragmentEditProfileBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) = with(binding) {
        btnBack.setOnClickListener { handleToolbarBack() }

        etName.addTextChangedListener {
            viewModel.onNameChanged(it?.toString().orEmpty())
        }

        ivEditName.setOnClickListener {
            etName.requestFocus()
            val controller = androidx.core.view.WindowCompat.getInsetsController(requireActivity().window, etName)
            controller.show(androidx.core.view.WindowInsetsCompat.Type.ime())
        }

        cardShowQr.setOnClickListener {
            navigationManager.navigateTo(AppDestination.ShowQrFriend(FriendCodeDisplayMode.QR))
        }

        cardShareProfile.setOnClickListener {
            shareProfile()
        }

        btnChangeAvatar.setOnClickListener {
            com.nhn.gps.location.phone.tracker.ui.setup_profile.AvatarSelectorBottomSheet
                .newInstance(viewModel.currentAvatarKey.value)
                .show(parentFragmentManager, com.nhn.gps.location.phone.tracker.ui.setup_profile.AvatarSelectorBottomSheet.TAG)
        }

        parentFragmentManager.setFragmentResultListener(
            com.nhn.gps.location.phone.tracker.ui.setup_profile.AvatarSelectorBottomSheet.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val avatarKey = bundle.getString(com.nhn.gps.location.phone.tracker.ui.setup_profile.AvatarSelectorBottomSheet.RESULT_AVATAR_KEY)
            if (avatarKey != null) {
                viewModel.selectPresetAvatar(avatarKey)
            }
        }

        btnSave.setOnClickListener {
            viewModel.saveChanges()
        }
    }

    private fun shareProfile() {
        val uid = mainViewModel.userId.value ?: return
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = "text/plain"
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
        val shareMessage = "Add me on GPS Tracker! My ID: $uid"
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareMessage)
        startActivity(Intent.createChooser(shareIntent, "Share via"))
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.currentName.collectLatest { name ->
                        if (binding.etName.text.toString() != name) {
                            binding.etName.setText(name)
                        }
                    }
                }
                launch {
                    combine(
                        viewModel.isChanged,
                        viewModel.canEditProfile,
                    ) { changed, canEdit -> changed && canEdit }
                        .collectLatest { showSave ->
                            binding.btnSave.visibility = if (showSave) View.VISIBLE else View.GONE
                        }
                }
                launch {
                    viewModel.canEditProfile.collectLatest { canEdit ->
                        binding.etName.isEnabled = canEdit
                        binding.ivEditName.isEnabled = canEdit
                        binding.btnChangeAvatar.isEnabled = canEdit
                        binding.ivEditName.alpha = if (canEdit) 1f else 0.45f
                        binding.btnChangeAvatar.alpha = if (canEdit) 1f else 0.45f
                    }
                }
                launch {
                    mainViewModel.userPhone.collectLatest { phone ->
                        binding.tvPhone.text = phone.ifBlank { "+84 000 0000" }
                    }
                }
                launch {
                    mainViewModel.userId.collectLatest { id ->
                        binding.tvUserId.text = getString(R.string.user_id_prefix, id?.takeLast(8) ?: "00000000")
                    }
                }
                launch {
                    combine(
                        mainViewModel.userAvatar,
                        viewModel.currentAvatarKey,
                        mainViewModel.localAvatarPath
                    ) { remoteUrl, key, localPath ->
                        Triple(remoteUrl, key, localPath)
                    }.collectLatest { (remoteUrl, key, localPath) ->
                        binding.imgAvatar.loadAvatar(
                            avatarKey = key,
                            avatarUrl = remoteUrl,
                            localPath = localPath,
                            fallbackRes = R.drawable.ic_avt
                        )
                    }
                }
                launch {
                    viewModel.uiState.collectLatest { state ->
                        when (state) {
                            is EditProfileUiState.Loading -> {
                                binding.btnSave.isEnabled = false
                            }
                            is EditProfileUiState.Success -> {
                                binding.btnSave.isEnabled = true
                                Toast.makeText(requireContext(), R.string.profile_updated, Toast.LENGTH_SHORT).show()
                                viewModel.resetState()
                            }
                            is EditProfileUiState.IdentityMismatch -> {
                                binding.btnSave.isEnabled = false
                                MaterialAlertDialogBuilder(requireContext())
                                    .setTitle(R.string.profile_edit_device_mismatch_title)
                                    .setMessage(R.string.profile_edit_device_mismatch_message)
                                    .setPositiveButton(R.string.ok, null)
                                    .show()
                                viewModel.resetState()
                            }
                            is EditProfileUiState.Error -> {
                                binding.btnSave.isEnabled = true
                                Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                                viewModel.resetState()
                            }
                            else -> {
                                binding.btnSave.isEnabled = true
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun newInstance() = EditProfileFragment()
    }
}
