package com.nhn.gps.location.phone.tracker.ui.friend

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.data.local.AppPreferences
import com.nhn.gps.location.phone.tracker.databinding.FragmentShowQrFriendBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ShowQrFriendFragment : BaseFragment<FragmentShowQrFriendBinding, FriendListViewModel>() {

    override val viewModel: FriendListViewModel by viewModels()

    @Inject
    lateinit var appPreferences: AppPreferences

    private val mode: FriendCodeDisplayMode by lazy {
        val modeName = arguments?.getString(ARG_MODE)
        if (modeName != null) FriendCodeDisplayMode.valueOf(modeName) else FriendCodeDisplayMode.QR
    }

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentShowQrFriendBinding = FragmentShowQrFriendBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) {
        binding.btnBack.setOnClickListener { handleToolbarBack() }

        binding.txtTitle.text = getString(
            if (mode == FriendCodeDisplayMode.CODE) R.string.my_code else R.string.my_qr_code
        )

        binding.qrContainer.isVisible = mode == FriendCodeDisplayMode.QR
        binding.codeContainer.isVisible = mode == FriendCodeDisplayMode.CODE
        binding.cardDownload.isVisible = mode == FriendCodeDisplayMode.QR
        binding.tvShareLabel.text = getString(
            if (mode == FriendCodeDisplayMode.CODE) R.string.share_code else R.string.share_qr
        )
        binding.tvCode.isVisible = mode == FriendCodeDisplayMode.CODE
        binding.imgCopy.isVisible = mode == FriendCodeDisplayMode.CODE

        viewLifecycleOwner.lifecycleScope.launch {
            val uid = appPreferences.userId.first()
            if (uid.isNullOrBlank()) {
                handleEmptyUid()
                return@launch
            }

            val payload = if (mode == FriendCodeDisplayMode.QR) "gps_friend:$uid" else uid
            binding.tvCode.text = uid

            if (mode == FriendCodeDisplayMode.QR) {
                setupQr(payload)
            }

            binding.cardShare.setOnClickListener { shareProfile(payload) }
            binding.imgCopy.setOnClickListener { copyToClipboard(payload) }
        }
    }

    private fun handleEmptyUid() {
        binding.tvCode.text = getString(R.string.uid_unavailable)
        binding.cardShare.isEnabled = false
        binding.cardDownload.isEnabled = false
        binding.imgCopy.isEnabled = false
        Toast.makeText(requireContext(), R.string.uid_unavailable, Toast.LENGTH_SHORT).show()
    }

    private fun setupQr(payload: String) {
        try {
            val encoder = BarcodeEncoder()
            val bitmap = encoder.encodeBitmap(payload, BarcodeFormat.QR_CODE, 512, 512)
            binding.imgQr.setImageBitmap(bitmap)
            binding.cardDownload.setOnClickListener { saveQrToGallery(bitmap) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Friend Code", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), R.string.code_copied, Toast.LENGTH_SHORT).show()
    }

    private fun saveQrToGallery(bitmap: Bitmap) {
        try {
            val title = "MyFriendCode_${System.currentTimeMillis()}"
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.insertImage(
                requireContext().contentResolver,
                bitmap,
                title,
                "QR Code for My Friend Profile"
            )
            Toast.makeText(requireContext(), R.string.qr_downloaded, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to download QR", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareProfile(payload: String) {
        val message = if (mode == FriendCodeDisplayMode.CODE) {
            getString(R.string.share_code_message, payload)
        } else {
            getString(R.string.share_qr_message, payload)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.my_friend_code))
            putExtra(Intent.EXTRA_TEXT, message)
        }
        startActivity(Intent.createChooser(intent, "Share via"))
    }

    companion object {
        private const val ARG_MODE = "display_mode"

        fun newInstance(mode: FriendCodeDisplayMode) = ShowQrFriendFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_MODE, mode.name)
            }
        }
    }
}
