package com.nhn.gps.location.phone.tracker.ui.language

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.nhn.gps.location.phone.tracker.R
import com.nhn.gps.location.phone.tracker.data.model.LanguageModel
import com.nhn.gps.location.phone.tracker.databinding.ItemSettingsLanguageBinding

class SettingsLanguageAdapter(
    private val items: MutableList<LanguageModel>,
    private val onClick: (Int) -> Unit,
) : RecyclerView.Adapter<SettingsLanguageAdapter.Holder>() {
    private var selectedIndex = items.indexOfFirst { it.isSelected }

    class Holder(val binding: ItemSettingsLanguageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemSettingsLanguageBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = with(holder.binding) {
        val item = items[position]
        val selected = position == selectedIndex
        tvLanguageName.text = item.name
        tvLanguageNativeName.text = item.nativeName
        ivLanguageFlag.setImageResource(item.flagIconRes)
        cardRoot.strokeColor = ContextCompat.getColor(
            root.context,
            if (selected) R.color.settings_language_selected else R.color.settings_language_stroke,
        )
        cardRoot.strokeWidth = root.resources.getDimensionPixelSize(
            if (selected) R.dimen.settings_language_selected_stroke else R.dimen.settings_language_stroke,
        )
        tvLanguageNativeName.setTextColor(
            ContextCompat.getColor(
                root.context,
                if (selected) R.color.settings_language_selected else R.color.settings_language_native,
            ),
        )
        ivSelected.setBackgroundResource(
            if (selected) R.drawable.bg_settings_language_radio_selected else R.drawable.bg_settings_language_radio,
        )
        ivSelected.setImageResource(if (selected) R.drawable.ic_check_white else 0)
        cardRoot.setOnClickListener { onClick(holder.bindingAdapterPosition) }
    }

    override fun getItemCount() = items.size

    fun select(index: Int) {
        if (index !in items.indices) return
        val previous = selectedIndex
        selectedIndex = index
        if (previous >= 0) notifyItemChanged(previous)
        notifyItemChanged(index)
    }
}
