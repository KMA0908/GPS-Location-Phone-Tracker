package com.nhn.gps.location.phone.tracker.ui.zone

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.base.BaseFragment
import com.nhn.gps.location.phone.tracker.data.model.ZoneAlert
import com.nhn.gps.location.phone.tracker.databinding.FragmentAlertDetailLocalBinding
import com.nhn.gps.location.phone.tracker.ui.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class AlertDetailFragment : BaseFragment<FragmentAlertDetailLocalBinding, MainViewModel>(), OnMapReadyCallback {
    override val viewModel: MainViewModel by viewModels({ requireActivity() })
    
    private var map: GoogleMap? = null
    private var currentAlert: ZoneAlert? = null

    override fun createBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentAlertDetailLocalBinding.inflate(inflater, container, false)

    override fun setupViews(savedInstanceState: Bundle?) {
        with(binding) {
            btnBack.setOnClickListener { handleToolbarBack() }
            
            val mapFragment = childFragmentManager.findFragmentById(R.id.mapFragment) as? SupportMapFragment
            mapFragment?.getMapAsync(this@AlertDetailFragment)
        }
    }

    override fun observeData() {
        // Trong thực tế, bạn sẽ lấy Alert từ SafeArgs hoặc Shared ViewModel
        // Ở đây tôi giả lập dữ liệu hiển thị dựa trên Alert cuối cùng (hoặc logic bạn đã có)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        googleMap.uiSettings.isMapToolbarEnabled = false
        
        // Logic hiển thị Marker và Circle của Zone dựa trên currentAlert
        // currentAlert?.let { alert ->
        //     val pos = LatLng(alert.latitude, alert.longitude)
        //     googleMap.addMarker(MarkerOptions().position(pos).title(alert.userName))
        //     googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f))
        // }
    }

    companion object {
        fun newInstance() = AlertDetailFragment()
    }
}
