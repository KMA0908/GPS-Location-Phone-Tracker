package com.nhn.gps.location.phone.tracker.ui.phone_number_locator

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nhn.gps.location.phone.tracker.data.model.Country
import com.nhn.gps.location.phone.tracker.databinding.ItemCountryBinding

class CountryAdapter(
    private val onCountryClick: (Country) -> Unit
) : ListAdapter<Country, CountryAdapter.CountryViewHolder>(CountryDiffCallback()) {

    private var selectedCountryIso: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CountryViewHolder {
        val binding = ItemCountryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CountryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CountryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: CountryViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            payloads.forEach { payload ->
                if (payload == PAYLOAD_SELECTION_CHANGED) {
                    holder.updateSelection(getItem(position).iso)
                }
            }
        }
    }

    fun setSelectedCountry(iso: String?) {
        if (selectedCountryIso == iso) return
        
        val oldIso = selectedCountryIso
        selectedCountryIso = iso
        
        // Find indices to notify for optimized updates
        for (i in 0 until itemCount) {
            val item = getItem(i)
            if (item.iso == oldIso || item.iso == iso) {
                notifyItemChanged(i, PAYLOAD_SELECTION_CHANGED)
            }
        }
    }

    inner class CountryViewHolder(private val binding: ItemCountryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(country: Country) = with(binding) {
            tvEmoji.text = country.emoji
            tvName.text = country.name
            tvDialCode.text = "(${country.dialCode})"
            
            updateSelection(country.iso)

            root.setOnClickListener {
                onCountryClick(country)
            }
        }

        fun updateSelection(iso: String) {
            binding.ivCheck.visibility = if (iso == selectedCountryIso) View.VISIBLE else View.GONE
        }
    }

    class CountryDiffCallback : DiffUtil.ItemCallback<Country>() {
        override fun areItemsTheSame(oldItem: Country, newItem: Country): Boolean {
            return oldItem.iso == newItem.iso
        }

        override fun areContentsTheSame(oldItem: Country, newItem: Country): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        private const val PAYLOAD_SELECTION_CHANGED = "PAYLOAD_SELECTION_CHANGED"
    }
}
