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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentLanguageBinding.inflate(inflater, container, false)
        return requireNotNull(binding).root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?, languageCodeSelected: String?) {
        val currentBinding = binding ?: return
        val items = LanguageHelper.languages()
        selectedCode = languageCodeSelected.orEmpty()
        adapter = LanguageAdapter(items, showClickGuide = languageCodeSelected.isNullOrBlank()) { index ->
            val item = items.getOrNull(index) ?: return@LanguageAdapter
            if (languageCodeSelected.isNullOrBlank()) {
                selectListener?.invoke(item.languageCode)
            } else {
                selectedCode = item.languageCode
                adapter?.select(index)
                currentBinding.btnNext.visibility = View.VISIBLE
            }
        }
        currentBinding.recyclerView.layoutManager = LinearLayoutManager(view.context)
        currentBinding.recyclerView.adapter = adapter
        if (languageCodeSelected.isNullOrBlank()) {
            currentBinding.btnNext.visibility = View.INVISIBLE
        } else {
            val index = LanguageHelper.languageIndexFromCode(languageCodeSelected)
            adapter?.select(index)
            currentBinding.btnNext.visibility = View.VISIBLE
        }
        currentBinding.btnNext.setOnClickListener {
            selectedCode.takeIf(String::isNotBlank)?.let { applyListener?.invoke(it) }
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
}
