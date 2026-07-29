package com.nhn.gps.location.phone.tracker.ui.friend

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
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

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentShowQrFriendBinding = FragmentShowQrFriendBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) {
        binding.btnBack.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        
        viewLifecycleOwner.lifecycleScope.launch {
            val userId = appPreferences.userName.first() // Use userName as ID for demo
            binding.tvCode.text = userId
            
            try {
                val encoder = BarcodeEncoder()
                val bitmap = encoder.encodeBitmap(userId, BarcodeFormat.QR_CODE, 512, 512)
                binding.imgQr.setImageBitmap(bitmap)
                
                binding.cardDownload.setOnClickListener {
                    saveQrToGallery(bitmap)
                }
                
                binding.cardShare.setOnClickListener {
                    shareProfile(userId)
                }
                
                binding.imgCopy.setOnClickListener {
                    val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Friend Code", userId)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(requireContext(), "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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
            Toast.makeText(requireContext(), "QR Code downloaded to gallery", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to download QR", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareProfile(userId: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "My Friend Code")
            putExtra(Intent.EXTRA_TEXT, "Add me on GPS Location Phone Tracker! Code: $userId")
        }
        startActivity(Intent.createChooser(intent, "Share via"))
    }

    companion object {
        fun newInstance() = ShowQrFriendFragment()
    }
}
