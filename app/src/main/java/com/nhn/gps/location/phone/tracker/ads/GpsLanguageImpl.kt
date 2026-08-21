package com.nhn.gps.location.phone.tracker.ads

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.leansoft.ads.enums.AdStatus
import com.leansoft.ads.ui.language.LeansoftLanguageInterface
import com.leansoft.ads.view.NativeAdViewContainer
import com.nhn.gps.location.phone.tracker.databinding.FragmentLanguageBinding
import com.nhn.gps.location.phone.tracker.ui.language.LanguageAdapter
import com.nhn.gps.location.phone.tracker.util.LanguageHelper

class GpsLanguageImpl : LeansoftLanguageInterface {
    private var binding: FragmentLanguageBinding? = null
    private var adapter: LanguageAdapter? = null
    private var selectListener: ((String) -> Unit)? = null
    private var applyListener: ((String) -> Unit)? = null
    private var selectedCode = ""
    private var showCheckButtonRunnable: Runnable? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        cancelDelayedCheckButton()
        binding = FragmentLanguageBinding.inflate(inflater, container, false)
        return requireNotNull(binding).also {
            it.btnNext.visibility = View.INVISIBLE
            it.btnNext.isEnabled = false
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?, languageCodeSelected: String?) {
        val currentBinding = binding ?: return
        val items = LanguageHelper.getListLanguage(view.context)
        selectedCode = languageCodeSelected.orEmpty()
        adapter = LanguageAdapter(items, showClickGuide = languageCodeSelected.isNullOrBlank()) { index ->
            val item = items.getOrNull(index) ?: return@LanguageAdapter
            if (languageCodeSelected.isNullOrBlank()) {
                selectListener?.invoke(item.languageCode)
            } else {
                selectedCode = item.languageCode
                adapter?.select(index)
                updateCheckButtonVisibility(selectedCode)
            }
        }
        currentBinding.recyclerView.layoutManager = LinearLayoutManager(view.context)
        currentBinding.recyclerView.adapter = adapter
        if (languageCodeSelected.isNullOrBlank()) {
            currentBinding.btnNext.visibility = View.INVISIBLE
            currentBinding.btnNext.isEnabled = false
            currentBinding.btnNext.setOnClickListener(null)
        } else {
            val index = LanguageHelper.languageIndexFromCode(languageCodeSelected)
            adapter?.select(index)
            updateCheckButtonVisibility(languageCodeSelected)
            currentBinding.btnNext.setOnClickListener {
                selectedCode.takeIf(String::isNotBlank)?.let { applyListener?.invoke(it) }
            }
        }
    }

    override fun getNativeAdContainer(): NativeAdViewContainer =
        binding?.nativeAdViewContainer ?: error("Language view is not created")

    override fun registerSelectLanguageEvent(listener: (String) -> Unit) { selectListener = listener }
    override fun registerSetLanguageEvent(listener: (String) -> Unit) { applyListener = listener }
    override fun updateUI(needEasy: Boolean) {
        binding?.nativeAdViewContainer?.let {
            it.visibility = if (it.adStatus == AdStatus.LOADING || it.adStatus == AdStatus.LOADED) View.VISIBLE else View.GONE
        }
    }

    private fun updateCheckButtonVisibility(languageCode: String?) {
        cancelDelayedCheckButton()
        if (languageCode.isNullOrBlank()) {
            binding?.btnNext?.visibility = View.INVISIBLE
            binding?.btnNext?.isEnabled = false
            return
        }

        val delayMillis = GpsRemoteConfig.languageNextButtonDelayMillis()
        if (delayMillis <= 0L) {
            binding?.btnNext?.visibility = View.VISIBLE
            binding?.btnNext?.isEnabled = true
            return
        }

        binding?.btnNext?.visibility = View.INVISIBLE
        binding?.btnNext?.isEnabled = false
        val runnable = Runnable {
            binding?.btnNext?.visibility = View.VISIBLE
            binding?.btnNext?.isEnabled = true
            showCheckButtonRunnable = null
        }
        showCheckButtonRunnable = runnable
        binding?.btnNext?.postDelayed(runnable, delayMillis)
    }

    private fun cancelDelayedCheckButton() {
        val runnable = showCheckButtonRunnable ?: return
        binding?.btnNext?.removeCallbacks(runnable)
        showCheckButtonRunnable = null
    }
}
