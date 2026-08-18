package com.nhn.gps.location.phone.tracker.ui.phone_number_locator

import android.os.Bundle
import androidx.core.os.BundleCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.graphics.Color
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.ads.GpsAdPlacement
import com.nhn.gps.location.phone.tracker.data.model.UserLocation
import com.nhn.gps.location.phone.tracker.data.model.UserProfile
import com.nhn.gps.location.phone.tracker.databinding.BottomSheetPhoneNumberLocatorInfoBinding
import com.nhn.gps.location.phone.tracker.ui.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PhoneLocatorInfoBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetPhoneNumberLocatorInfoBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetPhoneNumberLocatorInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val profile = arguments?.let { bundle ->
            BundleCompat.getParcelable(bundle, ARG_PROFILE, UserProfile::class.java)
        }
        val address = arguments?.getString(ARG_ADDRESS)

        if (profile != null) {
            bindData(profile, address)
        }
    }

    private fun bindData(profile: UserProfile, address: String?) = with(binding) {
        // 1. Address Info Card
        layoutCountry.tvTitle.text = getString(R.string.country_region)
        layoutCountry.tvValue.text = address ?: getString(R.string.updating)
        
        // 2. Carrier Info Card
        layoutCarrier.tvTitle.text = getString(R.string.carrier)
        layoutCarrier.tvValue.text = getString(R.string.mobile)
    }

    override fun onStart() {
        super.onStart()
        (binding.root.parent as? View)?.setBackgroundResource(android.R.color.transparent)
        (activity as? MainActivity)?.showScreenBanner(GpsAdPlacement.BANNER_PHONE_NUMBER_DETAIL)
    }

    override fun onDestroyView() {
        (activity as? MainActivity)?.restoreCurrentScreenAd()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "PhoneLocatorInfoBottomSheet"
        private const val ARG_PROFILE = "ARG_PROFILE"
        private const val ARG_LOCATION = "ARG_LOCATION"
        private const val ARG_ADDRESS = "ARG_ADDRESS"

        fun newInstance(profile: UserProfile, location: UserLocation?, address: String? = null): PhoneLocatorInfoBottomSheet {
            return PhoneLocatorInfoBottomSheet().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_PROFILE, profile)
                    putParcelable(ARG_LOCATION, location)
                    putString(ARG_ADDRESS, address)
                }
            }
        }
    }
}
