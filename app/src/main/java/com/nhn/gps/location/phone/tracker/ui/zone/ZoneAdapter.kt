package com.nhn.gps.location.phone.tracker.ui.zone

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nhn.gps.location.phone.tracker.data.model.Zone
import com.nhn.gps.location.phone.tracker.databinding.ItemZoneLocalBinding

class ZoneAdapter(private val onClick: (Zone) -> Unit) :
    ListAdapter<Zone, ZoneAdapter.Holder>(DIFF) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemZoneLocalBinding.inflate(LayoutInflater.from(parent.context), parent, false), onClick
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    class Holder(
        private val binding: ItemZoneLocalBinding,
        private val onClick: (Zone) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Zone) = with(binding) {
            tvZoneName.text = item.name
            tvZoneAddress.text = item.address.ifBlank { "Location not set" }
            tvZoneMeta.text = "${item.status.label} · ${item.radiusMeters} m · ${item.type.label}"
            root.setOnClickListener { onClick(item) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Zone>() {
            override fun areItemsTheSame(oldItem: Zone, newItem: Zone) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Zone, newItem: Zone) = oldItem == newItem
        }
    }
}
