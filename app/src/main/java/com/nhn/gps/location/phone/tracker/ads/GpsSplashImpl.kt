package com.nhn.gps.location.phone.tracker.ads

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import com.leansoft.ads.ui.splash.LeansoftSplashInterface
import com.leansoft.ads.view.BannerAdViewContainer
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.databinding.ActivitySplashBinding
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.constraintlayout.widget.ConstraintLayout

@Singleton
class GpsSplashImpl @Inject constructor() : LeansoftSplashInterface() {
    private lateinit var binding: ActivitySplashBinding
    private var progressAnimator: ValueAnimator? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = ActivitySplashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.splashVideo.apply {
            setVideoUri("android.resource://${view.context.packageName}/${R.raw.video_gps_splash}".toUri())
            setOnPlaybackErrorListener {
                visibility = View.GONE
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val params = binding.bannerSplashAd.layoutParams as ConstraintLayout.LayoutParams
            if (params.bottomMargin != systemBars.bottom) {
                params.bottomMargin = systemBars.bottom
                binding.bannerSplashAd.layoutParams = params
            }
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)

        progressAnimator?.cancel()
        val animator = ValueAnimator.ofFloat(0.03f, 0.97f).apply {
            duration = 1_600L
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener {
                binding.splashProgressGuide.setGuidelinePercent(it.animatedValue as Float)
            }
        }
        progressAnimator = animator
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) {
                animator.cancel()
                binding.splashVideo.stopPlayback()
                view.removeOnAttachStateChangeListener(this)
            }
        })
        view.post { if (view.isAttachedToWindow) animator.start() }
    }

    override fun getBannerAdContainer(): BannerAdViewContainer = binding.bannerSplashAd
}
