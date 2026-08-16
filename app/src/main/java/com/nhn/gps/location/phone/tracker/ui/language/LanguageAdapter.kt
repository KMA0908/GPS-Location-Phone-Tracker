package com.nhn.gps.location.phone.tracker.ui.language

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.data.model.LanguageModel
import com.nhn.gps.location.phone.tracker.databinding.ItemLanguageBinding

class LanguageAdapter(
    private val items: MutableList<LanguageModel>,
    private var showClickGuide: Boolean,
    private val onClick: (Int) -> Unit,
) : RecyclerView.Adapter<LanguageAdapter.Holder>() {
    private var selectedIndex = items.indexOfFirst { it.isSelected }

    class Holder(val binding: ItemLanguageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemLanguageBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = with(holder.binding) {
        val item = items[position]
        tvLanguageName.text = item.nativeName
        tvLanguageNativeName.text = item.name
        ivLanguageFlag.setImageResource(item.flagIconRes)
        ivSelected.setImageResource(
            if (position == selectedIndex) R.drawable.ic_language_radio_checked
            else R.drawable.ic_language_radio_unchecked,
        )
        val showGuide = showClickGuide && position == 0 && selectedIndex < 0
        val elevation = if (showGuide) 48f else 0f
        ViewCompat.setElevation(holder.itemView, elevation)
        ivClickLanguageGuide.visibility = if (showGuide) View.VISIBLE else View.GONE
        if (showGuide) {
            // TEMP DISABLED: ls-leansoft-publishing-sdk unavailable
            // Glide.with(ivClickLanguageGuide).asGif().load(R.raw.click_language).fitCenter().into(ivClickLanguageGuide)
        } else {
            Glide.with(ivClickLanguageGuide).clear(ivClickLanguageGuide)
        }
        cardRoot.setOnClickListener { onClick(holder.bindingAdapterPosition) }
    }

    override fun getItemCount() = items.size

    fun select(index: Int) {
        if (index !in items.indices) return
        val previous = selectedIndex
        val hadGuide = showClickGuide
        showClickGuide = false
        selectedIndex = index
        if (previous >= 0) notifyItemChanged(previous)
        if (hadGuide && items.isNotEmpty() && previous != 0 && index != 0) notifyItemChanged(0)
        notifyItemChanged(index)
    }
}
