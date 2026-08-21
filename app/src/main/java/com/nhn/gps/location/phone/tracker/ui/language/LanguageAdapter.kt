package com.nhn.gps.location.phone.tracker.ui.language

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
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

    init {
        setHasStableIds(true)
    }

    class Holder(val binding: ItemLanguageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemLanguageBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = with(holder.binding) {
        val item = items[position]
        val context = holder.itemView.context
        tvLanguageName.text = item.nativeName
        tvLanguageNativeName.text = item.name
        ivLanguageFlag.setImageResource(item.flagIconRes)
        ivSelected.setImageResource(
            if (item.isSelected) R.drawable.ic_language_radio_checked
            else R.drawable.ic_language_radio_unchecked,
        )
        cardRoot.setCardBackgroundColor(ContextCompat.getColor(context, R.color.language_card_sdk))
        tvLanguageName.setTextColor(ContextCompat.getColor(context, R.color.neutral01))
        tvLanguageNativeName.setTextColor(ContextCompat.getColor(context, R.color.neutral05))

        val showGuide = showClickGuide && position == 0 && items.none { it.isSelected }
        val elevation = if (showGuide) 48f else 0f
        ViewCompat.setElevation(holder.itemView, elevation)
        holder.itemView.translationZ = elevation
        ivClickLanguageGuide.translationZ = elevation + 1f
        ivClickLanguageGuide.visibility = if (showGuide) View.VISIBLE else View.GONE
        if (showGuide) {
            Glide.with(ivClickLanguageGuide).asGif().load(R.raw.click_language).fitCenter().into(ivClickLanguageGuide)
        } else {
            Glide.with(ivClickLanguageGuide).clear(ivClickLanguageGuide)
        }
        holder.itemView.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition != RecyclerView.NO_POSITION) onClick(adapterPosition)
        }
    }

    override fun getItemCount() = items.size

    override fun getItemId(position: Int): Long = items[position].languageCode.hashCode().toLong()

    fun select(index: Int) {
        val previous = selectedIndex.takeIf { it in items.indices }
        val hadGuide = showClickGuide
        showClickGuide = false

        if (previous == index) {
            if (index in items.indices) notifyItemChanged(index)
            return
        }

        previous?.let { items[it].isSelected = false }
        selectedIndex = if (index in items.indices) {
            items[index].isSelected = true
            index
        } else {
            -1
        }

        val changedPositions = linkedSetOf<Int>()
        previous?.let(changedPositions::add)
        if (hadGuide && items.isNotEmpty()) changedPositions.add(0)
        if (index in items.indices) changedPositions.add(index)
        changedPositions.forEach(::notifyItemChanged)
    }
}
