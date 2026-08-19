package com.nhn.gps.location.phone.tracker.ads

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.leansoft.ads.enums.AdStatus
import com.leansoft.ads.ui.onboarding.LeansoftOnboardingInterface
import com.leansoft.ads.utils.LeansoftAdPlacement
import com.leansoft.ads.utils.leansofttNativeFullPlacements
import com.leansoft.ads.view.NativeAdViewContainer
import com.nhn.gps.location.phone.tracker.R

class GpsOnboardingImpl : LeansoftOnboardingInterface() {
    private val containers = mutableMapOf<LeansoftAdPlacement, NativeAdViewContainer>()
    private val views = mutableMapOf<LeansoftAdPlacement, View>()
    private val nextListeners = mutableMapOf<LeansoftAdPlacement, () -> Unit>()
    private val fullPlacements = leansofttNativeFullPlacements

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
        placement: LeansoftAdPlacement,
    ): View {
        val layout = if (placement in fullPlacements) R.layout.fragment_onboarding_native_full else R.layout.layout_onboarding_page
        return inflater.inflate(layout, container, false).also {
            containers[placement] = it.findViewById(R.id.native_ad_view_container)
            views[placement] = it
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?, placement: LeansoftAdPlacement) {
        if (placement in fullPlacements) {
            val showClose = GpsRemoteConfig.showNativeFullOnboardingClose()
            view.findViewById<View>(R.id.close_scrim).visibility = if (showClose) View.VISIBLE else View.GONE
            view.findViewById<View>(R.id.btn_close).apply {
                visibility = if (showClose) View.VISIBLE else View.GONE
                setOnClickListener { nextListeners[placement]?.invoke() }
            }
            return
        }
        view.findViewById<View>(R.id.btn_next).setOnClickListener { nextListeners[placement]?.invoke() }
        val page = when (placement) {
            LeansoftAdPlacement.NATIVE_OBD_1 -> Page(R.drawable.bg_onboard1, R.string.onboarding_title_location, R.string.onboarding_desc_location, 0)
            LeansoftAdPlacement.NATIVE_OBD_2 -> Page(R.drawable.bg_onboard2, R.string.onboarding_title_friends, R.string.onboarding_desc_friends, 1)
            else -> Page(R.drawable.bg_onboard3, R.string.onboarding_title_zones, R.string.onboarding_desc_zones, 2)
        }
        view.findViewById<ImageView>(R.id.img_cover).setImageResource(page.image)
        view.findViewById<TextView>(R.id.tv_title).setText(page.title)
        view.findViewById<TextView>(R.id.tv_desc).setText(page.description)
        listOf(R.id.dot_1, R.id.dot_2, R.id.dot_3).forEachIndexed { index, id ->
            view.findViewById<View>(id).isSelected = index == page.dot
        }
        renderSwipeHint(view, placement == LeansoftAdPlacement.NATIVE_OBD_2)
    }

    override fun getNativeAdContainer(placement: LeansoftAdPlacement): NativeAdViewContainer =
        containers[placement] ?: error("Onboarding view is not created for $placement")

    override fun registerNextClick(placement: LeansoftAdPlacement, listener: () -> Unit) {
        nextListeners[placement] = listener
    }

    override fun updateUI(placement: LeansoftAdPlacement, needEasy: Boolean) = Unit

    override fun onPlacementSelected(placement: LeansoftAdPlacement) {
        views.forEach { (page, view) ->
            renderSwipeHint(view, page == placement && placement == LeansoftAdPlacement.NATIVE_OBD_2)
        }
    }

    override fun getOnStatusChange(placement: LeansoftAdPlacement): ((AdStatus) -> Unit)? {
        if (placement !in fullPlacements) return null
        return { status ->
            if (status == AdStatus.NONE || status == AdStatus.FAILED) {
                containers[placement]?.post { nextListeners[placement]?.invoke() }
            }
        }
    }

    private data class Page(val image: Int, val title: Int, val description: Int, val dot: Int)

    private fun renderSwipeHint(view: View, show: Boolean) {
        val hint = view.findViewById<ImageView>(R.id.sw_onboard) ?: return
        Glide.with(view).clear(hint)
        hint.visibility = if (show) View.VISIBLE else View.GONE
        if (show) Glide.with(view).asGif().load(R.raw.sw_onboard).into(hint)
    }
}
