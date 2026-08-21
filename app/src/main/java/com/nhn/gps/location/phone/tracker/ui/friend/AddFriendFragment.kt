package com.nhn.gps.location.phone.tracker.ui.friend

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ResultPoint
import com.google.zxing.common.HybridBinarizer
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.ads.GpsAdPlacement
import com.nhn.gps.location.phone.tracker.ads.GpsAdViewBinder
import com.nhn.gps.location.phone.tracker.ads.GpsAds
import com.nhn.gps.location.phone.tracker.ads.ResumeAdGuard
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.databinding.FragmentAddFriendBinding
import com.nhn.gps.location.phone.tracker.navigation.AppDestination
import com.nhn.gps.location.phone.tracker.ui.main.MainActivity
import com.nhn.gps.location.phone.tracker.util.loadAvatar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.InputStream
import javax.inject.Inject

@AndroidEntryPoint
class AddFriendFragment : BaseFragment<FragmentAddFriendBinding, AddFriendViewModel>() {

    override val viewModel: AddFriendViewModel by viewModels()
    private var isFlashOn = false

    private val barcodeCallback = object : BarcodeCallback {
        override fun barcodeResult(result: BarcodeResult?) {
            result?.text?.let { code ->
                binding.layoutCamera.barcodeScanner.pause()
                viewModel.findFriend(code)
            }
        }
        override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) {}
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        ResumeAdGuard.onSystemDialogFinished()
        uri?.let { scanQrFromUri(it) }
    }

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentAddFriendBinding = FragmentAddFriendBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) = with(binding) {
        setupImeInsets()
        cameraContainer.addOnLayoutChangeListener { _, left, top, right, bottom,
                                                    oldLeft, oldTop, oldRight, oldBottom ->
            val sizeChanged = right - left != oldRight - oldLeft ||
                bottom - top != oldBottom - oldTop
            if (sizeChanged && cameraContainer.isVisible) {
                cameraContainer.post(::updateCustomFramingRect)
            }
        }

        header.btnBack.setOnClickListener { 
            handleToolbarBack()
        }

        tabLayout.btnScanQR.setOnClickListener {
            switchTab(isScan = true)
        }

        tabLayout.btnEnterCode.setOnClickListener {
            switchTab(isScan = false)
        }

        layoutScanQR.btnOpenCamera.setOnClickListener {
            showAddFriendInterThen {
                cameraContainer.isVisible = true
                layoutCamera.barcodeScanner.decodeContinuous(barcodeCallback)
                layoutCamera.barcodeScanner.resume()
                checkFlashSupport()
                (activity as? MainActivity)?.showScreenNative(
                    GpsAdPlacement.NATIVE_QR_CAMERA,
                    GpsAdViewBinder.NativeFormat.MEDIUM,
                )
                cameraContainer.post(::updateCustomFramingRect)
            }
        }
        
        layoutScanQR.btnMyQR.setOnClickListener {
            navigationManager.navigateTo(AppDestination.ShowQrFriend(FriendCodeDisplayMode.QR))
        }

        layoutEnterCode.btnFindFriend.setOnClickListener {
            val code = layoutEnterCode.edtFriendCode.text.toString()
            showAddFriendInterThen { viewModel.findFriend(code) }
        }

        layoutEnterCode.btnMyCode.setOnClickListener {
            navigationManager.navigateTo(AppDestination.ShowQrFriend(FriendCodeDisplayMode.CODE))
        }

        // Camera Layout listeners
        layoutCamera.headerCamera.btnClose.setOnClickListener {
            cameraContainer.isVisible = false
            layoutCamera.barcodeScanner.pause()
            turnOffFlash()
            showTabAd(isScan = true)
        }

        layoutCamera.headerCamera.layoutFlash.setOnClickListener {
            toggleFlash()
        }
        
        layoutCamera.btnMyQr.setOnClickListener {
            navigationManager.navigateTo(AppDestination.ShowQrFriend(FriendCodeDisplayMode.QR))
        }
        
        layoutCamera.btnUpload.setOnClickListener {
            ResumeAdGuard.onSystemDialogRequested()
            pickImageLauncher.launch("image/*")
        }

        viewDim.setOnClickListener { 
            popupContainer.isVisible = false
            viewDim.isVisible = false
            viewModel.clearFoundFriend()
            if (cameraContainer.isVisible) {
                layoutCamera.barcodeScanner.resume()
            }
        }

        layoutFriendFound.btnAddFriend.setOnClickListener {
            showAddFriendInterThen { viewModel.addFriend() }
        }
    }

    private fun setupImeInsets() {
        val content = binding.contentContainer
        val initialBottomPadding = content.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val systemBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                initialBottomPadding + (imeBottom - systemBottom).coerceAtLeast(0),
            )
            insets
        }
        ViewCompat.requestApplyInsets(content)
    }

    private fun updateCustomFramingRect() {
        val scanner = binding.layoutCamera.barcodeScanner
        val frame = binding.layoutCamera.imgQrFrame
        if (!scanner.isLaidOut || !frame.isLaidOut) return

        val frameRect = Rect()
        val scannerRect = Rect()
        if (!frame.getGlobalVisibleRect(frameRect) || !scanner.getGlobalVisibleRect(scannerRect)) return

        val localRect = Rect(
            frameRect.left - scannerRect.left,
            frameRect.top - scannerRect.top,
            frameRect.right - scannerRect.left,
            frameRect.bottom - scannerRect.top,
        )
        if (localRect.width() > 0 && localRect.height() > 0) {
            scanner.setManualFramingRect(localRect)
        }
    }

    private fun scanQrFromUri(uri: Uri) {
        try {
            val inputStream: InputStream? = requireContext().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            if (bitmap == null) {
                Toast.makeText(requireContext(), "Failed to load image", Toast.LENGTH_SHORT).show()
                return
            }
            
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            
            val source = RGBLuminanceSource(width, height, pixels)
            val bBitmap = BinaryBitmap(HybridBinarizer(source))
            val reader = MultiFormatReader()
            val result = reader.decode(bBitmap)
            
            viewModel.findFriend(result.text)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "No QR code found in image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun switchTab(isScan: Boolean) = with(binding) {
        if (isScan) {
            tabLayout.btnScanQR.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), android.R.color.white))
            tabLayout.btnScanQR.setTextColor(ContextCompat.getColor(requireContext(), R.color.bg_switch_permission))
            tabLayout.btnEnterCode.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), android.R.color.transparent))
            tabLayout.btnEnterCode.setTextColor(ContextCompat.getColor(requireContext(), R.color.bg_botton_add_friend))
            
            layoutScanQR.root.isVisible = true
            layoutEnterCode.root.isVisible = false
        } else {
            tabLayout.btnEnterCode.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), android.R.color.white))
            tabLayout.btnEnterCode.setTextColor(ContextCompat.getColor(requireContext(), R.color.bg_switch_permission))
            tabLayout.btnScanQR.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), android.R.color.transparent))
            tabLayout.btnScanQR.setTextColor(ContextCompat.getColor(requireContext(), R.color.bg_botton_add_friend))
            
            layoutEnterCode.root.isVisible = true
            layoutScanQR.root.isVisible = false
            layoutCamera.barcodeScanner.pause()
        }
        showTabAd(isScan)
    }

    private fun showTabAd(isScan: Boolean) {
        (activity as? MainActivity)?.showScreenNative(
            if (isScan) GpsAdPlacement.NATIVE_ADD_FRIEND_QR else GpsAdPlacement.NATIVE_ADD_FRIEND_CODE,
            GpsAdViewBinder.NativeFormat.SMALL,
        )
    }

    private fun showAddFriendInterThen(next: () -> Unit) {
        GpsAds.showInterThen(
            placement = GpsAdPlacement.INTER_ADD_FRIEND,
            fragmentManager = parentFragmentManager,
            next = {
                if (isAdded) next()
            },
        )
    }

    override fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.friendFound.collectLatest { friend ->
                        if (friend != null) {
                            showFriendFound(friend)
                        }
                    }
                }

                launch {
                    viewModel.friendNotFound.collect {
                        showNotFoundDialog()
                        if (binding.cameraContainer.isVisible) {
                            binding.layoutCamera.barcodeScanner.resume()
                        }
                    }
                }

                launch {
                    viewModel.alreadyFriend.collect {
                        showAlreadyFriendDialog()
                        if (binding.cameraContainer.isVisible) {
                            binding.layoutCamera.barcodeScanner.resume()
                        }
                    }
                }

                launch {
                    viewModel.addSuccess.collect {
                        showFriendAdded()
                    }
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showFriendFound(friend: com.nhn.gps.location.phone.tracker.data.model.FriendLocation) = with(binding) {
        layoutFriendFound.tvName.text = friend.name
        layoutFriendFound.tvId.text = "id: " + friend.id
        
        layoutFriendFound.imgAvatar.loadAvatar(friend.avatarKey, friend.avatarUrl, fallbackRes = R.drawable.ic_avt_find_friend)
        
        viewDim.isVisible = true
        popupContainer.isVisible = true
        layoutFriendFound.root.isVisible = true
        layoutFriendAdded.root.isVisible = false
    }

    private fun showNotFoundDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Friend Not Found")
            .setMessage("We couldn't find any user with that code.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showAlreadyFriendDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Already Friend")
            .setMessage("This user is already in your friend list.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showFriendAdded() = with(binding) {
        layoutFriendFound.root.isVisible = false
        layoutFriendAdded.root.isVisible = true
        
        viewLifecycleOwner.lifecycleScope.launch {
            delay(3000)
            navigationManager.navigateTo(AppDestination.MyFriend)
        }
    }

    override fun onResume() {
        super.onResume()
        if (binding.cameraContainer.isVisible) {
            binding.layoutCamera.barcodeScanner.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        binding.layoutCamera.barcodeScanner.pause()
        turnOffFlash()
    }

    private fun checkFlashSupport() {
        val hasFlash = requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        binding.layoutCamera.headerCamera.layoutFlash.isVisible = hasFlash
    }

    private fun toggleFlash() {
        isFlashOn = !isFlashOn
        binding.layoutCamera.barcodeScanner.setTorch(isFlashOn)
        updateFlashUi()
    }

    private fun turnOffFlash() {
        isFlashOn = false
        binding.layoutCamera.barcodeScanner.setTorch(false)
        updateFlashUi()
    }

    private fun updateFlashUi() = with(binding.layoutCamera.headerCamera) {
        val color = if (isFlashOn) {
            ContextCompat.getColor(requireContext(), R.color.color_ffd700)
        } else {
            ContextCompat.getColor(requireContext(), android.R.color.white)
        }
        imgFlash.imageTintList = android.content.res.ColorStateList.valueOf(color)
        tvFlash.setTextColor(color)
    }

    companion object {
        fun newInstance() = AddFriendFragment()
    }
}
